package com.trailbook.core.format

import com.trailbook.core.geo.GeoPoint
import com.trailbook.core.model.Track
import com.trailbook.core.model.TrackPoint
import com.trailbook.core.stats.StatisticsEngine

object CsvCodec {

    fun write(track: Track): String {
        val sb = StringBuilder()
        sb.append("latitude,longitude,elevation,time,speed,bearing,accuracy,heart_rate\n")
        for (p in track.points) {
            sb.append(p.latitude).append(",").append(p.longitude).append(",")
            sb.append(p.elevation ?: "").append(",")
            sb.append(p.time).append(",")
            sb.append(p.speedMps ?: "").append(",")
            sb.append(p.bearingDeg ?: "").append(",")
            sb.append(p.accuracyM ?: "").append(",")
            sb.append(p.heartRate ?: "").append("\n")
        }
        return sb.toString()
    }

    fun parse(content: String, idGenerator: () -> String, now: () -> Long): ParsedTrackFile {
        val warnings = mutableListOf<String>()
        val lines = content.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) throw FormatParseException("Empty CSV file")
        val header = lines[0].lowercase().split(",").map { it.trim() }
        val latIdx = header.indexOfFirst { it.contains("lat") }
        val lonIdx = header.indexOfFirst { it.contains("lon") }
        if (latIdx < 0 || lonIdx < 0) throw FormatParseException("CSV must contain latitude and longitude columns")
        val eleIdx = header.indexOfFirst { it.contains("ele") || it.contains("alt") }
        val timeIdx = header.indexOfFirst { it.contains("time") || it.contains("date") }
        val speedIdx = header.indexOfFirst { it.contains("speed") }
        val brgIdx = header.indexOfFirst { it.contains("bearing") || it.contains("course") }
        val accIdx = header.indexOfFirst { it.contains("accuracy") }
        val hrIdx = header.indexOfFirst { it.contains("heart") }
        val points = mutableListOf<TrackPoint>()
        for ((lineNo, line) in lines.drop(1).withIndex()) {
            val cells = line.split(",")
            val lat = cells.getOrNull(latIdx)?.trim()?.toDoubleOrNull()
            val lon = cells.getOrNull(lonIdx)?.trim()?.toDoubleOrNull()
            if (lat == null || lon == null) {
                warnings.add("Line ${lineNo + 2}: invalid coordinates, skipped")
                continue
            }
            val p = GeoPoint.tryCreate(lat, lon)
            if (p == null) {
                warnings.add("Line ${lineNo + 2}: coordinates out of range, skipped")
                continue
            }
            val time = timeIdx?.let { cells.getOrNull(it)?.trim()?.toLongOrNull() } ?: 0L
            points.add(
                TrackPoint(
                    latitude = p.latitude,
                    longitude = p.longitude,
                    elevation = eleIdx?.let { cells.getOrNull(it)?.trim()?.toDoubleOrNull() },
                    time = time,
                    speedMps = speedIdx?.let { cells.getOrNull(it)?.trim()?.toDoubleOrNull() },
                    bearingDeg = brgIdx?.let { cells.getOrNull(it)?.trim()?.toDoubleOrNull() },
                    accuracyM = accIdx?.let { cells.getOrNull(it)?.trim()?.toDoubleOrNull() },
                    heartRate = hrIdx?.let { cells.getOrNull(it)?.trim()?.toIntOrNull() },
                )
            )
        }
        if (points.isEmpty()) throw FormatParseException("CSV file contains no valid points")
        return ParsedTrackFile(
            listOf(
                Track(
                    id = idGenerator(),
                    name = "Imported CSV track",
                    points = points,
                    createdAt = points.first().time,
                    modifiedAt = now(),
                    source = "csv",
                    stats = StatisticsEngine.compute(points),
                )
            ),
            emptyList(),
            "CSV",
            warnings,
        )
    }
}
