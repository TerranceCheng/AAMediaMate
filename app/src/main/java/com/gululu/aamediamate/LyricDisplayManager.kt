package com.gululu.aamediamate

import android.content.Context
import android.media.session.PlaybackState
import android.os.PowerManager
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import com.gululu.aamediamate.lyrics.LyricCache
import com.gululu.aamediamate.lyrics.LyricSyncEngine
import com.gululu.aamediamate.lyrics.LyricsRepository
import com.gululu.aamediamate.models.MediaInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

class LyricDisplayManager(private val context: Context) {

    private val lyricsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile
    private var currentLyricsJob: Job? = null
    private val lyricsJobMutex = Mutex()
    @Volatile
    private var lyricsUpdateJob: Job? = null
    @Volatile
    private var currentMediaInfo: MediaInfo? = null
    private val startRequestId = AtomicLong(0L)
    private var wakeLock: PowerManager.WakeLock? = null
    private val WAKELOCK_TIMEOUT = 10 * 60 * 1000L  // 10 minutes

    fun start(mediaSession: MediaSessionCompat, info: MediaInfo) {
        val requestId = startRequestId.incrementAndGet()

        val globalLyricsEnabled = SettingsManager.getLyricsEnabled(context)
        val appLyricsEnabled = SettingsManager.isAppLyricsEnabled(context, info.appPackageName)
        
        if (!globalLyricsEnabled || !appLyricsEnabled || !info.isPlaying || info.title.isBlank() || info.artist.isBlank()) {
            if (!globalLyricsEnabled) {
                Log.d("MediaBridge", "🚫 Lyrics globally disabled")
            } else if (!appLyricsEnabled) {
                Log.d("MediaBridge", "🚫 Lyrics disabled for app: ${info.appPackageName}")
            }
            stop()
            return
        }
        
        Log.d("MediaBridge", "🎵 Starting lyrics for: ${info.appPackageName} - ${info.title} by ${info.artist}")
        
        // Immediately stop any previous lyric sync so old lyrics never outlive a track change.
        // The new sync re-displays the currently active line as soon as it starts.
        LyricSyncEngine.stop()
        
        currentMediaInfo = info
        acquireWakeLock()

        // Cancel any previous update jobs
        lyricsUpdateJob?.cancel()
        
        // Start observing lyric updates
        lyricsUpdateJob = lyricsScope.launch {
            val observedMediaInfo = info // Capture the media info that started this observation
            LyricsRepository.lyricsUpdatedFlow.collectLatest { updatedKey ->
                val currentKey = "${observedMediaInfo.title}_${observedMediaInfo.artist}"
                if (updatedKey == currentKey && currentMediaInfo === observedMediaInfo) {
                    Log.d("MediaBridge", "🎤 Lyrics for current song updated. Restarting lyric display.")
                    // Restart to refresh with new lyrics (start() cancels the previous jobs)
                    start(mediaSession, observedMediaInfo)
                }
            }
        }

        lyricsScope.launch {
            lyricsJobMutex.withLock {
                // A newer start()/stop() took over while this request was waiting for the lock.
                if (requestId != startRequestId.get()) return@withLock

                currentLyricsJob?.cancel()
                currentLyricsJob = launch {
                    val lyrics = LyricCache.getOrFetchLyrics(
                        context,
                        info.title,
                        info.artist,
                        info.duration.toString()
                    )

                    // Fetching may outlive a track change; never start stale lyrics.
                    if (requestId != startRequestId.get()) return@launch

                    if (lyrics.isEmpty()) {
                        Log.d("MediaBridge", "🚫 Lyrics not found: ${info.title}")
                        updateLyricLine(mediaSession, info, "", null) // Clear the displayed lyric
                        return@launch
                    }

                    val currentPosition = currentPlaybackPositionMs() ?: info.position
                    val offsetMs = SettingsManager.getLyricsTimingOffset(context).toLong()

                    val engineJob = LyricSyncEngine.start(
                        lyrics,
                        currentPosition,
                        offsetMs,
                        positionProvider = { currentPlaybackPositionMs() }
                    ) { line, nextLine ->
                        updateLyricLine(mediaSession, info, line, nextLine)
                    }

                    // The track may have changed while this start was in flight; never leave
                    // the previous track's lyrics running on top of the new one.
                    if (requestId != startRequestId.get()) LyricSyncEngine.stopIfCurrent(engineJob)
                }
            }
        }
    }

