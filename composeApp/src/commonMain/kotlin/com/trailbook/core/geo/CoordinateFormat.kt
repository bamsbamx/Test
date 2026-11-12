package com.trailbook.core.geo

import com.trailbook.core.format.Str

import kotlin.math.abs

enum class CoordinateDisplayFormat(val id: String, val label: String) {
    DECIMAL_DEGREES("dd", "DD.DDDDD°"),
    DEGREES_MINUTES("dm", "DD° MM.MMM'"),
    DEGREES_MINUTES_SECONDS("dms", "DD° MM' SS.S\""),
    UTM("utm", "UTM");

    companion object {
        fun byId(id: String): CoordinateDisplayFormat = entries.firstOrNull { it.id == id } ?: DECIMAL_DEGREES
    }
}

object CoordinateFormat {
    fun formatDd(p: GeoPoint): String {
        val latH = if (p.latitude >= 0) "N" else "S"
        val lonH = if (p.longitude >= 0) "E" else "W"
        return Str.fmt("%s %.5f, %s %.5f", latH, abs(p.latitude), lonH, abs(p.longitude))
    }

    fun formatDm(p: GeoPoint): String {
        val latH = if (p.latitude >= 0) "N" else "S"
        val lonH = if (p.longitude >= 0) "E" else "W"
        val latD = abs(p.latitude).toInt()
        val lonD = abs(p.longitude).toInt()
        val latM = (abs(p.latitude) - latD) * 60
        val lonM = (abs(p.longitude) - lonD) * 60
        return Str.fmt("%s %d° %.3f', %s %d° %.3f'", latH, latD, latM, lonH, lonD, lonM)
    }

    fun formatDms(p: GeoPoint): String {
        val latH = if (p.latitude >= 0) "N" else "S"
        val lonH = if (p.longitude >= 0) "E" else "W"
        val latD = abs(p.latitude).toInt()
        val lonD = abs(p.longitude).toInt()
        val latMf = (abs(p.latitude) - latD) * 60
        val lonMf = (abs(p.longitude) - lonD) * 60
        val latM = latMf.toInt()
        val lonM = lonMf.toInt()
        val latS = (latMf - latM) * 60
        val lonS = (lonMf - lonM) * 60
        return Str.fmt("%s %d° %d' %.1f\", %s %d° %d' %.1f\"", latH, latD, latM, latS, lonH, lonD, lonM, lonS)
    }

    fun formatUtm(p: GeoPoint): String {
        val u = Utm.fromLatLon(p)
        return Str.fmt("%d%s %.0f %.0f", u.zone, u.letter(), u.easting, u.northing)
    }

    fun format(p: GeoPoint, f: CoordinateDisplayFormat): String = when (f) {
        CoordinateDisplayFormat.DECIMAL_DEGREES -> formatDd(p)
        CoordinateDisplayFormat.DEGREES_MINUTES -> formatDm(p)
        CoordinateDisplayFormat.DEGREES_MINUTES_SECONDS -> formatDms(p)
        CoordinateDisplayFormat.UTM -> formatUtm(p)
    }

    sealed interface ParseResult {
        data class Success(val point: GeoPoint, val format: CoordinateDisplayFormat) : ParseResult
        data class Failure(val message: String) : ParseResult
    }

    fun parse(input: String): ParseResult {
        val text = input.trim()
        if (text.isEmpty()) return ParseResult.Failure("Empty input")
        val utm = parseUtmOrNull(text)
        if (utm != null) return ParseResult.Success(utm, CoordinateDisplayFormat.UTM)
        val dd = parseDecimalPair(text)
        if (dd != null) return dd
        val dm = parseDegreesMinutes(text)
        if (dm != null) return dm
        return ParseResult.Failure("Unrecognized coordinate format")
    }

    private fun parseDecimalPair(text: String): ParseResult? {
        val cleaned = text.replace(",", " ").replace(Regex("\\s+"), " ").trim()
        val parts = cleaned.split(" ")
        if (parts.size != 2) return null
        val lat = parts[0].toDoubleOrNull() ?: return null
        val lon = parts[1].toDoubleOrNull() ?: return null
        val p = GeoPoint.tryCreate(lat, lon) ?: return ParseResult.Failure("Latitude/longitude out of range")
        return ParseResult.Success(p, CoordinateDisplayFormat.DECIMAL_DEGREES)
    }

    private val HEMI = Regex("([NSEWnsew])")

    private fun parseDegreesMinutes(text: String): ParseResult? {
        val hemis = HEMI.findAll(text).map { it.value.uppercase() }.toList()
        val numbers = Regex("(-?\\d+(?:\\.\\d+)?)").findAll(text).map { it.value.toDouble() }.toList()
        if (numbers.size != 4) return null
        val latDeg = numbers[0].toInt(); val latMin = numbers[1]
        val lonDeg = numbers[2].toInt(); val lonMin = numbers[3]
        if (latMin < 0 || latMin >= 60 || lonMin < 0 || lonMin >= 60) return null
        val latSigned = applyHemisphere(latDeg.toDouble() + latMin / 60.0, hemis.getOrNull(0), isLatitude = true)
        val lonSigned = applyHemisphere(lonDeg.toDouble() + lonMin / 60.0, hemis.getOrNull(1), isLatitude = false)
        val p = GeoPoint.tryCreate(latSigned, lonSigned)
            ?: return ParseResult.Failure("Latitude/longitude out of range")
        return ParseResult.Success(p, CoordinateDisplayFormat.DEGREES_MINUTES)
    }

    private fun applyHemisphere(value: Double, hemi: String?, isLatitude: Boolean): Double {
        val negative = when (hemi) {
            "S", "W" -> true
            "N", "E" -> false
            null -> isLatitude && value < 0 || !isLatitude && value < 0
            else -> false
        }
        return if (negative) -abs(value) else abs(value)
    }

    fun parseUtmOrNull(text: String): GeoPoint? {
        val m = Regex("^(\\d{1,2})\\s*([C-HJ-NP-X])\\s+(\\d+(?:\\.\\d+)?)\\s+(\\d+(?:\\.\\d+)?)$", RegexOption.IGNORE_CASE)
            .find(text.trim()) ?: return null
        val zone = m.groupValues[1].toInt()
        if (zone !in 1..60) return null
        val letter = m.groupValues[2].uppercase()[0]
        val easting = m.groupValues[3].toDouble()
        val northing = m.groupValues[4].toDouble()
        if (easting < 100000 || easting > 999999 || northing < 0 || northing > 10000000) return null
        val south = letter < 'N'
        return Utm.toLatLon(UtmCoordinate(zone, south, easting, northing))
    }
}
