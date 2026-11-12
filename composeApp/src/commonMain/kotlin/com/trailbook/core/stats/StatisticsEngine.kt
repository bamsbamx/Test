package com.trailbook.core.stats

import com.trailbook.core.geo.GeoMath
import com.trailbook.core.geo.GeoPoint
import com.trailbook.core.model.TrackPoint
import com.trailbook.core.model.TrackStats
import kotlin.math.abs
import kotlin.math.max

data class StatisticsOptions(
    val maxAccuracyMeters: Double? = null,
    val movingSpeedThresholdMps: Double = 0.3,
    val elevationGainThresholdMeters: Double = 3.0,
    val elevationSmoothingWindow: Int = 5,
)

object StatisticsEngine {

    fun filteredPoints(points: List<TrackPoint>, options: StatisticsOptions = StatisticsOptions()): List<TrackPoint> {
        val result = ArrayList<TrackPoint>(points.size)
        var last: TrackPoint? = null
        for (p in points) {
            if (!p.latitude.isFinite() || !p.longitude.isFinite()) continue
            if (p.latitude == 0.0 && p.longitude == 0.0) continue
            val acc = options.maxAccuracyMeters
            if (acc != null && (p.accuracyM == null || p.accuracyM > acc)) continue
            val l = last
            if (l != null && p.time < l.time) continue
            if (l != null && p.latitude == l.latitude && p.longitude == l.longitude && p.elevation == l.elevation) continue
            result.add(p)
            last = p
        }
        return result
    }

    fun compute(points: List<TrackPoint>, options: StatisticsOptions = StatisticsOptions()): TrackStats? {
        val pts = filteredPoints(points, options)
        if (pts.isEmpty()) return null
        var distance = 0.0
        var movingTime = 0.0
        var maxSpeed = 0.0
        var gain = 0.0
        var loss = 0.0
        var minEle: Double? = null
        var maxEle: Double? = null
        val smoothed = smoothElevations(pts, options.elevationSmoothingWindow)
        var refEle = smoothed.firstOrNull { it != null }
        var accumulated = 0.0
        for (i in 1 until pts.size) {
            val a = pts[i - 1]
            val b = pts[i]
            val seg = GeoMath.haversineMeters(GeoPoint(a.latitude, a.longitude), GeoPoint(b.latitude, b.longitude))
            distance += seg
            val dt = (b.time - a.time) / 1000.0
            if (dt > 0) {
                val segSpeed = seg / dt
                maxSpeed = max(maxSpeed, segSpeed)
                if (segSpeed >= options.movingSpeedThresholdMps) movingTime += dt
            }
            val ea = smoothed.getOrNull(i - 1)
            val eb = smoothed.getOrNull(i)
            if (ea != null && eb != null) {
                val base = refEle ?: eb
                val delta = eb - base
                accumulated += delta
                if (abs(accumulated) >= options.elevationGainThresholdMeters) {
                    if (accumulated > 0) gain += accumulated else loss -= accumulated
                    accumulated = 0.0
                    refEle = eb
                } else if (abs(delta) > 0) {
                    refEle = base
                }
            }
        }
        for (e in smoothed) {
            if (e == null) continue
            minEle = if (minEle == null || e < minEle!!) e else minEle
            maxEle = if (maxEle == null || e > maxEle!!) e else maxEle
        }
        val totalTime = (pts.last().time - pts.first().time) / 1000.0
        val duration = max(totalTime, 0.0)
        return TrackStats(
            distanceMeters = distance,
            totalTimeSeconds = duration,
            movingTimeSeconds = movingTime,
            maxSpeedMps = maxSpeed,
            avgSpeedMps = if (duration > 0) distance / duration else 0.0,
            avgMovingSpeedMps = if (movingTime > 0) distance / movingTime else 0.0,
            elevationGain = gain,
            elevationLoss = loss,
            minElevation = minEle,
            maxElevation = maxEle,
            startedAt = pts.first().time,
            endedAt = pts.last().time,
            pointCount = pts.size,
        )
    }

    fun smoothElevations(points: List<TrackPoint>, window: Int): List<Double?> {
        if (window <= 1) return points.map { it.elevation }
        val w = window.coerceAtLeast(1)
        return points.indices.map { i ->
            val from = max(0, i - w / 2)
            val to = minOf(points.size - 1, i + w / 2)
            val values = (from..to).mapNotNull { points[it].elevation }
            if (values.isEmpty()) null else values.sorted()[values.size / 2]
        }
    }

    fun elevationProfile(points: List<TrackPoint>, samples: Int = 100): List<ElevationSample> {
        val pts = filteredPoints(points)
        if (pts.size < 2) return pts.map { ElevationSample(0.0, it.elevation) }
        var total = 0.0
        val cumulative = DoubleArray(pts.size)
        for (i in 1 until pts.size) {
            total += GeoMath.haversineMeters(pts[i - 1].toGeoPoint(), pts[i].toGeoPoint())
            cumulative[i] = total
        }
        val step = total / samples
        return (0..samples).map { s ->
            val target = (s * step).coerceAtMost(total)
            val idx = cumulative.binarySearch(target).let { if (it >= 0) it else -(it + 1) }.coerceIn(0, pts.size - 1)
            ElevationSample(target, pts[idx].elevation)
        }
    }

    fun speedProfile(points: List<TrackPoint>, samples: Int = 100, options: StatisticsOptions = StatisticsOptions()): List<SpeedSample> {
        val pts = filteredPoints(points, options)
        if (pts.size < 2) return emptyList()
        val out = ArrayList<SpeedSample>(samples + 1)
        var idx = 0
        val totalTime = (pts.last().time - pts.first().time) / 1000.0
        if (totalTime <= 0) return emptyList()
        for (s in 0..samples) {
            val t = pts.first().time + (totalTime * s / samples * 1000).toLong()
            while (idx < pts.size - 1 && pts[idx + 1].time <= t) idx++
            out.add(SpeedSample(t, pts[idx].speedMps))
        }
        return out
    }

    fun simplify(points: List<TrackPoint>, toleranceMeters: Double): List<TrackPoint> {
        if (points.size <= 2) return points
        val pts = filteredPoints(points)
        return douglasPeucker(pts, toleranceMeters)
    }

    private fun douglasPeucker(points: List<TrackPoint>, tolerance: Double): List<TrackPoint> {
        if (points.size <= 2) return points
        var maxDist = 0.0
        var index = 0
        val first = points.first()
        val last = points.last()
        for (i in 1 until points.size - 1) {
            val d = abs(GeoMath.pointToSegmentMeters(points[i].toGeoPoint(), first.toGeoPoint(), last.toGeoPoint()))
            if (d > maxDist) { maxDist = d; index = i }
        }
        return if (maxDist > tolerance) {
            val left = douglasPeucker(points.subList(0, index + 1), tolerance)
            val right = douglasPeucker(points.subList(index, points.size), tolerance)
            left + right.drop(1)
        } else {
            listOf(first, last)
        }
    }
}

data class ElevationSample(val distanceMeters: Double, val elevation: Double?)
data class SpeedSample(val time: Long, val speedMps: Double?)
