package com.trailbook.core.geo

import kotlin.math.*

data class UtmCoordinate(
    val zone: Int,
    val isSouthHemisphere: Boolean,
    val easting: Double,
    val northing: Double,
) {
    fun letter(): Char = Utm.zoneLetter(northing)
}

object Utm {
    private val K0 = 0.9996
    private val E = 0.00669438
    private val E2 = E * E
    private val E3 = E2 * E
    private val E_P2 = E / (1.0 - E)
    private val SQRT_E = sqrt(1 - E)
    private val _E = (1 - SQRT_E) / (1 + SQRT_E)
    private val _E2 = _E * _E
    private val _E3 = _E2 * _E
    private val _E4 = _E3 * _E
    private val _E5 = _E4 * _E
    private val M1 = (1 - E / 4 - 3 * E2 / 64 - 5 * E3 / 256)
    private val M2 = (3 * E / 8 + 3 * E2 / 32 + 45 * E3 / 1024)
    private val M3 = (15 * E2 / 256 + 45 * E3 / 1024)
    private val M4 = (35 * E3 / 3072)
    private val P2 = (3.0 / 2 * _E - 27.0 / 32 * _E2 - 269.0 / 144 * _E3)
    private val P3 = (21.0 / 16 * _E2 - 55.0 / 32 * _E3)
    private val P4 = (151.0 / 96 * _E3)
    private val P5 = (1097.0 / 512 * _E4)
    private val R = 6378137.0

    fun fromLatLon(point: GeoPoint): UtmCoordinate {
        val zone = zoneOf(point.longitude)
        return fromLatLon(point, zone)
    }

    fun zoneOf(lon: Double): Int = (((lon + 180.0) % 360.0) / 6).toInt().coerceIn(0, 59) + 1

    fun fromLatLon(point: GeoPoint, zone: Int): UtmCoordinate {
        val lat = point.latRad
        val lon = point.lonRad
        val lonOrigin = Math.toRadians(zoneLonOriginDeg(zone))
        val sinLat = sin(lat)
        val cosLat = cos(lat)
        val tanLat = tan(lat)
        val n = R / sqrt(1 - E * sinLat * sinLat)
        val t = tanLat * tanLat
        val c = E_P2 * cosLat * cosLat
        val a = cosLat * (lon - lonOrigin)
        val m = R * (M1 * lat - M2 * sin(2 * lat) + M3 * sin(4 * lat) - M4 * sin(6 * lat))
        val easting = K0 * n * (a + (1 - t + c) * a.pow(3) / 6 +
            (5 - 18 * t + t * t + 72 * c - 58 * E_P2) * a.pow(5) / 120) + 500000.0
        var northing = K0 * (m + n * tanLat * (a * a / 2 + (5 - t + 9 * c + 4 * c * c) * a.pow(4) / 24 +
            (61 - 58 * t + t * t + 600 * c - 330 * E_P2) * a.pow(6) / 720))
        val south = point.latitude < 0
        if (south) northing += 10000000.0
        return UtmCoordinate(zone, south, easting, northing)
    }

    fun zoneLonOriginDeg(zone: Int): Double = (zone - 1) * 6.0 - 180.0 + 3.0

    fun toLatLon(utm: UtmCoordinate): GeoPoint {
        val x = utm.easting - 500000.0
        val y = if (utm.isSouthHemisphere) utm.northing - 10000000.0 else utm.northing
        val m = y / K0
        val mu = m / (R * M1)
        val pRad = mu + P2 * sin(2 * mu) + P3 * sin(4 * mu) + P4 * sin(6 * mu) + P5 * sin(8 * mu)
        val pSin = sin(pRad)
        val pCos = cos(pRad)
        val pTan = pSin / pCos
        val epSq = E / (1 - E)
        val c = epSq * pCos * pCos
        val t = pTan * pTan
        val n = R / sqrt(1 - E * pSin * pSin)
        val d = x / (n * K0)
        val lat = pRad - (pTan * n / R) * (d * d / 2 - (5 + 3 * t + 10 * c - 4 * c * c - 9 * epSq) * d.pow(4) / 24 +
            (61 + 90 * t + 298 * c + 45 * t * t - 252 * epSq - 3 * c * c) * d.pow(6) / 720)
        val lon = (d - (1 + 2 * t + c) * d.pow(3) / 6 + (5 - 2 * c + 28 * t - 3 * c * c + 8 * epSq + 24 * t * t) *
            d.pow(5) / 120) / pCos
        return GeoPoint(Math.toDegrees(lat), zoneLonOriginDeg(utm.zone) + Math.toDegrees(lon))
    }

    fun zoneLetter(northing: Double): Char {
        val letters = "CDEFGHJKLMNPQRSTUVWX"
        val idx = ((northing / 1000000.0).toInt() + 10).coerceIn(0, 19)
        return letters[idx]
    }
}
