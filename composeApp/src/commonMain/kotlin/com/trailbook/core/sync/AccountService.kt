package com.trailbook.core.sync

import com.trailbook.core.net.HttpRequest
import com.trailbook.core.net.HttpTransport
import com.trailbook.platform.SecureStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class UserProfile(
    val id: String,
    val email: String,
    val displayName: String,
)

@Serializable
data class AuthSession(
    val token: String,
    val profile: UserProfile,
    val serverUrl: String,
    val signedInAt: Long,
)

sealed interface AuthResult {
    data class Success(val session: AuthSession) : AuthResult
    data class InvalidCredentials(val message: String) : AuthResult
    data class NetworkError(val message: String) : AuthResult
}

class AccountService(
    private val transport: HttpTransport,
    private val secureStore: SecureStore,
    private val now: () -> Long,
) {
    var currentSession: AuthSession? = null
        private set

    val isSignedIn: Boolean get() = currentSession != null

    suspend fun signIn(serverUrl: String, email: String, password: String): AuthResult {
        val base = serverUrl.trim().trimEnd('/')
        if (base.isEmpty()) return AuthResult.InvalidCredentials("Server address is required")
        if (email.isBlank() || password.isBlank()) return AuthResult.InvalidCredentials("Email and password are required")
        return try {
            val body = """{"email":${jsonString(email)},"password":${jsonString(password)}}"""
            val response = transport.execute(
                HttpRequest(
                    url = "$base/auth/login",
                    method = "POST",
                    headers = mapOf("Accept" to "application/json"),
                    body = body.encodeToByteArray(),
                    contentType = "application/json",
                )
            )
            when {
                response.status == 401 || response.status == 403 ->
                    AuthResult.InvalidCredentials("Invalid email or password")
                !response.isSuccessful ->
                    AuthResult.NetworkError("Server error: ${response.status}")
                else -> {
                    val profile = parseProfile(response.text())
                        ?: return AuthResult.NetworkError("Unexpected server response")
                    val token = response.headers["Authorization"]?.removePrefix("Bearer ")?.trim()
                        ?: response.headers["authorization"]?.removePrefix("Bearer ")?.trim()
                        ?: profile["token"]
                        ?: return AuthResult.NetworkError("Server did not return a token")
                    val session = AuthSession(
                        token = token,
                        profile = UserProfile(
                            id = profile["id"] ?: email,
                            email = profile["email"] ?: email,
                            displayName = profile["displayName"] ?: profile["name"] ?: email.substringBefore('@'),
                        ),
                        serverUrl = base,
                        signedInAt = now(),
                    )
                    secureStore.put(KEY_TOKEN, session.token)
                    secureStore.put(KEY_SESSION, Json.encodeToString(AuthSession.serializer(), session))
                    currentSession = session
                    AuthResult.Success(session)
                }
            }
        } catch (e: com.trailbook.core.net.NetworkUnavailableException) {
            AuthResult.NetworkError("Network unavailable: ${e.message}")
        } catch (e: Exception) {
            AuthResult.NetworkError("Sign-in failed: ${e.message}")
        }
    }

    fun restoreSession(): AuthSession? {
        if (currentSession != null) return currentSession
        val raw = secureStore.get(KEY_SESSION) ?: return null
        return try {
            val session = Json.decodeFromString(AuthSession.serializer(), raw)
            currentSession = session
            session
        } catch (e: Exception) {
            null
        }
    }

    fun signOut() {
        currentSession = null
        secureStore.remove(KEY_TOKEN)
        secureStore.remove(KEY_SESSION)
    }

    fun storedToken(): String? = secureStore.get(KEY_TOKEN)

    private fun parseProfile(json: String): Map<String, String>? = try {
        val obj = Json.parseToJsonElement(json).jsonObject
        val map = mutableMapOf<String, String>()
        for ((k, v) in obj) {
            map[k] = v.jsonPrimitive.content
        }
        map
    } catch (e: Exception) {
        null
    }

    private fun jsonString(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

    companion object {
        const val KEY_TOKEN = "account.token"
        const val KEY_SESSION = "account.session"
        const val DEFAULT_SERVER = "https://sync.trailbook.example"
    }
}
