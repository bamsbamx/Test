package com.trailbook.core.routing

import com.trailbook.core.geo.GeoMath
import com.trailbook.core.geo.GeoPoint
import com.trailbook.core.model.RoutePoint
import com.trailbook.core.model.TrackPoint
import kotlin.math.abs

enum class GuidanceEvent { OFF_ROUTE, BACK_ON_ROUTE, WAYPOINT_REACHED, ROUTE_COMPLETED, PROXIMITY_ALERT }

data class FollowState(
    val active: Boolean = false,
    val segmentIndex: Int = 0,
    val progressMeters: Double = 0.0,
    val remainingMeters: Double = 0.0,
    val offRoute: Boolean = false,
    val completed: Boolean = false,
    val nextPoint: RoutePoint? = null,
    val nextPointDistanceMeters: Double? = null,
    val bearingToNextDeg: Double? = null,
    val deviationMeters: Double = 0.0,
)

class RouteFollower(
    private val points: List<RoutePoint>,
    private val offRouteThresholdMeters: Double = 40.0,
    private val completionRadiusMeters: Double = 25.0,
) {
    init {
        require(points.size >= 2) { "Route needs at least 2 points" }
    }

    private val cumulative = run {
        val c = DoubleArray(points.size)
        for (i in 1 until points.size) {
            c[i] = c[i - 1] + GeoMath.haversineMeters(points[i - 1].toGeoPoint(), points[i].toGeoPoint())
        }
        c
    }

    val totalDistanceMeters: Double get() = cumulative.lastOrNull() ?: 0.0

    private var segmentIndex = 0
    private var wasOffRoute = false
    private var completed = false
    private var lastProgress = 0.0

    fun start(): FollowState = FollowState(
        active = true,
        segmentIndex = 0,
        remainingMeters = totalDistanceMeters,
        nextPoint = points.getOrNull(1),
        nextPointDistanceMeters = points.getOrNull(1)?.let { GeoMath.haversineMeters(points[0].toGeoPoint(), it.toGeoPoint()) },
    )

    fun onLocation(p: TrackPoint): Pair<FollowState, List<GuidanceEvent>> {
        val events = mutableListOf<GuidanceEvent>()
        if (completed) return FollowState(active = false, completed = true) to events
        val loc = GeoPoint(p.latitude, p.longitude)
        var bestIdx = segmentIndex
        var bestDist = Double.MAX_VALUE
        for (i in maxOf(0, segmentIndex - 5)..minOf(points.size - 2, segmentIndex + 20)) {
            val d = abs(GeoMath.pointToSegmentMeters(loc, points[i].toGeoPoint(), points[i + 1].toGeoPoint()))
            if (d < bestDist) { bestDist = d; bestIdx = i }
        }
        segmentIndex = bestIdx
        val progress = cumulative[bestIdx] + projectionDistance(p, bestIdx)
        val remaining = (totalDistanceMeters - progress).coerceAtLeast(0.0)
        val offRoute = bestDist > offRouteThresholdMeters
        if (offRoute && !wasOffRoute) events.add(GuidanceEvent.OFF_ROUTE)
        if (!offRoute && wasOffRoute) events.add(GuidanceEvent.BACK_ON_ROUTE)
        wasOffRoute = offRoute

        val nextIdx = (bestIdx + 1).coerceAtMost(points.size - 1)
        val next = points[nextIdx]
        val nextDist = GeoMath.haversineMeters(loc, next.toGeoPoint())
        if (nextDist < completionRadiusMeters && nextIdx > bestIdx) {
            events.add(GuidanceEvent.WAYPOINT_REACHED)
        }
        if (remaining < completionRadiusMeters || nextDist < completionRadiusMeters && nextIdx == points.size - 1) {
            completed = true
            events.add(GuidanceEvent.ROUTE_COMPLETED)
        }
        if (progress < lastProgress - 200.0) {
            events.add(GuidanceEvent.PROXIMITY_ALERT)
        }
        lastProgress = progress

        val state = FollowState(
            active = !completed,
            segmentIndex = bestIdx,
            progressMeters = progress,
            remainingMeters = remaining,
            offRoute = offRoute,
            completed = completed,
            nextPoint = next,
            nextPointDistanceMeters = nextDist,
            bearingToNextDeg = GeoMath.initialBearingDeg(loc, next.toGeoPoint()),
            deviationMeters = bestDist,
        )
        return state to events
    }

    fun stop() {
        completed = false
        wasOffRoute = false
        segmentIndex = 0
        lastProgress = 0.0
    }

    private fun projectionDistance(p: TrackPoint, segIdx: Int): Double {
        val a = points[segIdx].toGeoPoint()
        val b = points[segIdx + 1].toGeoPoint()
        val loc = GeoPoint(p.latitude, p.longitude)
        val cross = abs(GeoMath.pointToSegmentMeters(loc, a, b))
        val ap = GeoMath.haversineMeters(a, loc)
        val alongSq = ap * ap - cross * cross
        val along = if (alongSq > 0) kotlin.math.sqrt(alongSq) else 0.0
        val segLen = GeoMath.haversineMeters(a, b)
        return along.coerceIn(0.0, segLen)
    }
}
