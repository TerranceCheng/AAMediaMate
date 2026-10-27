package com.gululu.aamediamate.lyrics

import android.content.Context
import com.gululu.aamediamate.SettingsManager
import com.gululu.aamediamate.models.RomanizeLanguage
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class LyricRomanizerTest {

    private lateinit var mockContext: Context

    @Before
    fun setUp() {
        mockContext = mockk(relaxed = true)
        mockkObject(SettingsManager)
        every { SettingsManager.getRomanizeEnabled(any()) } returns true
        every { SettingsManager.getRomanizeLanguages(any()) } returns
            setOf(RomanizeLanguage.JAPANESE, RomanizeLanguage.KOREAN)
    }

    @After
    fun tearDown() {
        unmockkObject(SettingsManager)
    }

    @Test
    fun `detectLanguage returns null for latin text`() {
        assertNull(LyricRomanizer.detectLanguage("Hello world 123"))
    }

    @Test
    fun `detectLanguage returns null for han-only text`() {
        assertNull(LyricRomanizer.detectLanguage("我爱你"))
    }

    @Test
    fun `detectLanguage returns JAPANESE for kana text`() {
        assertEquals(RomanizeLanguage.JAPANESE, LyricRomanizer.detectLanguage("こんにちは"))
    }

    @Test
    fun `detectLanguage returns KOREAN for hangul text`() {
        assertEquals(RomanizeLanguage.KOREAN, LyricRomanizer.detectLanguage("안녕하세요"))
    }

    @Test
    fun `romanize japanese converts kana and kanji to latin`() {
        val result = LyricRomanizer.romanize(mockContext, "桜が咲く")
        assertEquals(false, result.any { isHanOrKana(it) })
        assertEquals(true, result.lowercase().contains("saku"))
    }

    @Test
    fun `romanize korean converts hangul to latin`() {
        val result = LyricRomanizer.romanize(mockContext, "안녕하세요")
        assertEquals(false, result.any { isHangul(it) })
        assertEquals(true, result.lowercase().contains("annyeong"))
    }

    @Test
    fun `romanize returns text unchanged when disabled`() {
        every { SettingsManager.getRomanizeEnabled(any()) } returns false
        assertEquals("안녕하세요", LyricRomanizer.romanize(mockContext, "안녕하세요"))
    }

    @Test
    fun `romanize returns text unchanged when language not enabled`() {
        every { SettingsManager.getRomanizeLanguages(any()) } returns setOf(RomanizeLanguage.JAPANESE)
        assertEquals("안녕하세요", LyricRomanizer.romanize(mockContext, "안녕하세요"))
    }

    private fun isHangul(char: Char): Boolean {
        val code = char.code
        return code in 0xAC00..0xD7A3 || code in 0x1100..0x11FF
    }

    private fun isHanOrKana(char: Char): Boolean {
        val code = char.code
        return code in 0x4E00..0x9FFF || code in 0x3040..0x309F || code in 0x30A0..0x30FF
    }
}
