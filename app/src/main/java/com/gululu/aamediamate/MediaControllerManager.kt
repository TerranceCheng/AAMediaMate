package com.gululu.aamediamate

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.PlaybackState
import com.gululu.aamediamate.diagnostics.DiagnosticLogger
import com.gululu.aamediamate.diagnostics.DiagnosticModule

object MediaControllerManager {
    fun getAllControllers(context: Context): List<MediaController> {
        return try {
            val sessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as android.media.session.MediaSessionManager
            val component = ComponentName(context, MediaNotificationListener::class.java)
            val controllers = sessionManager.getActiveSessions(component)
                .filter { it.packageName != context.packageName && Global.packageAllowed(context, it.packageName) }
            DiagnosticLogger.debug(
                context,
                DiagnosticModule.MEDIA,
                "Active media controllers queried",
                mapOf("count" to controllers.size, "packages" to controllers.joinToString(",") { it.packageName })
            )
            controllers
        } catch (e: Exception) {
            // Likely SecurityException due to missing notification access
            DiagnosticLogger.error(
                context,
                DiagnosticModule.MEDIA,
                "Failed to query active media controllers",
                throwable = e
            )
            emptyList()
        }
    }

    fun getFirstController(context: Context): MediaController? {
        val controllers = getAllControllers(context)
        
        // Prioritize the controller that is currently playing
        val playingController = controllers.firstOrNull { 
            it.playbackState?.state == PlaybackState.STATE_PLAYING ||
            it.playbackState?.state == PlaybackState.STATE_BUFFERING
        }
        
        return playingController ?: controllers.firstOrNull()
    }

    fun getActiveController(context: Context): MediaController? {
        val target = MediaBridgeSessionManager.getCurrentMediaPackage() ?: return null
        return getAllControllers(context).firstOrNull { it.packageName == target }
    }
}
