package com.trailbook.platform

import com.trailbook.core.model.TrackPoint
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
class IosPermissionManager : PermissionManager {
    private val manager = CLLocationManager()

    override fun status(type: PermissionType): PermissionStatus = when (type) {
        PermissionType.LOCATION_FINE, PermissionType.LOCATION_BACKGROUND -> when (manager.authorizationStatus()) {
            kCLAuthorizationStatusAuthorizedAlways, kCLAuthorizationStatusAuthorizedWhenInUse -> PermissionStatus.GRANTED
            kCLAuthorizationStatusNotDetermined -> PermissionStatus.NOT_DETERMINED
            else -> PermissionStatus.DENIED
        }
        PermissionType.CAMERA -> PermissionStatus.NOT_DETERMINED
    }

    override suspend fun request(type: PermissionType): PermissionStatus {
        if (status(type) != PermissionStatus.NOT_DETERMINED) return status(type)
        manager.requestWhenInUseAuthorization()
        var waited = 0
        while (status(type) == PermissionStatus.NOT_DETERMINED && waited < 50) {
            kotlinx.coroutines.delay(100)
            waited++
        }
        return status(type)
    }
}

@OptIn(ExperimentalForeignApi::class)
class IosLocationProvider : LocationProvider {

    override val isAvailable: Boolean get() = true

    override fun permissionType(): PermissionType = PermissionType.LOCATION_FINE

    override fun updates(intervalMillis: Long): Flow<LocationUpdate> = callbackFlow {
        val manager = CLLocationManager()
        val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
            override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
                val location = didUpdateLocations.lastOrNull() as? CLLocation
                if (location != null) {
                    trySend(LocationUpdate.Success(location.toTrackPoint()))
                }
            }

            override fun locationManager(manager: CLLocationManager, didFailWithError: platform.Foundation.NSError) {
                trySend(LocationUpdate.Failed(didFailWithError.localizedDescription ?: "location error"))
            }

            override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
                val status = manager.authorizationStatus()
                if (status == kCLAuthorizationStatusAuthorizedAlways || status == kCLAuthorizationStatusAuthorizedWhenInUse) {
                    manager.startUpdatingLocation()
                }
            }
        }
        manager.delegate = delegate
        manager.desiredAccuracy = kCLLocationAccuracyBest
        manager.distanceFilter = 3.0
        manager.startUpdatingLocation()
        awaitClose { manager.stopUpdatingLocation() }
    }

    override suspend fun lastKnown(): TrackPoint? {
        val manager = CLLocationManager()
        return manager.location?.toTrackPoint()
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun CLLocation.toTrackPoint(): TrackPoint = TrackPoint(
    latitude = coordinate.latitude,
    longitude = coordinate.longitude,
    elevation = altitude,
    time = (timestamp().timeIntervalSince1970 * 1000).toLong(),
    speedMps = if (speed() >= 0) speed() else null,
    bearingDeg = if (course() >= 0) course() else null,
    accuracyM = horizontalAccuracy,
)
