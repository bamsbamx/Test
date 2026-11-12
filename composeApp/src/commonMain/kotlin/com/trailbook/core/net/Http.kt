package com.trailbook.core.net

data class HttpRequest(
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
    val contentType: String? = null,
)

data class HttpResponse(
    val status: Int,
    val body: ByteArray,
    val headers: Map<String, String> = emptyMap(),
) {
    val isSuccessful: Boolean get() = status in 200..299
    fun text(): String = body.decodeToString()
}

interface HttpTransport {
    suspend fun execute(request: HttpRequest): HttpResponse

    class HttpException(val status: Int, message: String) : Exception(message)
}

class NetworkUnavailableException(message: String = "Network unavailable") : Exception(message)

interface ConnectivityMonitor {
    fun isOnline(): Boolean
}
