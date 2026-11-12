package com.trailbook

import com.trailbook.core.geo.CoordinateFormat
import com.trailbook.core.geo.GeoMath
import com.trailbook.core.geo.GeoPoint
import com.trailbook.core.geo.SunriseSunset
import com.trailbook.core.geo.Utm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GeoTest {

    @Test
    fun haversineKnownDistance() {
        val a = GeoPoint(47.0, 8.0)
        val east = GeoMath.destination(a, 1000.0, 90.0)
        assertEquals(1000.0, GeoMath.haversineMeters(a, east), 1.0)
        assertEquals(0.0, GeoMath.haversineMeters(a, a), 0.0001)
    }

    @Test
    fun bearingEastIs90() {
        val bearing = GeoMath.initialBearingDeg(GeoPoint(47.0, 8.0), GeoPoint(47.0, 8.01))
        assertEquals(90.0, bearing, 1.0)
    }

    @Test
    fun destinationReturnsToStart() {
        val start = GeoPoint(47.0, 8.0)
        val dest = GeoMath.destination(start, 1000.0, 90.0)
        assertEquals(1000.0, GeoMath.haversineMeters(start, dest), 1.0)
        assertEquals(90.0, GeoMath.initialBearingDeg(start, dest), 0.5)
    }

    @Test
    fun pointToSegmentDistance() {
        val a = GeoPoint(47.0, 8.0)
        val b = GeoPoint(47.0, 8.01)
        val mid = GeoPoint(47.0, 8.005)
        val offset = GeoMath.destination(mid, 100.0, 0.0)
        assertEquals(100.0, GeoMath.pointToSegmentMeters(offset, a, b), 2.0)
        assertEquals(0.0, GeoMath.pointToSegmentMeters(mid, a, b), 0.5)
    }

    @Test
    fun utmRoundtrip() {
        val p = GeoPoint(47.3769, 8.5417)
        val utm = Utm.fromLatLon(p)
        assertEquals(32, utm.zone)
        assertTrue(!utm.isSouthHemisphere)
        val back = Utm.toLatLon(utm)
        assertEquals(p.latitude, back.latitude, 0.0005)
        assertEquals(p.longitude, back.longitude, 0.0001)
    }

    @Test
    fun utmSouthernHemisphere() {
        val p = GeoPoint(-33.86, 151.21)
        val utm = Utm.fromLatLon(p)
        assertTrue(utm.isSouthHemisphere)
        val back = Utm.toLatLon(utm)
        assertEquals(p.latitude, back.latitude, 0.0005)
        assertEquals(p.longitude, back.longitude, 0.0001)
    }

    @Test
    fun coordinateParseDecimalDegrees() {
        val result = CoordinateFormat.parse("47.3769, 8.5417")
        assertIs<CoordinateFormat.ParseResult.Success>(result)
        assertEquals(47.3769, result.point.latitude, 1e-6)
    }

    @Test
    fun coordinateParseUtm() {
        val text = "32T 481000 5247000"
        val result = CoordinateFormat.parse(text)
        assertIs<CoordinateFormat.ParseResult.Success>(result)
        assertEquals(47.376, result.point.latitude, 0.05)
        assertEquals(8.748, result.point.longitude, 0.05)
    }

    @Test
    fun coordinateParseRejectsGarbage() {
        assertIs<CoordinateFormat.ParseResult.Failure>(CoordinateFormat.parse("Zurich main station"))
    }

    @Test
    fun coordinateParseOutOfRange() {
        assertIs<CoordinateFormat.ParseResult.Failure>(CoordinateFormat.parse("99.0, 200.0"))
    }

    @Test
    fun formatUtmAndBack() {
        val p = GeoPoint(47.3769, 8.5417)
        val text = CoordinateFormat.formatUtm(p)
        val back = CoordinateFormat.parseUtmOrNull(text)
        assertNotNull(back)
        assertEquals(p.latitude, back.latitude, 0.0005)
    }

    @Test
    fun sunriseSunsetZurichSummer() {
        val times = SunriseSunset.compute(47.3769, 8.5417, 1_751_328_000_000L, 2.0)
        assertNotNull(times.sunriseEpochMillis)
        assertNotNull(times.sunsetEpochMillis)
        assertTrue(times.sunsetEpochMillis!! > times.sunriseEpochMillis!!)
        assertFalse(times.polarDay)
        assertFalse(times.polarNight)
    }

    @Test
    fun polarNightInWinter() {
        val times = SunriseSunset.compute(78.22, 15.65, 1_767_225_600_000L, 1.0)
        assertTrue(times.polarNight || times.sunriseEpochMillis == null)
    }

    private fun assertFalse(v: Boolean) {
        kotlin.test.assertFalse(v)
    }
}
