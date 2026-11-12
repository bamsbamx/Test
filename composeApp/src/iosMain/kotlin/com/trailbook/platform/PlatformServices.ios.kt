package com.trailbook.platform

actual fun createPermissionManager(): PermissionManager = IosPermissionManager()

actual fun createLocationProvider(): LocationProvider = IosLocationProvider()

actual fun createSensorProvider(): SensorProvider = IosSensorProvider()
