package com.trailbook

import com.trailbook.core.model.Marker
import com.trailbook.core.model.Track
import com.trailbook.core.model.TrackPoint
import com.trailbook.core.net.HttpRequest
import com.trailbook.core.net.HttpResponse
import com.trailbook.core.net.NetworkUnavailableException
import com.trailbook.core.sync.AccountService
import com.trailbook.core.sync.SyncEngine
import com.trailbook.core.sync.SyncOutcome
import com.trailbook.core.sync.SyncPhase
import com.trailbook.data.FileMarkerRepository
import com.trailbook.data.FileTrackRepository
import com.trailbook.data.JsonFileStore
import com.trailbook.helpers.FakeTransport
import com.trailbook.helpers.InMemoryFileSystem
import com.trailbook.helpers.InMemorySecureStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncEngineTest {

    private fun account(transport: com.trailbook.core.net.HttpTransport) =
        AccountService(transport, InMemorySecureStore()) { 1000L }

    private fun repos(fs: InMemoryFileSystem = InMemoryFileSystem()) =
        Pair(FileTrackRepository(JsonFileStore(fs, "data")), FileMarkerRepository(JsonFileStore(fs, "data")))

    private fun track(id: String, name: String, modifiedAt: Long = 100L, remoteId: String? = null) = Track(
        id = id, name = name,
        points = listOf(TrackPoint(47.0, 8.0, 100.0, 1L)),
        createdAt = 100L, modifiedAt = modifiedAt, remoteId = remoteId,
    )

    private suspend fun signedIn(transport: com.trailbook.core.net.HttpTransport): AccountService {
        val acc = account(transport)
        val result = acc.signIn("https://sync.example.com", "user@example.com", "secret")
        assertTrue(result is com.trailbook.core.sync.AuthResult.Success, "Sign-in failed: $result")
        return acc
    }

    @Test
    fun notSignedInReturnsNotSignedIn() = runTest {
        val (tracks, markers) = repos()
        val engine = SyncEngine(FakeTransport.offline(), account(FakeTransport.offline()), tracks, markers) { 1L }
        assertEquals(SyncOutcome.NotSignedIn, engine.sync())
    }

    @Test
    fun offlineTransportReturnsOfflineOutcome() = runTest {
        val (tracks, markers) = repos()
        val loginTransport = FakeTransport {
            HttpResponse(200, "{}".encodeToByteArray(), mapOf("Authorization" to "Bearer tok"))
        }
        val acc = signedIn(loginTransport)
        val engine = SyncEngine(FakeTransport.offline(), acc, tracks, markers) { 1L }
        val outcome = engine.sync()
        assertTrue(outcome is SyncOutcome.Offline || outcome is SyncOutcome.Failure)
    }

    @Test
    fun successfulUploadAndDownload() = runTest {
        val (tracks, markers) = repos()
        tracks.save(track("t1", "Local", remoteId = null))
        val serverTracks = mutableListOf<Track>()
        val transport = FakeTransport { request ->
            when {
                request.url.endsWith("/auth/login") -> HttpResponse(
                    200,
                    """{"id":"u1","email":"user@example.com"}""".encodeToByteArray(),
                    mapOf("Authorization" to "Bearer token-123"),
                )
                request.method == "PUT" && request.url.endsWith("/tracks") -> {
                    val body = kotlinx.serialization.json.Json.decodeFromString(
                        Track.serializer(),
                        request.body!!.decodeToString(),
                    )
                    serverTracks.add(body)
                    HttpResponse(200, "{}".encodeToByteArray())
                }
                request.method == "GET" && request.url.endsWith("/tracks") -> HttpResponse(
                    200,
                    """[{"id":"t9","name":"Remote track","points":[],"createdAt":1,"modifiedAt":50,"remoteId":"r-9"}]""".encodeToByteArray(),
                )
                request.url.endsWith("/markers") -> HttpResponse(200, "[]".encodeToByteArray())
                else -> HttpResponse(200, "[]".encodeToByteArray())
            }
        }
        val acc = signedIn(transport)
        val engine = SyncEngine(transport, acc, tracks, markers) { 2000L }
        val outcome = engine.sync()
        assertTrue(outcome is SyncOutcome.Success, "Expected success, got $outcome")
        val state = outcome.state
        assertEquals(1, state.uploadedTracks)
        assertEquals(1, state.downloadedTracks)
        assertEquals(SyncPhase.DONE, state.phase)
        assertEquals("t1", tracks.getById("t1")?.remoteId)
        assertEquals("Remote track", tracks.getById("t9")?.name)
        assertEquals(1, serverTracks.size)
        assertEquals("token-123", acc.storedToken())
    }

    @Test
    fun conflictWhenRemoteNewer() = runTest {
        val (tracks, markers) = repos()
        tracks.save(track("t1", "Local older", modifiedAt = 100L, remoteId = "r-1"))
        val transport = FakeTransport { request ->
            when {
                request.url.endsWith("/auth/login") -> HttpResponse(
                    200, """{"id":"u1"}""".encodeToByteArray(), mapOf("Authorization" to "Bearer tok"),
                )
                request.method == "PUT" && request.url.endsWith("/tracks") -> HttpResponse(409, """{"modifiedAt":900}""".encodeToByteArray())
                request.url.endsWith("/tracks") -> HttpResponse(200, "[]".encodeToByteArray())
                else -> HttpResponse(200, "[]".encodeToByteArray())
            }
        }
        val engine = SyncEngine(transport, signedIn(transport), tracks, markers) { 1L }
        val outcome = engine.sync()
        assertTrue(outcome is SyncOutcome.Success)
        assertEquals(1, outcome.state.conflicts.size)
        assertEquals("t1", outcome.state.conflicts.first().entityId)
        assertEquals(900L, outcome.state.conflicts.first().remoteModifiedAt)
    }

    @Test
    fun serverErrorReturnsFailure() = runTest {
        val (tracks, markers) = repos()
        tracks.save(track("t1", "Local"))
        val transport = FakeTransport { request ->
            when {
                request.url.endsWith("/auth/login") -> HttpResponse(
                    200, "{}".encodeToByteArray(), mapOf("Authorization" to "Bearer tok"),
                )
                else -> HttpResponse(500, ByteArray(0))
            }
        }
        val engine = SyncEngine(transport, signedIn(transport), tracks, markers) { 1L }
        val outcome = engine.sync()
        assertTrue(outcome is SyncOutcome.Failure)
    }

    @Test
    fun invalidCredentialsRejected() = runTest {
        val transport = FakeTransport.json(401, "{}")
        val result = account(transport).signIn("https://sync.example.com", "user@example.com", "wrong")
        assertTrue(result is com.trailbook.core.sync.AuthResult.InvalidCredentials)
    }

    @Test
    fun blankFieldsRejectedBeforeNetwork() = runTest {
        val transport = FakeTransport.offline()
        val result = account(transport).signIn("https://sync.example.com", "", "")
        assertTrue(result is com.trailbook.core.sync.AuthResult.InvalidCredentials)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun signOutClearsSession() = runTest {
        val transport = FakeTransport {
            HttpResponse(200, """{"id":"u1"}""".encodeToByteArray(), mapOf("Authorization" to "Bearer tok"))
        }
        val acc = signedIn(transport)
        assertTrue(acc.isSignedIn)
        acc.signOut()
        assertTrue(!acc.isSignedIn)
        assertEquals(null, acc.storedToken())
    }
}
