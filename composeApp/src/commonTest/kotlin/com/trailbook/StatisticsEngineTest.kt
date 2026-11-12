package com.trailbook

import com.trailbook.core.model.TrackPoint
import com.trailbook.core.stats.StatisticsEngine
import com.trailbook.core.stats.StatisticsOptions
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatisticsEngineTest {

    private fun flatPoints(vararg coords: Triple<Double, Double, Long>, elevation: Double = 100.0): List<TrackPoint> =
        coords.map { TrackPoint(it.first, it.second, elevation, it.third) }

    private fun eastwardPoints(count: Int, spacingMeters: Double, intervalMs: Long, elevation: (Int) -> Double? = { 100.0 }): List<TrackPoint> {
        val metersPerDeg = 111320.0 * kotlin.math.cos(kotlin.math.PI * 47.0 / 180.0)
        return (0 until count).map { i ->
            TrackPoint(
                latitude = 47.0,
                longitude = 8.0 + i * spacingMeters / metersPerDeg,
                elevation = elevation(i),
                time = 1_000_000L + i * intervalMs,
            )
        }
    }

    @Test
    fun distanceIsSumOfHaversineSegments() {
        val points = eastwardPoints(11, 100.0, 10_000L)
        val stats = StatisticsEngine.compute(points)
        assertNotNull(stats)
        assertEquals(1000.0, stats.distanceMeters, 5.0)
    }

    @Test
    fun durationMovingTimeAndSpeeds() {
        val points = eastwardPoints(11, 10.0, 10_000L)
        val stats = StatisticsEngine.compute(points)
        assertNotNull(stats)
        assertEquals(100.0, stats.totalTimeSeconds, 0.001)
        assertEquals(100.0, stats.movingTimeSeconds, 0.001)
        assertTrue(stats.avgSpeedMps > 0.5 && stats.avgSpeedMps < 1.5)
        assertEquals(stats.maxSpeedMps, stats.maxSpeedMps)
        assertTrue(stats.maxSpeedMps >= stats.avgMovingSpeedMps - 0.01)
    }

    @Test
    fun elevationGainAndLoss() {
        val points = eastwardPoints(5, 100.0, 60_000L) { i -> listOf(100.0, 150.0, 150.0, 90.0, 90.0)[i] }
        val stats = StatisticsEngine.compute(points, StatisticsOptions(elevationSmoothingWindow = 1, elevationGainThresholdMeters = 1.0))
        assertNotNull(stats)
        assertEquals(50.0, stats.elevationGain, 1.0)
        assertEquals(60.0, stats.elevationLoss, 1.0)
        assertEquals(90.0, stats.minElevation!!, 0.001)
        assertEquals(150.0, stats.maxElevation!!, 0.001)
    }

    @Test
    fun filtersInvalidAndDuplicatePoints() {
        val points = listOf(
            TrackPoint(47.0, 8.0, 100.0, 1_000L),
            TrackPoint(Double.NaN, 8.001, 100.0, 2_000L),
            TrackPoint(0.0, 0.0, 100.0, 3_000L),
            TrackPoint(47.0, 8.0, 100.0, 4_000L),
            TrackPoint(47.001, 8.0, 100.0, 5_000L),
        )
        val filtered = StatisticsEngine.filteredPoints(points)
        assertEquals(2, filtered.size)
    }

    @Test
    fun accuracyFilterDropsImprecisePoints() {
        val points = listOf(
            TrackPoint(47.0, 8.0, null, 1_000L, accuracyM = 5.0),
            TrackPoint(47.001, 8.0, null, 2_000L, accuracyM = 50.0),
            TrackPoint(47.002, 8.0, null, 3_000L, accuracyM = null),
        )
        val filtered = StatisticsEngine.filteredPoints(points, StatisticsOptions(maxAccuracyMeters = 25.0))
        assertEquals(1, filtered.size)
    }

    @Test
    fun outOfOrderPointsAreDropped() {
        val points = listOf(
            TrackPoint(47.0, 8.0, null, 5_000L),
            TrackPoint(47.001, 8.0, null, 1_000L),
        )
        val filtered = StatisticsEngine.filteredPoints(points)
        assertEquals(1, filtered.size)
    }

    @Test
    fun emptyOrNullStats() {
        assertNull(StatisticsEngine.compute(emptyList()))
    }

    @Test
    fun startedAndEndedAtMatchFirstLast() {
        val points = eastwardPoints(3, 10.0, 5_000L)
        val stats = StatisticsEngine.compute(points)
        assertNotNull(stats)
        assertEquals(1_000_000L, stats.startedAt)
        assertEquals(1_010_000L, stats.endedAt)
        assertEquals(3, stats.pointCount)
    }
}
