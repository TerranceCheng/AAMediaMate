package com.gululu.aamediamate.lyrics

import android.content.Context
import android.util.Log
import com.github.houbb.opencc4j.util.ZhConverterUtil
import com.gululu.aamediamate.SettingsManager
import com.gululu.aamediamate.lyrics.providers.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

data class LyricLine(val timeSec: Float, val text: String)

/**
 * Outcome of a lyrics lookup.
 * [failed] is true when at least one provider could not be queried (network/HTTP error), meaning the
 * "not found" result may be caused by an outage and should not be cached permanently.
 */
data class LyricsFetchResult(val lrc: String?, val failed: Boolean)

object LyricsManager {
    suspend fun getLyricsLrt(context: Context, title: String, artist: String, duration: String): LyricsFetchResult = withContext(Dispatchers.IO) {
        val cleanupRules = SettingsManager.getLyricsCleanupRules(context)
        var (cleanedTitle, cleanedArtist) = LyricsCleanupManager.applyRules(title, artist, cleanupRules)
        
        // Apply artist name replacements
        cleanedArtist = applyArtistNameReplacements(context, cleanedArtist)

        // Get enabled providers in order of priority
        val enabledProviders = SettingsManager.getEnabledProvidersInOrder(context)

        var anyProviderFailed = false
        for (providerConfig in enabledProviders) {
            try {
                val lrc = providerConfig.provider.getLyricsLrc(context, cleanedTitle, cleanedArtist, duration)
                if (!lrc.isNullOrBlank()) {
                    return@withContext LyricsFetchResult(lrc, failed = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MediaBridge", "Lyrics provider ${providerConfig.name} failed", e)
                anyProviderFailed = true
            }
        }
        return@withContext LyricsFetchResult(null, failed = anyProviderFailed)
    }

    private fun applyArtistNameReplacements(context: Context, artist: String): String {
        var result = artist
        val replacements = SettingsManager.getArtistNameReplacements(context)
        
        replacements.filter { it.isEnabled }.forEach { replacement ->
            if (replacement.englishName.isNotBlank() && replacement.chineseName.isNotBlank()) {
                // Case-insensitive replacement
                result = result.replace(Regex("(?i)${Regex.escape(replacement.englishName)}"), replacement.chineseName)
            }
        }
        
        return result
    }

    fun parseLrc(context: Context, lrc: String): List<LyricLine> {
        // This regex captures all timestamp tags at the beginning of the line, and the lyric text.
        // Group 1: The entire block of timestamp tags (e.g., "[00:01.23][00:02.45]")
        // Group 2: The lyric text after the timestamps
        val linePattern = Pattern.compile("((?:\\[\\d+:\\d+\\.\\d+])+)(.*)")

        // This regex is for parsing a single timestamp tag from the block captured by group 1.
        val timeTagPattern = Pattern.compile("\\[(\\d+):(\\d+\\.\\d+)]")

        val lyricLines = mutableListOf<LyricLine>()

        lrc.lineSequence().forEach { line ->
            val lineMatcher = linePattern.matcher(line)
            if (lineMatcher.matches()) {
                val tagsBlock = lineMatcher.group(1)!!
                var text = lineMatcher.group(2)!!.trim()

                // Romanize detected non-Latin languages (e.g. Japanese/Korean) into Latin text.
                // Runs before the opencc conversion so Japanese kanji is not simplified first.
                text = LyricRomanizer.romanize(context, text)

                // Chinese character conversion
                if (SettingsManager.getSimplifyEnabled(context)) {
                    text = ZhConverterUtil.toSimple(text)
                } else {
                    text = ZhConverterUtil.toTraditional(text)
                }

                val timeTagMatcher = timeTagPattern.matcher(tagsBlock)
                while (timeTagMatcher.find()) {
                    val min = timeTagMatcher.group(1)!!.toInt()
                    val sec = timeTagMatcher.group(2)!!.toFloat()
                    val timeSec = min * 60 + sec
                    lyricLines.add(LyricLine(timeSec = timeSec, text = text))
                }
            }
        }

        return lyricLines.sortedBy { it.timeSec }
    }
}
