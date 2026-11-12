package com.trailbook.platform

actual fun createPermissionManager(): PermissionManager = AndroidPermissionManager()

actual fun createLocationProvider(): LocationProvider = AndroidLocationProvider()

actual fun createSensorProvider(): SensorProvider = AndroidSensorProvider()
