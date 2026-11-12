package com.trailbook.helpers

import com.trailbook.core.net.HttpRequest
import com.trailbook.core.net.HttpResponse
import com.trailbook.core.net.HttpTransport
import com.trailbook.core.net.NetworkUnavailableException
import com.trailbook.platform.AppFileSystem
import com.trailbook.platform.SecureStore
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

class InMemoryFileSystem : AppFileSystem {
    val files = mutableMapOf<String, ByteArray>()

    override fun dataDirectory(): String = "/memory"
    override fun readText(path: String): String? = files[path]?.decodeToString()
    override fun writeText(path: String, content: String) { files[path] = content.encodeToByteArray() }
    override fun readBytes(path: String): ByteArray? = files[path]
    override fun writeBytes(path: String, content: ByteArray) { files[path] = content }
    override fun delete(path: String): Boolean = files.remove(path) != null
    override fun listFiles(directory: String): List<String> = files.keys.filter { it.startsWith("$directory/") }
    override fun exists(path: String): Boolean = files.containsKey(path)
}

class InMemorySecureStore : SecureStore {
    private val map = mutableMapOf<String, String>()
    override fun put(key: String, value: String) { map[key] = value }
    override fun get(key: String): String? = map[key]
    override fun remove(key: String) { map.remove(key) }
}

class FakeTransport(
    private val handler: suspend (HttpRequest) -> HttpResponse,
) : HttpTransport {
    val requests = mutableListOf<HttpRequest>()
    override suspend fun execute(request: HttpRequest): HttpResponse {
        requests.add(request)
        return handler(request)
    }

    companion object {
        fun offline(): FakeTransport = FakeTransport { throw NetworkUnavailableException("offline") }
        fun json(status: Int, body: String, headers: Map<String, String> = emptyMap()): FakeTransport =
            FakeTransport { HttpResponse(status, body.encodeToByteArray(), headers) }
    }
}

val testJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun <T> encodeList(serializer: KSerializer<T>, items: List<T>): String =
    testJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(serializer), items)
