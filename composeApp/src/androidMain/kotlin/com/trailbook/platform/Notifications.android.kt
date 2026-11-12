package com.trailbook.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

actual class RecordingNotification actual constructor() {
    private val manager get() = AndroidContext.context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun build(distanceMeters: Double, durationSeconds: Double): Notification {
        val minutes = (durationSeconds / 60).toInt()
        val km = distanceMeters / 1000
        val text = com.trailbook.core.format.Str.fmt("%s km • %d min", com.trailbook.core.format.NumFormat.fixed(km, 2), minutes)
        val notification = Notification.Builder(AndroidContext.context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Recording track")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        return notification
    }

    actual fun show(distanceMeters: Double, durationSeconds: Double) {
        val channel = NotificationChannel(CHANNEL_ID, "Track recording", NotificationManager.IMPORTANCE_LOW)
        manager.createNotificationChannel(channel)
        RecordingForegroundService.update(build(distanceMeters, durationSeconds))
    }

    actual fun hide() {
        RecordingForegroundService.stop()
    }

    companion object {
        const val CHANNEL_ID = "recording"
    }
}
