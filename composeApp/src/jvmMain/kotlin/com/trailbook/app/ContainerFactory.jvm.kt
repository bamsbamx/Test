package com.trailbook.app

import com.trailbook.platform.createAppFileSystem
import com.trailbook.platform.DesktopLocationProvider
import com.trailbook.platform.DesktopPermissionManager
import com.trailbook.platform.DesktopSensorProvider
import com.trailbook.platform.FeedbackPlayer
import com.trailbook.platform.FileSharer
import com.trailbook.platform.NetworkMonitor
import com.trailbook.platform.RecordingNotification
import com.trailbook.platform.createHttpTransport
import com.trailbook.platform.createSecureStore

actual fun createAppContainer(): AppContainer = AppContainer(
    fileSystem = createAppFileSystem(),
    httpTransport = createHttpTransport(),
    networkMonitor = NetworkMonitor(),
    secureStore = createSecureStore(),
    permissionManager = DesktopPermissionManager,
    locationProvider = DesktopLocationProvider,
    sensorProvider = DesktopSensorProvider,
    fileSharer = FileSharer(),
    recordingNotification = RecordingNotification(),
    feedbackPlayer = FeedbackPlayer(),
)
