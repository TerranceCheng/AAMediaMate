package com.gululu.aamediamate.lyrics

import android.os.SystemClock
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.math.abs
import kotlin.math.ceil

/** Synchronizes lyrics within the caller's job, using a monotonic playback clock. */
object LyricSyncEngine {
    private const val DRIFT_TOLERANCE_MS = 500L

    /**
     * Emits lyric lines as playback reaches them. Timestamp-only lines are instrumental gaps:
     * they are skipped so the previous sung line stays on screen instead of reverting to the
     * song title. [positionProvider] may return the live player position; when it disagrees with
     * the monotonic clock by more than 500 ms the clock is re-anchored to it, so pausing or
     * buffering does not accumulate drift.
     */
    suspend fun sync(
        lyrics: List<LyricLine>,
        startPositionMs: Long,
        offsetMs: Long = 0L,
        playbackSpeed: Float = 1f,
        nowMs: () -> Long = SystemClock::elapsedRealtime,
        positionProvider: () -> Long? = { null },
        onLineChanged: (String, String?) -> Unit
    ) {
        if (lyrics.isEmpty() || !playbackSpeed.isFinite() || playbackSpeed <= 0f) return
        var anchoredAt = nowMs()
        var anchoredPosition = startPositionMs
        var emitted = false
        var lastText: String? = null

        fun rawPosition(): Long {
            val predicted = anchoredPosition +
                ((nowMs() - anchoredAt).coerceAtLeast(0) * playbackSpeed.toDouble()).toLong()
            val actual = positionProvider()
            if (actual != null && abs(actual - predicted) > DRIFT_TOLERANCE_MS) {
                anchoredAt = nowMs()
                anchoredPosition = actual
                return actual
            }
            return predicted
        }

        while (true) {
            currentCoroutineContext().ensureActive()
            val position = rawPosition()
            val index = lyrics.indexOfLast { it.timeSec * 1000.0 <= position - offsetMs }
            val text = lyrics.subList(0, index + 1).lastOrNull { it.text.isNotBlank() }?.text
            if (!emitted || text != lastText) {
                onLineChanged(text.orEmpty(), nextNonBlankTextFrom(lyrics, index + 1))
                lastText = text
                emitted = true
            }
            val next = lyrics.subList(index + 1, lyrics.size).firstOrNull { it.text.isNotBlank() } ?: return
            val remainingMs = next.timeSec * 1000.0 + offsetMs - position
            delay(ceil(remainingMs / playbackSpeed).toLong().coerceAtLeast(1))
        }
    }

    private fun nextNonBlankTextFrom(lyrics: List<LyricLine>, fromIndex: Int): String? =
        lyrics.subList(fromIndex.coerceAtLeast(0), lyrics.size)
            .firstOrNull { it.text.isNotBlank() }
            ?.text
}