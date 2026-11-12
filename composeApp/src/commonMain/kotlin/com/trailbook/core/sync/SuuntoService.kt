package com.trailbook.core.sync

import com.trailbook.core.format.FitCodec
import com.trailbook.core.format.FormatParseException
import com.trailbook.core.model.Track
import com.trailbook.core.net.HttpRequest
import com.trailbook.core.net.HttpTransport
import com.trailbook.platform.SecureStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class SuuntoCredentials(val username: String, val password: String)

sealed interface SuuntoResult {
    data class Connected(val username: String) : SuuntoResult
    data object NotConfigured : SuuntoResult
    data class Failed(val message: String) : SuuntoResult
}

data class SuuntoWorkout(
    val workoutKey: String,
    val name: String,
    val startTime: Long?,
    val distanceMeters: Double?,
    val durationSeconds: Double?,
)

class SuuntoService(
    private val transport: HttpTransport,
    private val secureStore: SecureStore,
    private val now: () -> Long,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun hasCredentials(): Boolean = secureStore.get(KEY_USERNAME) != null && secureStore.get(KEY_TOKEN) != null

    fun storedUsername(): String? = secureStore.get(KEY_USERNAME)

    suspend fun connect(username: String, password: String): SuuntoResult {
        if (username.isBlank() || password.isBlank()) return SuuntoResult.NotConfigured
        return try {
            val body = "username=" + formEncode(username) + "&password=" + formEncode(password) +
                "&grant_type=password&client_id=" + formEncode(CLIENT_ID) + "&client_secret=" + formEncode(CLIENT_SECRET)
            val response = transport.execute(
                HttpRequest(
                    url = TOKEN_URL,
                    method = "POST",
                    headers = mapOf("Accept" to "application/json"),
                    body = body.encodeToByteArray(),
                    contentType = "application/x-www-form-urlencoded",
                )
            )
            if (!response.isSuccessful) {
                return if (response.status == 401 || response.status == 400) {
                    SuuntoResult.Failed("Suunto rejected the credentials (HTTP ${response.status}). OAuth client credentials are required and are not bundled.")
                } else {
                    SuuntoResult.Failed("Suunto service error: HTTP ${response.status}")
                }
            }
            val token = Json.parseToJsonElement(response.text()).jsonObject["access_token"]?.jsonPrimitive?.content
                ?: return SuuntoResult.Failed("No access token in response")
            secureStore.put(KEY_USERNAME, username)
            secureStore.put(KEY_TOKEN, token)
            secureStore.put(KEY_CONNECTED_AT, now().toString())
            SuuntoResult.Connected(username)
        } catch (e: com.trailbook.core.net.NetworkUnavailableException) {
            SuuntoResult.Failed("Network unavailable: ${e.message}")
        } catch (e: Exception) {
            SuuntoResult.Failed("Suunto connection failed: ${e.message}")
        }
    }

    suspend fun workouts(): List<SuuntoWorkout> {
        val token = secureStore.get(KEY_TOKEN) ?: return emptyList()
        val response = transport.execute(
            HttpRequest(
                url = WORKOUTS_URL,
                headers = mapOf("Authorization" to "Bearer $token", "Accept" to "application/json"),
            )
        )
        if (!response.isSuccessful) throw com.trailbook.core.net.NetworkUnavailableException("Suunto service error: ${response.status}")
        return parseWorkouts(response.text())
    }

    fun parseWorkouts(jsonText: String): List<SuuntoWorkout> = try {
        val root = Json.parseToJsonElement(jsonText)
        val arr = root.jsonObject["workouts"]?.jsonArray
            ?: root.jsonObject["payload"]?.jsonObject?.get("workouts")?.jsonArray
            ?: kotlinx.serialization.json.JsonArray(emptyList())
        arr.mapNotNull { el ->
            val obj = el as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
            val key = obj["workoutKey"]?.jsonPrimitive?.content ?: return@mapNotNull null
            SuuntoWorkout(
                workoutKey = key,
                name = obj["description"]?.jsonPrimitive?.content ?: obj["activityName"]?.jsonPrimitive?.content ?: "Suunto workout",
                startTime = obj["startTime"]?.jsonPrimitive?.content?.toLongOrNull(),
                distanceMeters = obj["distance"]?.jsonPrimitive?.content?.toDoubleOrNull()
                    ?: obj["distance"]?.jsonPrimitive?.double,
                durationSeconds = obj["duration"]?.jsonPrimitive?.content?.toDoubleOrNull()
                    ?: obj["duration"]?.jsonPrimitive?.double,
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    suspend fun downloadTrack(workoutKey: String): Track {
        val token = secureStore.get(KEY_TOKEN) ?: throw FormatParseException("Suunto is not connected")
        val response = transport.execute(
            HttpRequest(
                url = "$FIT_URL$workoutKey",
                headers = mapOf("Authorization" to "Bearer $token", "Accept" to "application/octet-stream"),
            )
        )
        if (!response.isSuccessful) throw com.trailbook.core.net.NetworkUnavailableException("Suunto FIT download failed: ${response.status}")
        val parsed = FitCodec.decode(response.body, { now().toString() + "-" + workoutKey }, now)
        val track = parsed.tracks.firstOrNull() ?: throw FormatParseException("Suunto workout contains no track points")
        return track.copy(source = "suunto", name = track.name.ifBlank { "Suunto $workoutKey" })
    }

    fun disconnect() {
        secureStore.remove(KEY_TOKEN)
        secureStore.remove(KEY_USERNAME)
        secureStore.remove(KEY_CONNECTED_AT)
    }

    private fun formEncode(s: String): String {
        val sb = StringBuilder()
        for (b in s.encodeToByteArray()) {
            val c = b.toInt() and 0xFF
            if (c in 0x30..0x39 || c in 0x61..0x7A || c in 0x41..0x5A || c == 0x2D || c == 0x5F || c == 0x2E || c == 0x7E) {
                sb.append(c.toChar())
            } else {
                sb.append('%')
                sb.append("0123456789ABCDEF"[(c shr 4) and 0xF])
                sb.append("0123456789ABCDEF"[c and 0xF])
            }
        }
        return sb.toString()
    }

    companion object {
        const val KEY_TOKEN = "suunto.token"
        const val KEY_USERNAME = "suunto.username"
        const val KEY_CONNECTED_AT = "suunto.connectedAt"
        const val TOKEN_URL = "https://cloudapi.suunto.com/v2/oauth/token"
        const val WORKOUTS_URL = "https://cloudapi.suunto.com/v2/workouts"
        const val FIT_URL = "https://cloudapi.suunto.com/v2/workouts/fit/?workoutKey="
        const val CLIENT_ID = ""
        const val CLIENT_SECRET = ""
    }
}
