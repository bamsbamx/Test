package com.trailbook.platform

import com.trailbook.core.model.TrackPoint
import kotlinx.coroutines.flow.Flow

enum class PermissionStatus { GRANTED, DENIED, NOT_DETERMINED, UNAVAILABLE }

enum class PermissionType { LOCATION_FINE, LOCATION_BACKGROUND, CAMERA, NOTIFICATIONS }

interface PermissionManager {
    fun status(type: PermissionType): PermissionStatus
    suspend fun request(type: PermissionType): PermissionStatus
}

interface LocationProvider {
    val isAvailable: Boolean
    fun permissionType(): PermissionType
    fun updates(intervalMillis: Long = 2000): Flow<LocationUpdate>
    suspend fun lastKnown(): TrackPoint?
}

sealed interface LocationUpdate {
    data class Success(val point: TrackPoint) : LocationUpdate
    data class Denied(val message: String) : LocationUpdate
    data class Unavailable(val message: String) : LocationUpdate
    data class Failed(val message: String) : LocationUpdate
}
