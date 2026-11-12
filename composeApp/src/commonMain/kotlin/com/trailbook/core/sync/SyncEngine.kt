package com.trailbook.core.sync

import com.trailbook.core.model.Marker
import com.trailbook.core.model.Track
import com.trailbook.core.net.HttpRequest
import com.trailbook.core.net.HttpTransport
import com.trailbook.data.MarkerRepository
import com.trailbook.data.TrackRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class SyncPhase { IDLE, UPLOADING, DOWNLOADING, MERGING, DONE, FAILED, OFFLINE }

@Serializable
data class SyncConflict(
    val entityId: String,
    val entityType: String,
    val localModifiedAt: Long,
    val remoteModifiedAt: Long,
    val resolution: String = "local",
)

@Serializable
data class SyncState(
    val phase: SyncPhase = SyncPhase.IDLE,
    val lastSyncAt: Long? = null,
    val uploadedTracks: Int = 0,
    val downloadedTracks: Int = 0,
    val uploadedMarkers: Int = 0,
    val downloadedMarkers: Int = 0,
    val conflicts: List<SyncConflict> = emptyList(),
    val message: String? = null,
)

sealed interface SyncOutcome {
    data class Success(val state: SyncState) : SyncOutcome
    data class Offline(val message: String) : SyncOutcome
    data class Failure(val message: String) : SyncOutcome
    data object NotSignedIn : SyncOutcome
}

