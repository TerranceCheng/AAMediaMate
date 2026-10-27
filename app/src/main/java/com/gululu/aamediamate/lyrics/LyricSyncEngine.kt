package com.gululu.aamediamate.lyrics

import kotlinx.coroutines.*
import android.util.Log
import kotlin.math.abs

object LyricSyncEngine {
    private const val POSITION_POLL_INTERVAL_MS = 1_000L
    private const val DRIFT_TOLERANCE_MS = 500L

    private var currentJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * @param positionProvider live playback position in ms, or null when unavailable. The engine
     * polls it while waiting for the next line and re-anchors when it drifts from the wall clock.
     */
    @Synchronized
    fun start(
        lyrics: List<LyricLine>,
        startPositionMs: Long,
        offsetMs: Long = 0L,
        positionProvider: () -> Long? = { null },
        onLineChanged: (String, String?) -> Unit
    ): Job {
        currentJob?.cancel()

        val job = scope.launch {
            Log.d("MediaBridge", "🎤 LyricSyncEngine starting with position: ${startPositionMs}ms, offset: ${offsetMs}ms")

            val clock = PlaybackClock(positionProvider, startPositionMs)

            // Find the current line based on actual playback position (not offset)
            val currentTimeSec = clock.positionMs() / 1000.0f
            val nextLineIndex = lyrics.indexOfFirst { it.timeSec > currentTimeSec }
                .let { if (it == -1) lyrics.size else it }

            // Blank entries are gap markers, not lyrics. Show the line that is already active
            // right away so restarting mid-gap never flashes the song title.
            val activeText = lyrics.subList(0, nextLineIndex).lastOrNull { it.text.isNotBlank() }?.text
            if (activeText != null) {
                onLineChanged(activeText, nextNonBlankTextFrom(lyrics, nextLineIndex))
            }

            for (i in nextLineIndex until lyrics.size) {
                val line = lyrics[i]
                clock.awaitPosition((line.timeSec * 1000).toLong() + offsetMs)

                // Keep the previous line visible during instrumental gaps.
                if (line.text.isBlank()) continue

                Log.d("MediaBridge", "🎤 Showing line: ${line.text}")
                onLineChanged(line.text, nextNonBlankTextFrom(lyrics, i + 1))
            }

            Log.d("MediaBridge", "🎤 LyricSyncEngine finished all lines")
        }

        currentJob = job
        return job
    }

    private fun nextNonBlankTextFrom(lyrics: List<LyricLine>, fromIndex: Int): String? {
        for (i in fromIndex until lyrics.size) {
            if (lyrics[i].text.isNotBlank()) return lyrics[i].text
        }
        return null
    }

    @Synchronized
    fun stop() {
        currentJob?.cancel()
        currentJob = null
        Log.d("MediaBridge", "🎤 LyricSyncEngine stopped")
    }

    /** Stops only if [job] is still the active sync, so a stale caller cannot stop a newer one. */
    @Synchronized
    fun stopIfCurrent(job: Job?) {
        if (job != null && currentJob === job) {
            stop()
        }
    }

    fun destroy() {
        scope.cancel()
        Log.d("MediaBridge", "🎤 LyricSyncEngine destroyed")
    }

    /**
     * Playback clock anchored to the real position reported by the player instead of pure wall
     * time, so pauses, buffering and slow position reporting cannot accumulate drift.
     */
    private class PlaybackClock(
        private val positionProvider: () -> Long?,
        initialPositionMs: Long
    ) {
        private var anchorWallMs = System.currentTimeMillis()
        private var anchorPositionMs = initialPositionMs

        fun positionMs(): Long {
            val now = System.currentTimeMillis()
            val predicted = anchorPositionMs + (now - anchorWallMs)
            val actual = positionProvider() ?: return predicted

            if (abs(actual - predicted) > DRIFT_TOLERANCE_MS) {
                anchorWallMs = now
                anchorPositionMs = actual
                return actual
            }
            return predicted
        }

        suspend fun awaitPosition(targetPositionMs: Long) {
            while (true) {
                val remainingMs = targetPositionMs - positionMs()
                if (remainingMs <= 0) return
                delay(minOf(remainingMs, POSITION_POLL_INTERVAL_MS))
            }
        }
    }
}