package com.gululu.aamediamate.lyrics.providers

import android.content.Context

interface LyricsProvider {
    suspend fun getLyricsLrc(context: Context, title: String, artist: String, duration: String): String?
}

/** Thrown when a provider could not complete the request (network, HTTP error, bad payload). */
class LyricsFetchException(message: String, cause: Throwable? = null) : Exception(message, cause)