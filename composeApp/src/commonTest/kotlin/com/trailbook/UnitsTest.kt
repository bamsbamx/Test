package com.trailbook

import com.trailbook.core.format.UnitFormatter
import com.trailbook.core.prefs.UnitsSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UnitsTest {

    @Test
    fun metricDistances() {
        val f = UnitFormatter(UnitsSystem.METRIC)
        assertEquals("500 m", f.distance(500.0))
        assertEquals("1.50 km", f.distance(1500.0))
        assertEquals("12.5 km", f.longDistance(12500.0))
    }

    @Test
    fun imperialDistances() {
        val f = UnitFormatter(UnitsSystem.IMPERIAL)
        assertEquals("1000 ft", f.elevation(304.8))
        assertEquals(1.0, f.distanceValue(1609.34), 0.01)
        assertTrue(f.distance(1609.34).contains("mi"))
    }

    @Test
    fun speeds() {
        val metric = UnitFormatter(UnitsSystem.METRIC)
        assertTrue(metric.speed(2.0).contains("km/h"))
        val imperial = UnitFormatter(UnitsSystem.IMPERIAL)
        assertTrue(imperial.speed(2.0).contains("mph"))
    }

    @Test
    fun durationFormatting() {
        val f = UnitFormatter(UnitsSystem.METRIC)
        assertEquals("0:59", f.duration(59.0))
        assertEquals("1:00", f.duration(60.0))
        assertEquals("1:01:00", f.duration(3660.0))
        assertTrue(f.durationLong(3660.0).contains("1"))
    }

    @Test
    fun temperature() {
        val metric = UnitFormatter(UnitsSystem.METRIC)
        assertTrue(metric.temperature(5.0).contains("°C"))
        val imperial = UnitFormatter(UnitsSystem.IMPERIAL)
        assertTrue(imperial.temperature(0.0).contains("°F"))
    }
}
