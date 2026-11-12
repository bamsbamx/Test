package com.trailbook

import com.trailbook.core.model.Marker
import com.trailbook.core.model.MarkerList
import com.trailbook.core.model.Track
import com.trailbook.core.model.TrackPoint
import com.trailbook.core.prefs.AppSettings
import com.trailbook.core.prefs.FilePreferencesRepository
import com.trailbook.core.prefs.UnitsSystem
import com.trailbook.data.FileMarkerListRepository
import com.trailbook.data.FileMarkerRepository
import com.trailbook.data.FileRouteRepository
import com.trailbook.data.FileTrackRepository
import com.trailbook.data.JsonFileStore
import com.trailbook.core.model.Route
import com.trailbook.core.model.RoutePoint
import com.trailbook.core.model.RoutingProfile
import com.trailbook.helpers.InMemoryFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersistenceTest {

    private fun store() = JsonFileStore(InMemoryFileSystem(), "data")

    private fun track(id: String, name: String, modifiedAt: Long = 100L) = Track(
        id = id,
        name = name,
        points = listOf(TrackPoint(47.0, 8.0, 100.0, 1L), TrackPoint(47.001, 8.001, 110.0, 2L)),
        createdAt = 100L,
        modifiedAt = modifiedAt,
        source = "recorded",
    )

    @Test
    fun trackSaveGetDelete() {
        val repo = FileTrackRepository(store())
        repo.save(track("t1", "First"))
        repo.save(track("t2", "Second"))
        assertEquals(2, repo.getAll().size)
        assertEquals("First", repo.getById("t1")?.name)
        repo.delete("t1")
        assertNull(repo.getById("t1"))
        assertEquals(1, repo.getAll().size)
    }

    @Test
    fun trackSaveUpdatesExisting() {
        val repo = FileTrackRepository(store())
        repo.save(track("t1", "First"))
        repo.save(track("t1", "Renamed", modifiedAt = 200L))
        assertEquals(1, repo.getAll().size)
        assertEquals("Renamed", repo.getById("t1")?.name)
        assertEquals(200L, repo.getById("t1")?.modifiedAt)
    }

    @Test
    fun trackPersistenceRoundtripSurvivesReload() {
        val fs = InMemoryFileSystem()
        val repo = FileTrackRepository(JsonFileStore(fs, "data"))
        repo.save(track("t1", "Hike"))
        val reloaded = FileTrackRepository(JsonFileStore(fs, "data"))
        val loaded = reloaded.getById("t1")
        assertNotNull(loaded)
        assertEquals("Hike", loaded.name)
        assertEquals(2, loaded.points.size)
        assertEquals(110.0, loaded.points[1].elevation!!, 0.001)
    }

    @Test
    fun bulkDelete() {
        val repo = FileTrackRepository(store())
        repo.save(track("t1", "a"))
        repo.save(track("t2", "b"))
        repo.save(track("t3", "c"))
        assertEquals(2, repo.deleteAll(listOf("t1", "t2", "missing")))
        assertEquals(1, repo.getAll().size)
    }

    @Test
    fun routeRepositoryRoundtrip() {
        val repo = FileRouteRepository(store())
        val route = Route(
            id = "r1", name = "Planned", profile = RoutingProfile.HIKING,
            points = listOf(RoutePoint(47.0, 8.0), RoutePoint(47.01, 8.01)),
            distanceMeters = 1500.0, createdAt = 5L,
        )
        repo.save(route)
        val loaded = repo.getById("r1")
        assertNotNull(loaded)
        assertEquals("Planned", loaded.name)
        assertEquals(RoutingProfile.HIKING, loaded.profile)
        assertEquals(2, loaded.points.size)
        repo.delete("r1")
        assertTrue(repo.getAll().isEmpty())
    }

    @Test
    fun markerRepositoryListFiltering() {
        val repo = FileMarkerRepository(store())
        repo.save(Marker("m1", "A", 47.0, 8.0, listId = "l1", createdAt = 1L))
        repo.save(Marker("m2", "B", 47.1, 8.1, listId = null, createdAt = 2L))
        assertEquals(2, repo.getAll().size)
        assertEquals(1, repo.getByList("l1").size)
        assertEquals(1, repo.getByList(null).size)
        repo.delete("m1")
        assertNull(repo.getById("m1"))
    }

    @Test
    fun markerListRepository() {
        val repo = FileMarkerListRepository(store())
        repo.save(MarkerList("l1", "Summits", createdAt = 1L))
        assertEquals("Summits", repo.getById("l1")?.name)
        assertFalse(repo.getAll().isEmpty())
    }

    @Test
    fun corruptedFileYieldsEmptyList() {
        val fs = InMemoryFileSystem()
        fs.writeText("data/tracks.json", "{not valid json")
        val repo = FileTrackRepository(JsonFileStore(fs, "data"))
        assertTrue(repo.getAll().isEmpty())
    }

    @Test
    fun preferencesRoundtrip() {
        val fs = InMemoryFileSystem()
        val repo = FilePreferencesRepository(JsonFileStore(fs, "prefs"))
        val settings = AppSettings(units = UnitsSystem.IMPERIAL, mapStyleId = "satellite", fullVersionUnlocked = true)
        repo.save(settings)
        val loaded = FilePreferencesRepository(JsonFileStore(fs, "prefs")).load()
        assertNotNull(loaded)
        assertEquals(UnitsSystem.IMPERIAL, loaded.units)
        assertEquals("satellite", loaded.mapStyleId)
        assertTrue(loaded.fullVersionUnlocked)
    }
}
