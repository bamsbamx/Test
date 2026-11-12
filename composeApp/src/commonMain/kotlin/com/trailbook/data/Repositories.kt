package com.trailbook.data

import com.trailbook.core.model.*
import com.trailbook.platform.AppFileSystem
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

interface TrackRepository {
    fun getAll(): List<Track>
    fun getById(id: String): Track?
    fun save(track: Track): Track
    fun delete(id: String): Boolean
    fun deleteAll(ids: Collection<String>): Int
}

interface RouteRepository {
    fun getAll(): List<Route>
    fun getById(id: String): Route?
    fun save(route: Route): Route
    fun delete(id: String): Boolean
}

interface MarkerRepository {
    fun getAll(): List<Marker>
    fun getById(id: String): Marker?
    fun getByList(listId: String?): List<Marker>
    fun save(marker: Marker): Marker
    fun delete(id: String): Boolean
    fun deleteAll(ids: Collection<String>): Int
}

interface MarkerListRepository {
    fun getAll(): List<MarkerList>
    fun getById(id: String): MarkerList?
    fun save(list: MarkerList): MarkerList
    fun delete(id: String): Boolean
}

class FileTrackRepository(private val store: JsonFileStore) : TrackRepository {
    private val serializer = ListSerializer(Track.serializer())

    @Synchronized
    override fun getAll(): List<Track> =
        store.readList(FILE, serializer).sortedByDescending { it.createdAt }

    @Synchronized
    override fun getById(id: String): Track? = getAll().firstOrNull { it.id == id }

    @Synchronized
    override fun save(track: Track): Track {
        val all = getAll().toMutableList()
        all.removeAll { it.id == track.id }
        all.add(track)
        store.writeList(FILE, serializer, all)
        return track
    }

    @Synchronized
    override fun delete(id: String): Boolean {
        val all = getAll()
        val updated = all.filter { it.id != id }
        if (updated.size == all.size) return false
        store.writeList(FILE, serializer, updated)
        return true
    }

    @Synchronized
    override fun deleteAll(ids: Collection<String>): Int {
        val all = getAll()
        val updated = all.filter { it.id !in ids }
        store.writeList(FILE, serializer, updated)
        return all.size - updated.size
    }

    companion object { const val FILE = "tracks.json" }
}

class FileRouteRepository(private val store: JsonFileStore) : RouteRepository {
    private val serializer = ListSerializer(Route.serializer())

    @Synchronized
    override fun getAll(): List<Route> = store.readList(FILE, serializer).sortedByDescending { it.createdAt }

    @Synchronized
    override fun getById(id: String): Route? = getAll().firstOrNull { it.id == id }

    @Synchronized
    override fun save(route: Route): Route {
        val all = getAll().toMutableList()
        all.removeAll { it.id == route.id }
        all.add(route)
        store.writeList(FILE, serializer, all)
        return route
    }

    @Synchronized
    override fun delete(id: String): Boolean {
        val all = getAll()
        val updated = all.filter { it.id != id }
        if (updated.size == all.size) return false
        store.writeList(FILE, serializer, updated)
        return true
    }

    companion object { const val FILE = "routes.json" }
}

class FileMarkerRepository(private val store: JsonFileStore) : MarkerRepository {
    private val serializer = ListSerializer(Marker.serializer())

    @Synchronized
    override fun getAll(): List<Marker> = store.readList(FILE, serializer).sortedByDescending { it.createdAt }

    @Synchronized
    override fun getById(id: String): Marker? = getAll().firstOrNull { it.id == id }

    @Synchronized
    override fun getByList(listId: String?): List<Marker> =
        getAll().filter { if (listId == null) it.listId == null else it.listId == listId }

    @Synchronized
    override fun save(marker: Marker): Marker {
        val all = getAll().toMutableList()
        all.removeAll { it.id == marker.id }
        all.add(marker)
        store.writeList(FILE, serializer, all)
        return marker
    }

    @Synchronized
    override fun delete(id: String): Boolean {
        val all = getAll()
        val updated = all.filter { it.id != id }
        if (updated.size == all.size) return false
        store.writeList(FILE, serializer, updated)
        return true
    }

    @Synchronized
    override fun deleteAll(ids: Collection<String>): Int {
        val all = getAll()
        val updated = all.filter { it.id !in ids }
        store.writeList(FILE, serializer, updated)
        return all.size - updated.size
    }

    companion object { const val FILE = "markers.json" }
}

class FileMarkerListRepository(private val store: JsonFileStore) : MarkerListRepository {
    private val serializer = ListSerializer(MarkerList.serializer())

    @Synchronized
    override fun getAll(): List<MarkerList> = store.readList(FILE, serializer).sortedBy { it.name }

    @Synchronized
    override fun getById(id: String): MarkerList? = getAll().firstOrNull { it.id == id }

    @Synchronized
    override fun save(list: MarkerList): MarkerList {
        val all = getAll().toMutableList()
        all.removeAll { it.id == list.id }
        all.add(list)
        store.writeList(FILE, serializer, all)
        return list
    }

    @Synchronized
    override fun delete(id: String): Boolean {
        val all = getAll()
        val updated = all.filter { it.id != id }
        if (updated.size == all.size) return false
        store.writeList(FILE, serializer, updated)
        return true
    }

    companion object { const val FILE = "marker_lists.json" }
}
