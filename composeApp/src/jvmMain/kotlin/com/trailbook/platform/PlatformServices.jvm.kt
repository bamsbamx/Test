package com.trailbook.platform

import com.trailbook.core.model.TrackPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

actual fun createPermissionManager(): PermissionManager = DesktopPermissionManager

actual fun createLocationProvider(): LocationProvider = DesktopLocationProvider

actual fun createSensorProvider(): SensorProvider = DesktopSensorProvider

object DesktopPermissionManager : PermissionManager {
    override fun status(type: PermissionType): PermissionStatus = PermissionStatus.UNAVAILABLE
    override suspend fun request(type: PermissionType): PermissionStatus = PermissionStatus.UNAVAILABLE
}

object DesktopLocationProvider : LocationProvider {
    override val isAvailable: Boolean get() = false
    override fun permissionType(): PermissionType = PermissionType.LOCATION_FINE
    override fun updates(intervalMillis: Long): Flow<LocationUpdate> =
        kotlinx.coroutines.flow.flowOf(LocationUpdate.Unavailable("GPS is not available on the desktop build"))
    override suspend fun lastKnown(): TrackPoint? = null
}

object DesktopSensorProvider : SensorProvider {
    override fun isCompassAvailable(): Boolean = false
    override fun isBarometerAvailable(): Boolean = false
    override fun isMagnetometerAvailable(): Boolean = false
    override fun readings(): Flow<SensorReading> = emptyFlow()
}
