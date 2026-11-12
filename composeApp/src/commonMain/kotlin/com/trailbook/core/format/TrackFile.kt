package com.trailbook.core.format

import com.trailbook.core.model.Marker
import com.trailbook.core.model.Track

data class ParsedTrackFile(
    val tracks: List<Track>,
    val waypoints: List<Marker>,
    val format: String,
    val warnings: List<String> = emptyList(),
)

class FormatParseException(message: String, cause: Throwable? = null) : Exception(message, cause)
