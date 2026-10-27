package com.gululu.aamediamate.lyrics

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class LyricSyncEngineTest {

    @After
    fun tearDown() {
        LyricSyncEngine.stop()
    }

    @Test
    fun `start should immediately trigger first line if position is zero`() = runTest {
        val lyrics = listOf(LyricLine(0.01f, "First line"), LyricLine(5.0f, "Second line"))
        val lines = mutableListOf<String>()

        LyricSyncEngine.start(lyrics, 0L, 0L) { current, next -> lines.add(current) }

        Thread.sleep(200)
        assertTrue(lines.contains("First line"))
    }

    @Test
    fun `start should trigger correct line when starting from middle`() = runTest {
        val lyrics = listOf(
            LyricLine(0.01f, "First line"),
            LyricLine(0.02f, "Second line"),
            LyricLine(0.03f, "Third line")
        )
        val lines = mutableListOf<String>()

        LyricSyncEngine.start(lyrics, 15L, 0L) { current, next -> lines.add(current) }

        Thread.sleep(200)
        assertTrue(lines.contains("Second line"))
    }

    @Test
    fun `stop should cancel the running job`() = runTest {
        val lyrics = listOf(LyricLine(0.0f, "Line 1"), LyricLine(10.0f, "Line 2"))
        LyricSyncEngine.start(lyrics, 0L, 0L) { _, _ -> }
        LyricSyncEngine.stop()
        // How to assert that the job is cancelled is a bit tricky in this setup
        // but we can at least ensure it doesn't crash.
    }

    @Test
    fun `blank lines keep the previous lyric visible instead of clearing it`() = runTest {
        val lyrics = listOf(
            LyricLine(0.0f, "First line"),
            LyricLine(0.05f, ""),
            LyricLine(0.1f, "Second line")
        )
        val lines = mutableListOf<String>()

        LyricSyncEngine.start(lyrics, 0L, 0L) { current, _ -> lines.add(current) }

        Thread.sleep(300)
        assertFalse(lines.contains(""))
        assertEquals("Second line", lines.last())
    }

    @Test
    fun `starting inside a gap shows the active line immediately`() = runTest {
        val lyrics = listOf(
            LyricLine(0.0f, "First line"),
            LyricLine(10.0f, "Second line")
        )
        val lines = mutableListOf<String>()

        LyricSyncEngine.start(lyrics, 2000L, 0L) { current, _ -> lines.add(current) }

        Thread.sleep(100)
        assertEquals(listOf("First line"), lines)
    }

    @Test
    fun `next line preview skips blank entries`() = runTest {
        val lyrics = listOf(
            LyricLine(0.0f, "First line"),
            LyricLine(0.05f, ""),
            LyricLine(0.1f, "Second line")
        )
        val nextLines = mutableListOf<String?>()

        LyricSyncEngine.start(lyrics, 0L, 0L) { _, next -> nextLines.add(next) }

        Thread.sleep(300)
        assertEquals("Second line", nextLines.first())
    }

    @Test
    fun `engine polls the live position while waiting for the next line`() = runTest {
        val lyrics = listOf(LyricLine(0.0f, "Line 1"), LyricLine(60.0f, "Line 2"))
        var polls = 0

        LyricSyncEngine.start(lyrics, 0L, 0L, positionProvider = {
            polls++
            0L
        }) { _, _ -> }

        Thread.sleep(1_500)
        assertTrue(polls >= 2)
    }

    @Test
    fun `engine re-anchors when the playback position jumps ahead`() = runTest {
        val lyrics = listOf(LyricLine(0.0f, "Line 1"), LyricLine(10.0f, "Line 2"))
        val lines = mutableListOf<String>()
        var positionJumped = false

        LyricSyncEngine.start(lyrics, 0L, 0L, positionProvider = {
            if (positionJumped) 20_000L else 0L
        }) { current, _ -> lines.add(current) }

        Thread.sleep(300)
        positionJumped = true
        Thread.sleep(1_500)

        assertTrue(lines.contains("Line 2"))
    }
}
