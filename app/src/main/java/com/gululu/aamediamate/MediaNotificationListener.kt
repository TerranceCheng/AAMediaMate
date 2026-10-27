package com.gululu.aamediamate

import android.app.Notification
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class MediaNotificationListener : NotificationListenerService() {
    private val syncHandler = Handler(Looper.getMainLooper())
    private val SYNC_DELAY = 1000L
    private var syncScheduled = false

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.notification.category != Notification.CATEGORY_TRANSPORT) return

        val packageName = sbn.packageName
        Log.d("MediaBridge", "📥 Media Notification from $packageName")

        sync()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        sync()
    }

    private fun sync() {
        // Apps that refresh their notification faster than SYNC_DELAY must not keep
        // pushing the sync out forever, otherwise a track change may never be picked up.
        if (syncScheduled) return
        syncScheduled = true
        syncHandler.postDelayed(syncRunnable, SYNC_DELAY)
    }

    private val syncRunnable = Runnable {
        syncScheduled = false
        MediaBridgeSessionManager.updateFromMediaInfo(MediaInformationRetriever.refreshCurrentMediaInfo(this))
        MediaBridgeService.refreshBrowserData()
    }

    override fun onDestroy() {
        super.onDestroy()
        syncHandler.removeCallbacks(syncRunnable)
    }
}
