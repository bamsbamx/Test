package com.trailbook.app

import com.trailbook.platform.createAppFileSystem
import com.trailbook.platform.AndroidContext
import com.trailbook.platform.AndroidLocationProvider
import com.trailbook.platform.AndroidPermissionManager
import com.trailbook.platform.AndroidSensorProvider
import com.trailbook.platform.FeedbackPlayer
import com.trailbook.platform.FileSharer
import com.trailbook.platform.NetworkMonitor
import com.trailbook.platform.RecordingNotification
import com.trailbook.platform.createSecureStore
import com.trailbook.platform.createHttpTransport

actual fun createAppContainer(): AppContainer = AppContainer(
    fileSystem = createAppFileSystem(),
    httpTransport = createHttpTransport(),
    networkMonitor = NetworkMonitor(),
    secureStore = createSecureStore(),
    permissionManager = AndroidPermissionManager(),
    locationProvider = AndroidLocationProvider(),
    sensorProvider = AndroidSensorProvider(),
    fileSharer = FileSharer(),
    recordingNotification = RecordingNotification(),
    feedbackPlayer = FeedbackPlayer(),
)
