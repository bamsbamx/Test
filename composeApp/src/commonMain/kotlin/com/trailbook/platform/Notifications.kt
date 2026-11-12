package com.trailbook.platform

expect class RecordingNotification() {
    fun show(distanceMeters: Double, durationSeconds: Double)
    fun hide()
}