    fun stop() {
        startRequestId.incrementAndGet()
        stopInternal()
        lyricsUpdateJob?.cancel()
        currentMediaInfo = null
        releaseWakeLock()
    }

    private fun stopInternal() {
        LyricSyncEngine.stop()
        val job = currentLyricsJob
        currentLyricsJob = null
        job?.cancel()
    }

    /**
     * Playback position extrapolated to now. MediaController reports the position as of the last
     * state update, so it must be advanced by the elapsed time when the player is running.
     */
    private fun currentPlaybackPositionMs(): Long? {
        val state = MediaControllerManager.getActiveController(context)?.playbackState ?: return null
        if (state.state != PlaybackState.STATE_PLAYING || state.lastPositionUpdateTime <= 0L || state.playbackSpeed <= 0f) {
            return state.position
        }
        val elapsedMs = SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
        return state.position + (elapsedMs * state.playbackSpeed).toLong()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld != true) {
            try {
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AAMediaMate:LyricSync").apply {
                    setReferenceCounted(false)
                    acquire(WAKELOCK_TIMEOUT)
                }
                Log.d("MediaBridge", "⚡ WakeLock acquired")
            } catch (e: Exception) {
                Log.e("MediaBridge", "Failed to acquire WakeLock", e)
            }
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d("MediaBridge", "⚡ WakeLock released")
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e("MediaBridge", "Failed to release WakeLock", e)
        }
    }

    private fun updateLyricLine(mediaSession: MediaSessionCompat, originalInfo: MediaInfo, lyricLine: String, nextLyricLine: String?) {
        val metadataBuilder = MediaMetadataCompat.Builder()
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, originalInfo.duration)

        // Always set the album to "From [App Name]"
        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "From ${originalInfo.appName}")

        val showAlbumName = SettingsManager.getShowAlbumName(context)
        val showNextLyricLine = SettingsManager.getShowNextLyricLine(context)

        // Blank lines are only used to clear the lyrics (e.g. no lyrics found).
        if (lyricLine.isNotBlank()) {
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, lyricLine)

            val artistText = if (showNextLyricLine) {
                nextLyricLine?.takeIf { it.isNotBlank() } ?: buildSongInfoText(originalInfo, showAlbumName)
            } else {
                buildSongInfoText(originalInfo, showAlbumName)
            }

            if (artistText.isNotBlank()) {
                metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artistText)
            }

        } else {
            // When no lyric is displayed, restore the original media info formatted correctly
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, originalInfo.title)

            val artist = originalInfo.artist.takeIf { it.isNotBlank() }
            val album = originalInfo.album.takeIf { it.isNotBlank() }
            
            val artistText = if (showAlbumName) {
                listOfNotNull(artist, album).joinToString(" - ")
            } else {
                artist ?: ""
            }

            if (artistText.isNotBlank()) {
                metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artistText)
            }
        }

        originalInfo.albumArt?.let {
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it)
        }

        mediaSession.setMetadata(metadataBuilder.build())
    }

    private fun buildSongInfoText(originalInfo: MediaInfo, showAlbumName: Boolean): String {
        val songInfoParts = mutableListOf<String>()
        originalInfo.title.takeIf { it.isNotBlank() }?.let { songInfoParts.add(it) }
        originalInfo.artist.takeIf { it.isNotBlank() }?.let { songInfoParts.add(it) }
        if (showAlbumName) {
            originalInfo.album.takeIf { it.isNotBlank() }?.let { songInfoParts.add(it) }
        }
        return songInfoParts.joinToString(" - ")
    }
}
