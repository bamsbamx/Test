package com.trailbook.platform

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder

class RecordingForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = pendingNotification
        if (notification != null) {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    companion object {
        const val NOTIFICATION_ID = 42
        private var pendingNotification: Notification? = null

        fun update(notification: Notification) {
            pendingNotification = notification
            val context = AndroidContext.context
            val intent = Intent(context, RecordingForegroundService::class.java)
            try {
                androidx.core.content.ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
            }
        }

        fun stop() {
            pendingNotification = null
            val context = AndroidContext.context
            context.stopService(Intent(context, RecordingForegroundService::class.java))
        }
    }
}
