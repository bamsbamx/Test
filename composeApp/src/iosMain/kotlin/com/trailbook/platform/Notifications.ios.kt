package com.trailbook.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSBundle
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter

@OptIn(ExperimentalForeignApi::class)
actual class RecordingNotification actual constructor() {
    private fun center(): UNUserNotificationCenter = UNUserNotificationCenter.currentNotificationCenter()

    actual fun show(distanceMeters: Double, durationSeconds: Double) {
        val content = UNMutableNotificationContent()
        content.title = "TrailBook recording"
        content.body = com.trailbook.core.format.Str.fmt(
            "%s m - %s s",
            com.trailbook.core.format.NumFormat.fixed(distanceMeters, 0),
            com.trailbook.core.format.NumFormat.fixed(durationSeconds, 0),
        )
        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(1.0, false)
        val request = UNNotificationRequest.requestWithIdentifier(
            "recording", content, trigger
        )
        center().addNotificationRequest(request) { }
    }

    actual fun hide() {
        center().removePendingNotificationRequestsWithIdentifiers(listOf("recording"))
        center().removeDeliveredNotificationWithIdentifiers(listOf("recording"))
    }
}