class SyncEngine(
    private val transport: HttpTransport,
    private val account: AccountService,
    private val trackRepository: TrackRepository,
    private val markerRepository: MarkerRepository,
    private val now: () -> Long,
) {
    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun sync(): SyncOutcome {
        val session = account.currentSession ?: account.restoreSession()
        if (session == null) return SyncOutcome.NotSignedIn
        val base = session.serverUrl
        val headers = mapOf(
            "Authorization" to "Bearer ${session.token}",
            "Accept" to "application/json",
            "Content-Type" to "application/json",
        )
        val conflicts = mutableListOf<SyncConflict>()
        var uploaded = 0
        var downloaded = 0

        _state.value = SyncState(phase = SyncPhase.UPLOADING, conflicts = emptyList())
        val localTracks = trackRepository.getAll().filter { it.remoteId == null || it.modifiedAt > (it.syncedAt ?: 0) }
        for (track in localTracks) {
            val outcome = postTrack(base, headers, track)
            when (outcome) {
                is PostResult.Conflict -> {
                    conflicts.add(SyncConflict(track.id, "track", track.modifiedAt, outcome.remoteModifiedAt, "local"))
                }
                is PostResult.Ok -> {
                    uploaded++
                    trackRepository.save(track.copy(remoteId = outcome.remoteId, syncedAt = now()))
                }
                is PostResult.Error -> return fail("Upload failed: ${outcome.message}")
            }
        }

        _state.value = _state.value.copy(phase = SyncPhase.DOWNLOADING)
        val remote = getTracks(base, headers)
        when (remote) {
            is GetResult.Error -> return fail("Download failed: ${remote.message}")
            is GetResult.Ok -> {
                _state.value = _state.value.copy(phase = SyncPhase.MERGING)
                for (remoteTrack in remote.items) {
                    val local = trackRepository.getById(remoteTrack.id)
                        ?: trackRepository.getAll().firstOrNull { it.remoteId == remoteTrack.remoteId }
                    if (local == null) {
                        trackRepository.save(remoteTrack.copy(syncedAt = now()))
                        downloaded++
                    } else if (remoteTrack.modifiedAt > local.modifiedAt) {
                        trackRepository.save(remoteTrack.copy(syncedAt = now()))
                        downloaded++
                    } else {
                        conflicts.add(SyncConflict(local.id, "track", local.modifiedAt, remoteTrack.modifiedAt, "local"))
                    }
                }
            }
        }

        var uploadedMarkers = 0
        var downloadedMarkers = 0
        val localMarkers = markerRepository.getAll().filter { it.modifiedAt > (it.syncedAt ?: 0) }
        for (marker in localMarkers) {
            val outcome = postMarker(base, headers, marker)
            when (outcome) {
                is PostResult.Ok -> {
                    uploadedMarkers++
                    markerRepository.save(marker.copy(remoteId = outcome.remoteId, syncedAt = now()))
                }
                is PostResult.Conflict -> conflicts.add(
                    SyncConflict(marker.id, "marker", marker.modifiedAt, outcome.remoteModifiedAt, "local")
                )
                is PostResult.Error -> return fail("Marker upload failed: ${outcome.message}")
            }
        }
        val remoteMarkers = getMarkers(base, headers)
        if (remoteMarkers is GetResultOkOrError.Ok) {
            for (remoteMarker in remoteMarkers.items) {
                val local = markerRepository.getById(remoteMarker.id)
                if (local == null) {
                    markerRepository.save(remoteMarker.copy(syncedAt = now()))
                    downloadedMarkers++
                } else if (remoteMarker.modifiedAt > local.modifiedAt) {
                    markerRepository.save(remoteMarker.copy(syncedAt = now()))
                    downloadedMarkers++
                }
            }
        }

        val done = SyncState(
            phase = SyncPhase.DONE,
            lastSyncAt = now(),
            uploadedTracks = uploaded,
            downloadedTracks = downloaded,
            uploadedMarkers = uploadedMarkers,
            downloadedMarkers = downloadedMarkers,
            conflicts = conflicts,
            message = null,
        )
        _state.value = done
        return SyncOutcome.Success(done)
    }

    private fun fail(message: String): SyncOutcome.Failure {
        _state.value = _state.value.copy(phase = SyncPhase.FAILED, message = message)
        return SyncOutcome.Failure(message)
    }

    private sealed interface PostResult {
        data class Ok(val remoteId: String) : PostResult
        data class Conflict(val remoteModifiedAt: Long) : PostResult
        data class Error(val message: String) : PostResult
    }

    private sealed interface GetResult {
        data class Ok(val items: List<Track>) : GetResult
        data class Error(val message: String) : GetResult
    }

    private suspend fun postTrack(base: String, headers: Map<String, String>, track: Track): PostResult = try {
        val body = json.encodeToString(Track.serializer(), track)
        val response = transport.execute(HttpRequest("$base/tracks", "PUT", headers, body.encodeToByteArray(), "application/json"))
        when {
            response.status == 409 -> PostResult.Conflict(parseModifiedAt(response.text()))
            response.isSuccessful -> PostResult.Ok(track.remoteId ?: track.id)
            else -> PostResult.Error("HTTP ${response.status}")
        }
    } catch (e: com.trailbook.core.net.NetworkUnavailableException) {
        PostResult.Error("network unavailable")
    } catch (e: Exception) {
        PostResult.Error(e.message ?: "unknown error")
    }

    private suspend fun postMarker(base: String, headers: Map<String, String>, marker: Marker): PostResult = try {
        val body = json.encodeToString(Marker.serializer(), marker)
        val response = transport.execute(HttpRequest("$base/markers", "PUT", headers, body.encodeToByteArray(), "application/json"))
        when {
            response.status == 409 -> PostResult.Conflict(parseModifiedAt(response.text()))
            response.isSuccessful -> PostResult.Ok(marker.remoteId ?: marker.id)
            else -> PostResult.Error("HTTP ${response.status}")
        }
    } catch (e: Exception) {
        PostResult.Error(e.message ?: "unknown error")
    }

    private suspend fun getTracks(base: String, headers: Map<String, String>): GetResult = try {
        val response = transport.execute(HttpRequest("$base/tracks", headers = headers))
        if (!response.isSuccessful) {
            GetResult.Error("HTTP ${response.status}")
        } else {
            val items = json.decodeFromString(ListSerializer(Track.serializer()), response.text())
            GetResult.Ok(items)
        }
    } catch (e: Exception) {
        GetResult.Error(e.message ?: "unknown error")
    }

    private suspend fun getMarkers(base: String, headers: Map<String, String>): GetResultOkOrError = try {
        val response = transport.execute(HttpRequest("$base/markers", headers = headers))
        if (!response.isSuccessful) {
            GetResultOkOrError.Error("HTTP ${response.status}")
        } else {
            val items = json.decodeFromString(ListSerializer(Marker.serializer()), response.text())
            GetResultOkOrError.Ok(items)
        }
    } catch (e: Exception) {
        GetResultOkOrError.Error(e.message ?: "unknown error")
    }

    private sealed interface GetResultOkOrError {
        data class Ok(val items: List<Marker>) : GetResultOkOrError
        data class Error(val message: String) : GetResultOkOrError
    }

    private fun parseModifiedAt(text: String): Long = try {
        Json.parseToJsonElement(text).jsonObject["modifiedAt"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
    } catch (e: Exception) {
        0L
    }
}
