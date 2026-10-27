package com.gululu.aamediamate.lyrics.providers

import android.content.Context
import android.util.Log
import com.gululu.aamediamate.SettingsManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

object LrcApiProvider : LyricsProvider {
    private val client = OkHttpClient()

    override suspend fun getLyricsLrc(context: Context, title: String, artist: String, duration: String): String? = withContext(Dispatchers.IO) {
        val baseUrl = SettingsManager.getLrcApiBaseUri(context);
        if (baseUrl.isEmpty())
        {
            return@withContext null
        }

        try {
            val t = URLEncoder.encode(title, "UTF-8")
            val a = URLEncoder.encode(artist, "UTF-8")

            val url = "$baseUrl?title=$t&artist=$a"

            val authToken = SettingsManager.getLrcApiAuthToken(context)
            val request = Request.Builder()
                .url(url)
                .get()
                .apply { if (authToken.isNotEmpty()) addHeader("Authorization", authToken) }
                .build()

            Log.d("MediaBridge", "Sending request to Lrc Api: $request")
            val response = client.newCall(request).execute()
            Log.d("MediaBridge", "Response: $response")

            if (response.code() == 404) {
                return@withContext null
            }
            if (response.code() != 200) {
                throw LyricsFetchException("LrcApi HTTP ${response.code()}")
            }

            val body = response.body()?.string()

            body?.takeIf { it.isNotBlank() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("MediaBridge", "Error fetching lyrics from LrcApi", e)
            throw LyricsFetchException("LrcApi request failed: ${e.message}", e)
        }
    }
}