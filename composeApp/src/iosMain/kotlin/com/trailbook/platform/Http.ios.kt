package com.trailbook.platform

import com.trailbook.core.net.HttpRequest
import com.trailbook.core.net.HttpResponse
import com.trailbook.core.net.NetworkUnavailableException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequestReloadIgnoringLocalCacheData
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.HTTPMethod
import platform.Foundation.HTTPBody
import platform.Foundation.timeoutIntervalForRequest
import platform.Foundation.setCachePolicy
import platform.Foundation.setValueForKey
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
actual fun createHttpTransport(): HttpTransport = IosHttpTransport()

@OptIn(ExperimentalForeignApi::class)
class IosHttpTransport : HttpTransport {
    private val session: NSURLSession = NSURLSession.sessionWithConfiguration(
        NSURLSessionConfiguration.defaultSessionConfiguration().apply {
            timeoutIntervalForRequest = 30.0
        }
    )

    override suspend fun execute(request: HttpRequest): HttpResponse {
        val deferred = CompletableDeferred<HttpResponse>()
        val url = NSURL.URLWithString(request.url)
            ?: throw NetworkUnavailableException("Invalid URL: ${request.url}")
        val nsRequest = NSMutableURLRequest.requestWithURL(url).apply {
            setHTTPMethod(request.method)
            setTimeoutInterval(30.0)
            setCachePolicy(NSURLRequestReloadIgnoringLocalCacheData)
            for ((k, v) in request.headers) setValueForKey(v, forKey = k)
            request.contentType?.let { setValueForKey(it, forKey = "Content-Type") }
            request.body?.let { bytes -> setHTTPBody(bytes.toNSData()) }
        }
        val task = session.dataTaskWithRequest(nsRequest) { data, response, error ->
            try {
                if (error != null) {
                    deferred.completeExceptionally(
                        NetworkUnavailableException(error.localizedDescription ?: "request failed")
                    )
                    return@dataTaskWithRequest
                }
                val http = response as? NSHTTPURLResponse
                val status = http?.statusCode?.toInt() ?: 0
                val headers = http?.allHeaderFields?.entries?.mapNotNull { entry ->
                    val key = entry.key as? String ?: return@mapNotNull null
                    key to (entry.value as? String ?: entry.value.toString())
                }?.toMap() ?: emptyMap()
                deferred.complete(HttpResponse(status, data?.toByteArray() ?: ByteArray(0), headers))
            } catch (e: Exception) {
                deferred.completeExceptionally(e)
            }
        }
        task.resume()
        return try {
            deferred.await()
        } catch (e: NetworkUnavailableException) {
            throw e
        } catch (e: Exception) {
            throw NetworkUnavailableException("Network request failed: ${e.message}")
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
fun ByteArray.toNSData(): NSData = NSData.dataWithBytes(this, this.size.toULong())

@OptIn(ExperimentalForeignApi::class)
fun NSData.toByteArray(): ByteArray {
    val size = this.length.toInt()
    val result = ByteArray(size)
    if (size > 0) {
        result.usePinned { pinned ->
            memcpy(pinned.addressOf(0), this.bytes, this.length)
        }
    }
    return result
}
