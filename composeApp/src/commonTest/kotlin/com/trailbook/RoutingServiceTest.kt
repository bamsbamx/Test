package com.trailbook

import com.trailbook.core.geo.GeoPoint
import com.trailbook.core.model.RoutingProfile
import com.trailbook.core.routing.RoutingService
import com.trailbook.core.routing.RoutingUnavailableException
import com.trailbook.helpers.FakeTransport
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class RoutingServiceTest {

    @Test
    fun osrmResponseIsParsedIntoRoute() = runTest {
        val json = """
        {"code":"Ok","routes":[{"distance":24000.0,"duration":3000.0,"geometry":{"coordinates":[[8.0,47.0],[8.05,47.0],[8.1,47.0]]}}]}
        """.trimIndent()
        val service = RoutingService(FakeTransport.json(200, json))
        val result = service.route(listOf(GeoPoint(47.0, 8.0), GeoPoint(47.0, 8.1)), RoutingProfile.HIKING)
        assertEquals(24000.0, result.distanceMeters, 0.001)
        assertEquals(3, result.points.size)
        assertEquals(47.0, result.points.first().latitude, 1e-6)
    }

    @Test
    fun osrmErrorThrows() = runTest {
        val service = RoutingService(FakeTransport.json(200, """{"code":"NoRoute"}"""))
        try {
            service.route(listOf(GeoPoint(47.0, 8.0), GeoPoint(47.0, 8.1)), RoutingProfile.HIKING)
            fail("Expected RoutingUnavailableException")
        } catch (e: RoutingUnavailableException) {
        }
    }

    @Test
    fun offlineThrows() = runTest {
        val service = RoutingService(FakeTransport.offline())
        try {
            service.route(listOf(GeoPoint(47.0, 8.0), GeoPoint(47.0, 8.1)), RoutingProfile.HIKING)
            fail("Expected RoutingUnavailableException")
        } catch (e: RoutingUnavailableException) {
        }
    }

    @Test
    fun distanceOfSumsHaversine() {
        val d = com.trailbook.core.routing.RoutingService.distanceOf(
            listOf(
                com.trailbook.core.model.RoutePoint(47.0, 8.0),
                com.trailbook.core.model.RoutePoint(47.0, 8.0 + 0.013168),
            )
        )
        assertTrue(d > 900.0 && d < 1100.0)
    }

    @Test
    fun routeFromTrackConvertsPoints() {
        val points = listOf(
            com.trailbook.core.model.TrackPoint(47.0, 8.0, 100.0, 1L),
            com.trailbook.core.model.TrackPoint(47.01, 8.01, 200.0, 2L),
        )
        val routePoints = com.trailbook.core.routing.RoutingService.routeFromTrack(points, "My track")
        assertEquals(2, routePoints.size)
    }
}
