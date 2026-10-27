package com.gululu.aamediamate.lyrics

import android.content.Context
import android.icu.text.Transliterator
import com.gululu.aamediamate.SettingsManager
import com.gululu.aamediamate.models.RomanizeLanguage
import fr.free.nrw.jakaroma.Jakaroma

/**
 * Romanizes non-Latin lyric lines (Japanese and Korean) into Latin script.
 *
 * Japanese is converted with jakaroma (kuromoji-based) so both kanji and kana become
 * romaji. Korean uses Android's built-in ICU Hangul-Latin transliteration. Chinese is
 * intentionally left untouched: lines are only romanized when they contain kana or
 * Hangul, so Chinese lyrics fall through to the existing simplified/traditional handling.
 */
object LyricRomanizer {

    private val hangulLatin: Transliterator by lazy {
        Transliterator.getInstance("Hangul-Latin")
    }

    private val jakaroma: Jakaroma by lazy {
        Jakaroma()
    }

    /**
     * Romanizes [text] when the user enabled romanization and enabled the detected language.
     * Returns [text] unchanged otherwise.
     */
    fun romanize(context: Context, text: String): String {
        if (text.isBlank() || !SettingsManager.getRomanizeEnabled(context)) return text

        val enabledLanguages = SettingsManager.getRomanizeLanguages(context)
        if (enabledLanguages.isEmpty()) return text

        return when (detectLanguage(text)) {
            RomanizeLanguage.JAPANESE -> {
                if (RomanizeLanguage.JAPANESE in enabledLanguages) {
                    runCatching { jakaroma.convert(text, true, true) }
                        .getOrDefault(text)
                } else {
                    text
                }
            }
            RomanizeLanguage.KOREAN -> {
                if (RomanizeLanguage.KOREAN in enabledLanguages) {
                    runCatching { hangulLatin.transliterate(text) }.getOrDefault(text)
                } else {
                    text
                }
            }
            null -> text
        }
    }

    /**
     * Detects the script of a lyric line. Returns the first detected script. Han-only text
     * (Chinese, or kanji-only Japanese) is not detected, so it is never romanized.
     */
    fun detectLanguage(text: String): RomanizeLanguage? {
        text.forEach { char ->
            when {
                isHangul(char) -> return RomanizeLanguage.KOREAN
                isKana(char) -> return RomanizeLanguage.JAPANESE
            }
        }
        return null
    }

    private fun isHangul(char: Char): Boolean {
        val code = char.code
        return code in 0xAC00..0xD7A3 || code in 0x1100..0x11FF || code in 0x3130..0x318F
    }

    private fun isKana(char: Char): Boolean {
        val code = char.code
        return code in 0x3040..0x309F || code in 0x30A0..0x30FF || code in 0x31F0..0x31FF
    }
}
