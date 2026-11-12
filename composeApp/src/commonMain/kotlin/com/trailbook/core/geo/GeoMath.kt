package com.trailbook.core.geo

import kotlin.math.*
import kotlinx.serialization.Serializable

object GeoMath {
    const val EARTH_RADIUS_M = 6371008.8

    fun haversineMeters(a: GeoPoint, b: GeoPoint): Double {
        val dLat = b.latRad - a.latRad
        val dLon = b.lonRad - a.lonRad
        val s = sin(dLat / 2).pow(2) + cos(a.latRad) * cos(b.latRad) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(s.coerceIn(0.0, 1.0)))
    }

    fun initialBearingDeg(from: GeoPoint, to: GeoPoint): Double {
        val dLon = to.lonRad - from.lonRad
        val y = sin(dLon) * cos(to.latRad)
        val x = cos(from.latRad) * sin(to.latRad) - sin(from.latRad) * cos(to.latRad) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    fun destination(from: GeoPoint, meters: Double, bearingDeg: Double): GeoPoint {
        val d = meters / EARTH_RADIUS_M
        val br = Math.toRadians(bearingDeg)
        val lat1 = from.latRad
        val lon1 = from.lonRad
        val lat2 = asin(sin(lat1) * cos(d) + cos(lat1) * sin(d) * cos(br))
        val lon2 = lon1 + atan2(sin(br) * sin(d) * cos(lat1), cos(d) - sin(lat1) * sin(lat2))
        return GeoPoint.normalize(Math.toDegrees(lat2), Math.toDegrees(lon2))
    }

    fun normalizeLat(lat: Double): Double = when {
        lat > 90.0 -> 180.0 - lat
        lat < -90.0 -> -180.0 - lat
        else -> lat
    }

    fun normalizeLon(lon: Double): Double {
        var l = lon
        while (l > 180.0) l -= 360.0
        while (l < -180.0) l += 360.0
        return l
    }

    fun normalize(lat: Double, lon: Double): GeoPoint = GeoPoint(normalizeLat(lat).coerceIn(-90.0, 90.0), normalizeLon(lon))

    fun crossTrackDistanceMeters(point: GeoPoint, pathStart: GeoPoint, pathEnd: GeoPoint): Double {
        val dist = haversineMeters(pathStart, point)
        val bearingTo = initialBearingDeg(pathStart, point)
        val bearingPath = initialBearingDeg(pathStart, pathEnd)
        val angular = dist / EARTH_RADIUS_M
        return abs(asin(sin(angular) * sin(Math.toRadians(bearingTo - bearingPath))) * EARTH_RADIUS_M)
    }

    fun pointToSegmentMeters(p: GeoPoint, a: GeoPoint, b: GeoPoint): Double {
        if (a == b) return haversineMeters(p, a)
        val crossStart = crossTrackDistanceMeters(p, a, b)
        val bearingAB = initialBearingDeg(a, b)
        val bearingAP = initialBearingDeg(a, p)
        val along = acos(
            cos(haversineMeters(a, p) / EARTH_RADIUS_M) / cos(crossStart / EARTH_RADIUS_M).coerceAtLeast(1e-12)
        ) * EARTH_RADIUS_M
        return if (along < 0 || along > haversineMeters(a, b)) {
            min(haversineMeters(p, a), haversineMeters(p, b))
        } else {
            val sign = if (((bearingAP - bearingAB + 540.0) % 360.0) - 180.0 > 0) -1.0 else 1.0
            abs(crossStart) * sign
        }
    }

    fun boundingBox(points: List<GeoPoint>): BoundingBox? {
        if (points.isEmpty()) return null
        var minLat = 90.0; var maxLat = -90.0; var minLon = 180.0; var maxLon = -180.0
        for (p in points) {
            if (p.latitude < minLat) minLat = p.latitude
            if (p.latitude > maxLat) maxLat = p.latitude
            if (p.longitude < minLon) minLon = p.longitude
            if (p.longitude > maxLon) maxLon = p.longitude
        }
        return BoundingBox(minLat, minLon, maxLat, maxLon)
    }
}

@Serializable
data class BoundingBox(val minLat: Double, val minLon: Double, val maxLat: Double, val maxLon: Double) {
    val center: GeoPoint get() = GeoPoint((minLat + maxLat) / 2, (minLon + maxLon) / 2)
    fun contains(p: GeoPoint): Boolean = p.latitude in minLat..maxLat && p.longitude in minLon..maxLon
    fun expandedByMeters(meters: Double): BoundingBox {
        val dLat = Math.toDegrees(meters / GeoMath.EARTH_RADIUS_M)
        val latRad = Math.toRadians((minLat + maxLat) / 2)
        val dLon = Math.toDegrees(meters / (GeoMath.EARTH_RADIUS_M * max(cos(latRad), 1e-6)))
        return BoundingBox(minLat - dLat, minLon - dLon, maxLat + dLat, maxLon + dLon)
    }
}
