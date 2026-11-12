package com.trailbook

import com.trailbook.core.format.FormatRegistry
import com.trailbook.core.format.GpxCodec
import com.trailbook.core.format.KmlCodec
import com.trailbook.core.geo.GeoPoint
import com.trailbook.core.model.ActivityType
import com.trailbook.core.model.Marker
import com.trailbook.core.model.MarkerList
import com.trailbook.core.model.Route
import com.trailbook.core.model.RoutePoint
import com.trailbook.core.model.RoutingProfile
import com.trailbook.core.model.TrackPoint
import com.trailbook.core.recording.RecordingController
import com.trailbook.core.recording.LocationSource
import com.trailbook.core.recording.RecordingState
import com.trailbook.core.prefs.RecordingSettings
import com.trailbook.core.routing.RoutingService
import com.trailbook.core.routing.RoutingUnavailableException
import com.trailbook.core.routing.RouteFollower
import com.trailbook.core.stats.StatisticsEngine
import com.trailbook.data.FileMarkerListRepository
import com.trailbook.data.FileMarkerRepository
import com.trailbook.data.FileRouteRepository
import com.trailbook.data.FileTrackRepository
import com.trailbook.data.JsonFileStore
import com.trailbook.helpers.FakeTransport
import com.trailbook.helpers.InMemoryFileSystem
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EndToEndWorkflowTest {

    private val metersPerDeg = 111320.0 * kotlin.math.cos(kotlin.math.PI * 47.0 / 180.0)

    private class NoLocation : LocationSource {
        override suspend fun current(): TrackPoint? = null
        override fun updates() = flowOf<TrackPoint>()
    }

    @Test
    fun recordSaveInspectExportImportWorkflow() {
        val fs = InMemoryFileSystem()
        val trackRepo = FileTrackRepository(JsonFileStore(fs, "data"))

        var clock = 1_000_000L
        val recorder = RecordingController(NoLocation(), { clock }) { "gen-1" }
        recorder.start(ActivityType.HIKING, RecordingSettings(minDistanceMeters = 1.0, minIntervalSeconds = 0))
        clock = 1_060_000L
        recorder.onLocationUpdate(TrackPoint(47.0, 8.0, 500.0, clock, accuracyM = 5.0), RecordingSettings(minDistanceMeters = 1.0, minIntervalSeconds = 0))
        clock = 1_120_000L
        recorder.onLocationUpdate(TrackPoint(47.001, 8.001, 540.0, clock, accuracyM = 5.0), RecordingSettings(minDistanceMeters = 1.0, minIntervalSeconds = 0))
        clock = 1_180_000L
        recorder.onLocationUpdate(TrackPoint(47.002, 8.002, 510.0, clock, accuracyM = 5.0), RecordingSettings(minDistanceMeters = 1.0, minIntervalSeconds = 0))
        val finished = recorder.finish()
        assertNotNull(finished)
        assertEquals(RecordingState.IDLE, finished.state)

        val stats = StatisticsEngine.compute(
            finished.points,
            com.trailbook.core.stats.StatisticsOptions(elevationSmoothingWindow = 1, elevationGainThresholdMeters = 1.0),
        )
        assertNotNull(stats)
        val track = com.trailbook.core.model.Track(
            id = "t-e2e",
            name = "Recorded hike",
            activityType = ActivityType.byId(finished.activityTypeId),
            points = finished.points,
            createdAt = finished.startedAt,
            modifiedAt = clock,
            source = "recorded",
            stats = stats,
        )
        trackRepo.save(track)

        val reloaded = FileTrackRepository(JsonFileStore(fs, "data")).getById("t-e2e")
        assertNotNull(reloaded)
        assertEquals("Recorded hike", reloaded.name)
        assertEquals(3, reloaded.points.size)
        assertNotNull(reloaded.stats)
        assertTrue(reloaded.stats!!.distanceMeters > 100.0)
        assertTrue(reloaded.stats!!.elevationGain > 20.0)
        assertTrue(reloaded.stats!!.elevationLoss > 20.0)
        assertEquals(3, reloaded.stats!!.pointCount)
        val elevationProfile = reloaded.points.mapNotNull { it.elevation }
        assertEquals(listOf(500.0, 540.0, 510.0), elevationProfile)

        val registry = FormatRegistry({ "gen-2" }, { clock })
        val gpx = registry.export(reloaded, com.trailbook.core.format.SupportedFormat.GPX)
        assertTrue(String(gpx).contains("<gpx"))
        val imported = registry.parse("reimported.gpx", gpx)
        assertEquals(1, imported.tracks.size)
        val importedTrack = imported.tracks.first().copy(id = "t-imported", createdAt = clock)
        trackRepo.save(importedTrack)
        assertNotNull(trackRepo.getById("t-imported"))
        assertEquals(3, trackRepo.getById("t-imported")!!.points.size)
    }

    @Test
    fun planFollowRouteWorkflowOffline() = runTest {
        val fs = InMemoryFileSystem()
        val routeRepo = FileRouteRepository(JsonFileStore(fs, "data"))

        val waypoints = listOf(GeoPoint(47.0, 8.0), GeoPoint(47.0, 8.0 + 1500.0 / metersPerDeg))
        val online = RoutingService(FakeTransport.json(200, """{"code":"Ok","routes":[{"distance":1500.0,"duration":1800.0,"geometry":{"coordinates":[[8.0,47.0],[8.013168,47.0]]}}]}"""))
        val onlineResult = online.route(waypoints, RoutingProfile.HIKING)
        assertEquals(1500.0, onlineResult.distanceMeters, 1.0)

        val offline = RoutingService(FakeTransport.offline())
        val fallbackPoints = try {
            offline.route(waypoints, RoutingProfile.HIKING).points
        } catch (e: RoutingUnavailableException) {
            listOf(
                RoutePoint(47.0, 8.0, name = "Start"),
                RoutePoint(47.0, 8.0 + 1500.0 / metersPerDeg, name = "Destination"),
            )
        }
        val route = Route(
            id = "r-e2e", name = "Planned offline", profile = RoutingProfile.HIKING,
            points = fallbackPoints,
            distanceMeters = RoutingService.distanceOf(fallbackPoints),
            createdAt = 1L,
        )
        routeRepo.save(route)
        assertEquals(2, routeRepo.getById("r-e2e")!!.points.size)

        val follower = RouteFollower(routeRepo.getById("r-e2e")!!.points)
        follower.start()
        val (mid, midEvents) = follower.onLocation(TrackPoint(47.0, 8.0 + 750.0 / metersPerDeg, null, 1L))
        assertEquals(750.0, mid.progressMeters, 15.0)
        assertTrue(midEvents.isEmpty())
        val (end, endEvents) = follower.onLocation(TrackPoint(47.0, 8.0 + 1490.0 / metersPerDeg, null, 2L))
        assertTrue(end.completed)
        assertTrue(endEvents.contains(com.trailbook.core.routing.GuidanceEvent.ROUTE_COMPLETED))
    }

    @Test
    fun markerListWorkflowWithKmlShare() {
        val fs = InMemoryFileSystem()
        val markerRepo = FileMarkerRepository(JsonFileStore(fs, "data"))
        val listRepo = FileMarkerListRepository(JsonFileStore(fs, "data"))

        listRepo.save(MarkerList(id = "l1", name = "Summits", createdAt = 1L))
        val marker = Marker(
            id = "m1", name = "Uetliberg", latitude = 47.3494, longitude = 8.4925,
            elevation = 869.0, listId = "l1", createdAt = 1L,
        )
        markerRepo.save(marker)
        markerRepo.save(marker.copy(name = "Uetliberg summit", modifiedAt = 2L))
        assertEquals("Uetliberg summit", markerRepo.getById("m1")!!.name)
        assertEquals(1, markerRepo.getByList("l1").size)

        val kml = KmlCodec.writeMarkers(markerRepo.getAll())
        assertTrue(kml.contains("Uetliberg summit"))
        val parsed = KmlCodec.parse(kml, { "gen" }, { 1L })
        assertEquals(1, parsed.waypoints.size)
        assertEquals(47.3494, parsed.waypoints[0].latitude, 1e-6)

        markerRepo.delete("m1")
        assertTrue(markerRepo.getByList("l1").isEmpty())
    }

    @Test
    fun weatherOfflineReportsErrorRatherThanFakeData() = runTest {
        val service = com.trailbook.core.weather.WeatherService(FakeTransport.offline()) { 1L }
        val error = runCatching {
            service.forecast(com.trailbook.core.model.WeatherLocation(47.3769, 8.5417))
        }.exceptionOrNull()
        assertTrue(error is com.trailbook.core.net.NetworkUnavailableException)
    }
}
