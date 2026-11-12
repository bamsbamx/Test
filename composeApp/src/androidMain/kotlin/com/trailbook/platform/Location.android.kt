package com.trailbook.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.trailbook.core.model.TrackPoint
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class AndroidLocationProvider : LocationProvider {
    private val locationManager: LocationManager
        get() = AndroidContext.context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    override val isAvailable: Boolean
        get() = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

    override fun permissionType(): PermissionType = PermissionType.LOCATION_FINE

    override fun updates(intervalMillis: Long): Flow<LocationUpdate> = callbackFlow {
        val granted = ContextCompat.checkSelfPermission(AndroidContext.context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            trySend(LocationUpdate.Denied("Location permission not granted"))
            awaitClose { }
            return@callbackFlow
        }
        if (!isAvailable) {
            trySend(LocationUpdate.Unavailable("Location services are disabled"))
            awaitClose { }
            return@callbackFlow
        }
        val listener = LocationListener { location ->
            trySend(LocationUpdate.Success(location.toTrackPoint()))
        }
        try {
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .filter { locationManager.isProviderEnabled(it) }
            if (providers.isEmpty()) {
                trySend(LocationUpdate.Unavailable("No location provider enabled"))
            } else {
                for (p in providers) {
                    locationManager.requestLocationUpdates(p, intervalMillis, 0f, listener, Looper.getMainLooper())
                }
            }
        } catch (e: SecurityException) {
            trySend(LocationUpdate.Denied("Location permission revoked"))
        } catch (e: Exception) {
            trySend(LocationUpdate.Failed(e.message ?: "Location error"))
        }
        awaitClose {
            locationManager.removeUpdates(listener)
        }
    }

    override suspend fun lastKnown(): TrackPoint? = try {
        val granted = ContextCompat.checkSelfPermission(AndroidContext.context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!granted) null
        else (locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER))?.toTrackPoint()
    } catch (e: SecurityException) {
        null
    }

    private fun Location.toTrackPoint(): TrackPoint = TrackPoint(
        latitude = latitude,
        longitude = longitude,
        elevation = if (hasAltitude()) altitude else null,
        time = time,
        speedMps = if (hasSpeed()) speed.toDouble() else null,
        bearingDeg = if (hasBearing()) bearing.toDouble() else null,
        accuracyM = if (hasAccuracy()) accuracy.toDouble() else null,
    )
}
