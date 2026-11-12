package com.trailbook.platform

import com.trailbook.core.net.HttpRequest
import com.trailbook.core.net.HttpResponse
import com.trailbook.core.net.HttpTransport
import com.trailbook.core.net.NetworkUnavailableException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

actual fun createHttpTransport(): HttpTransport = JvmHttpTransport()

class JvmHttpTransport : HttpTransport {
    override suspend fun execute(request: HttpRequest): HttpResponse = try {
        val connection = URL(request.url).openConnection() as HttpURLConnection
        connection.requestMethod = request.method
        connection.connectTimeout = 15000
        connection.readTimeout = 30000
        for ((k, v) in request.headers) connection.setRequestProperty(k, v)
        request.contentType?.let { connection.setRequestProperty("Content-Type", it) }
        if (request.body != null) {
            connection.doOutput = true
            connection.getOutputStream().use { it.write(request.body) }
        }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.use { it.readBytes() } ?: ByteArray(0)
        HttpResponse(status, body)
    } catch (e: IOException) {
        throw NetworkUnavailableException("Network request failed: ${e.message}")
    }
}
