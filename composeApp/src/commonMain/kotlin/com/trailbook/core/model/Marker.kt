package com.trailbook.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Marker(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val elevation: Double? = null,
    val iconId: String = DEFAULT_ICON,
    val colorHex: String? = null,
    val listId: String? = null,
    val description: String? = null,
    val createdAt: Long,
    val modifiedAt: Long = createdAt,
    val syncedAt: Long? = null,
    val remoteId: String? = null,
) {
    fun toGeoPoint() = com.trailbook.core.geo.GeoPoint(latitude, longitude)

    companion object {
        const val DEFAULT_ICON = "pin"
    }
}

@Serializable
data class MarkerList(
    val id: String,
    val name: String,
    val colorHex: String? = null,
    val description: String? = null,
    val createdAt: Long,
    val modifiedAt: Long = createdAt,
    val syncedAt: Long? = null,
    val remoteId: String? = null,
)

object MarkerIcons {
    val ALL = listOf(
        "pin", "summit", "camp", "water", "food", "viewpoint", "parking", "shelter",
        "warning", "start", "finish", "photo", "railway", "cave", "bridge", "star"
    )
    fun isValid(iconId: String): Boolean = iconId in ALL
    fun fallback(iconId: String?): String = if (iconId != null && isValid(iconId)) iconId else "pin"
}
