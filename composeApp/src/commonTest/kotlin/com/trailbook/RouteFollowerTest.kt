package com.trailbook

import com.trailbook.core.model.RoutePoint
import com.trailbook.core.model.TrackPoint
import com.trailbook.core.routing.GuidanceEvent
import com.trailbook.core.routing.RouteFollower
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RouteFollowerTest {

    private val metersPerDeg = 111320.0 * kotlin.math.cos(kotlin.math.PI * 47.0 / 180.0)

    private fun route(): List<RoutePoint> = listOf(
        RoutePoint(47.0, 8.0),
        RoutePoint(47.0, 8.0 + 1000.0 / metersPerDeg),
        RoutePoint(47.0, 8.0 + 2000.0 / metersPerDeg),
    )

    private fun at(lonOffsetMeters: Double, latOffsetMeters: Double = 0.0, time: Long = 1000L) =
        TrackPoint(47.0 + latOffsetMeters / metersPerDeg, 8.0 + lonOffsetMeters / metersPerDeg, null, time)

    @Test
    fun totalDistanceMatchesRouteLength() {
        val follower = RouteFollower(route())
        assertEquals(2000.0, follower.totalDistanceMeters, 5.0)
    }

    @Test
    fun startIsNotCompletedAndRemainingEqualsTotal() {
        val follower = RouteFollower(route())
        val state = follower.start()
        assertTrue(state.active)
        assertFalse(state.completed)
        assertEquals(2000.0, state.remainingMeters, 5.0)
    }

    @Test
    fun progressAdvancesAlongRoute() {
        val follower = RouteFollower(route())
        follower.start()
        val (state, events) = follower.onLocation(at(500.0))
        assertEquals(500.0, state.progressMeters, 10.0)
        assertEquals(1500.0, state.remainingMeters, 10.0)
        assertFalse(state.offRoute)
        assertTrue(events.isEmpty())
    }

    @Test
    fun offRouteAndBackOnRouteEvents() {
        val follower = RouteFollower(route())
        follower.start()
        var (state, events) = follower.onLocation(at(500.0, latOffsetMeters = 200.0))
        assertTrue(state.offRoute)
        assertTrue(events.contains(GuidanceEvent.OFF_ROUTE))
        val (state2, events2) = follower.onLocation(at(500.0))
        assertFalse(state2.offRoute)
        assertTrue(events2.contains(GuidanceEvent.BACK_ON_ROUTE))
    }

    @Test
    fun completionEventAtRouteEnd() {
        val follower = RouteFollower(route())
        follower.start()
        val (state, events) = follower.onLocation(at(1995.0))
        assertTrue(state.completed)
        assertTrue(events.contains(GuidanceEvent.ROUTE_COMPLETED))
    }

    @Test
    fun guidanceBearingAndNextPoint() {
        val follower = RouteFollower(route())
        follower.start()
        val (state, _) = follower.onLocation(at(100.0))
        assertEquals(90.0, state.bearingToNextDeg!!, 2.0)
        assertEquals(900.0, state.nextPointDistanceMeters!!, 10.0)
    }

    @Test
    fun afterCompletionFollowStateIsInactive() {
        val follower = RouteFollower(route())
        follower.start()
        follower.onLocation(at(1995.0))
        val (state, _) = follower.onLocation(at(2000.0))
        assertFalse(state.active)
        assertTrue(state.completed)
    }

    @Test
    fun routeWithOnePointIsRejected() {
        val error = runCatching { RouteFollower(listOf(RoutePoint(47.0, 8.0))) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }
}
