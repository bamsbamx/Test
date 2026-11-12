package com.trailbook.core.geo

import kotlinx.serialization.Serializable

@Serializable
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(latitude in -90.0..90.0) { "latitude out of range: $latitude" }
        require(longitude in -180.0..180.0) { "longitude out of range: $longitude" }
    }

    val latRad: Double get() = Math.toRadians(latitude)
    val lonRad: Double get() = Math.toRadians(longitude)

    fun distanceTo(other: GeoPoint): Double = GeoMath.haversineMeters(this, other)

    fun bearingTo(other: GeoPoint): Double = GeoMath.initialBearingDeg(this, other)

    fun offset(meters: Double, bearingDeg: Double): GeoPoint = GeoMath.destination(this, meters, bearingDeg)

    fun isValid(): Boolean = latitude.isFinite() && longitude.isFinite() &&
        latitude in -90.0..90.0 && longitude in -180.0..180.0 && !(latitude == 0.0 && longitude == 0.0)

    companion object {
        fun normalize(lat: Double, lon: Double): GeoPoint =
            GeoPoint(com.trailbook.core.geo.GeoMath.normalizeLat(lat).coerceIn(-90.0, 90.0), com.trailbook.core.geo.GeoMath.normalizeLon(lon))

        fun tryCreate(lat: Double, lon: Double): GeoPoint? =
            if (lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0) GeoPoint(lat, lon) else null
    }
}
