package com.trailbook.app

import com.trailbook.platform.createAppFileSystem
import com.trailbook.platform.FeedbackPlayer
import com.trailbook.platform.FileSharer
import com.trailbook.platform.IosLocationProvider
import com.trailbook.platform.IosPermissionManager
import com.trailbook.platform.IosSensorProvider
import com.trailbook.platform.NetworkMonitor
import com.trailbook.platform.RecordingNotification
import com.trailbook.platform.createSecureStore
import com.trailbook.platform.createHttpTransport

actual fun createAppContainer(): AppContainer = AppContainer(
    fileSystem = createAppFileSystem(),
    httpTransport = createHttpTransport(),
    networkMonitor = NetworkMonitor(),
    secureStore = createSecureStore(),
    permissionManager = IosPermissionManager(),
    locationProvider = IosLocationProvider(),
    sensorProvider = IosSensorProvider(),
    fileSharer = FileSharer(),
    recordingNotification = RecordingNotification(),
    feedbackPlayer = FeedbackPlayer(),
)
