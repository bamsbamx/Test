package com.trailbook.core.search

import com.trailbook.core.geo.CoordinateFormat
import com.trailbook.core.geo.GeoPoint
import com.trailbook.core.net.HttpRequest
import com.trailbook.core.net.HttpTransport
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class PlaceResult(
    val name: String,
    val detail: String,
    val latitude: Double,
    val longitude: Double,
    val type: String? = null,
)

sealed interface SearchResult {
    data class Places(val results: List<PlaceResult>) : SearchResult
    data class Coordinates(val point: GeoPoint, val formatted: String) : SearchResult
    data class Failure(val message: String) : SearchResult
}

class GeocodingService(private val transport: HttpTransport) {

    suspend fun search(query: String, limit: Int = 10): SearchResult {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return SearchResult.Failure("Empty query")
        when (val coordinate = CoordinateFormat.parse(trimmed)) {
            is CoordinateFormat.ParseResult.Success -> {
                val p = coordinate.point
                return SearchResult.Coordinates(p, CoordinateFormat.format(p, com.trailbook.core.geo.CoordinateDisplayFormat.DECIMAL_DEGREES))
            }
            is CoordinateFormat.ParseResult.Failure -> if (coordinate.message == "Latitude/longitude out of range") {
                return SearchResult.Failure(coordinate.message)
            }
        }
        return try {
            val url = "https://nominatim.openstreetmap.org/search?q=" + urlEncode(trimmed) + "&format=json&limit=" + limit + "&addressdetails=1"
            val response = transport.execute(HttpRequest(url, headers = mapOf("User-Agent" to USER_AGENT)))
            if (!response.isSuccessful) return SearchResult.Failure("Search service error: ${response.status}")
            SearchResult.Places(parse(response.text()))
        } catch (e: Exception) {
            SearchResult.Failure("Search unavailable: ${e.message}")
        }
    }

    fun parse(json: String): List<PlaceResult> = try {
        val arr = Json.parseToJsonElement(json).jsonArray
        arr.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val lat = obj["lat"]?.jsonPrimitive?.double ?: return@mapNotNull null
            val lon = obj["lon"]?.jsonPrimitive?.double ?: return@mapNotNull null
            val p = GeoPoint.tryCreate(lat, lon) ?: return@mapNotNull null
            PlaceResult(
                name = obj["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                    ?: obj["display_name"]?.jsonPrimitive?.content?.substringBefore(",") ?: "Unknown",
                detail = obj["display_name"]?.jsonPrimitive?.content ?: "",
                latitude = p.latitude,
                longitude = p.longitude,
                type = obj["type"]?.jsonPrimitive?.content,
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun urlEncode(s: String): String {
        val sb = StringBuilder()
        for (b in s.encodeToByteArray()) {
            val c = b.toInt() and 0xFF
            if (c in 0x30..0x39 || c in 0x61..0x7A || c in 0x41..0x5A ||
                c == 0x2D || c == 0x5F || c == 0x2E || c == 0x7E
            ) {
                sb.append(c.toChar())
            } else {
                sb.append('%')
                sb.append(HEX[(c shr 4) and 0xF])
                sb.append(HEX[c and 0xF])
            }
        }
        return sb.toString()
    }

    companion object {
        const val USER_AGENT = "TrailBook/1.0 (offline-first outdoor tracking)"
        private const val HEX = "0123456789ABCDEF"
    }
}

data class RecentSearch(val query: String, val timestamp: Long)

class RecentSearchStore(private val maxEntries: Int = 20) {
    private val entries = mutableListOf<RecentSearch>()

    fun all(): List<RecentSearch> = entries.toList()

    fun record(query: String, now: Long) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        entries.removeAll { it.query.equals(trimmed, ignoreCase = true) }
        entries.add(0, RecentSearch(trimmed, now))
        while (entries.size > maxEntries) entries.removeAt(entries.size - 1)
    }

    fun clear() = entries.clear()
}
