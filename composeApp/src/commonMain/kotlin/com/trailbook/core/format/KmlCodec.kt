package com.trailbook.core.format

import com.trailbook.core.geo.GeoPoint
import com.trailbook.core.model.ActivityType
import com.trailbook.core.model.Marker
import com.trailbook.core.model.Track
import com.trailbook.core.model.TrackPoint
import com.trailbook.core.stats.StatisticsEngine

object KmlCodec {

    fun write(track: Track): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n<Document>\n")
        sb.append("<name>").append(GpxCodec.escape(track.name)).append("</name>\n")
        sb.append("<Placemark><name>").append(GpxCodec.escape(track.name)).append("</name>")
        track.description?.let { sb.append("<description>").append(GpxCodec.escape(it)).append("</description>") }
        sb.append("<LineString><coordinates>")
        for (p in track.points) {
            sb.append(GpxCodec.num(p.longitude)).append(",").append(GpxCodec.num(p.latitude))
            sb.append(",").append(GpxCodec.num(p.elevation ?: 0.0)).append(" ")
        }
        sb.append("</coordinates></LineString></Placemark>\n</Document>\n</kml>\n")
        return sb.toString()
    }

    fun writeMarkers(markers: List<Marker>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n<Document>\n")
        for (m in markers) {
            sb.append("<Placemark><name>").append(GpxCodec.escape(m.name)).append("</name>")
            m.description?.let { sb.append("<description>").append(GpxCodec.escape(it)).append("</description>") }
            sb.append("<Point><coordinates>")
            sb.append(GpxCodec.num(m.longitude)).append(",").append(GpxCodec.num(m.latitude))
            m.elevation?.let { sb.append(",").append(GpxCodec.num(it)) }
            sb.append("</coordinates></Point></Placemark>\n")
        }
        sb.append("</Document>\n</kml>\n")
        return sb.toString()
    }

    fun parse(xml: String, idGenerator: () -> String, now: () -> Long): ParsedTrackFile {
        val warnings = mutableListOf<String>()
        if (!xml.contains("<kml")) throw FormatParseException("Not a KML document")
        val tracks = mutableListOf<Track>()
        val waypoints = mutableListOf<Marker>()
        val placemarks = GpxCodec.extractBlocks(xml, "<Placemark", "</Placemark>")
        for (pm in placemarks) {
            val name = GpxCodec.firstTagContent(pm, "name") ?: "Placemark"
            val desc = GpxCodec.firstTagContent(pm, "description")
            val whenTag = GpxCodec.firstTagContent(pm, "when")
            val lineCoords = GpxCodec.firstTagContent(pm, "LineString")?.let {
                GpxCodec.firstTagContent(it, "coordinates")
            }
            val trackCoords = lineCoords ?: GpxCodec.firstTagContent(pm, "Track")?.let {
                GpxCodec.firstTagContent(it, "coordinates")
            }
            if (trackCoords != null) {
                val points = parseCoordinates(trackCoords, whenTag, warnings)
                if (points.isEmpty()) {
                    warnings.add("Placemark \"$name\" has no valid coordinates")
                    continue
                }
                tracks.add(
                    Track(
                        id = idGenerator(),
                        name = name,
                        activityType = ActivityType.OTHER,
                        points = points,
                        description = desc,
                        createdAt = points.first().time,
                        modifiedAt = now(),
                        source = "kml",
                        stats = StatisticsEngine.compute(points),
                    )
                )
            } else {
                val pointCoords = GpxCodec.firstTagContent(pm, "Point")?.let {
                    GpxCodec.firstTagContent(it, "coordinates")
                }
                if (pointCoords == null) {
                    warnings.add("Placemark \"$name\" has unsupported geometry, skipped")
                    continue
                }
                val first = parseCoordinates(pointCoords, whenTag, warnings).firstOrNull()
                if (first == null) {
                    warnings.add("Placemark \"$name\" has invalid coordinates, skipped")
                    continue
                }
                waypoints.add(
                    Marker(
                        id = idGenerator(),
                        name = name,
                        latitude = first.latitude,
                        longitude = first.longitude,
                        elevation = first.elevation,
                        description = desc,
                        createdAt = now(),
                    )
                )
            }
        }
        if (tracks.isEmpty() && waypoints.isEmpty()) throw FormatParseException("KML document contains no tracks or placemarks")
        return ParsedTrackFile(tracks, waypoints, "KML", warnings)
    }

    private fun parseCoordinates(text: String, whenIso: String?, warnings: MutableList<String>): List<TrackPoint> {
        val time = GpxCodec.parseIso(whenIso) ?: 0L
        val out = mutableListOf<TrackPoint>()
        for (token in text.trim().split(Regex("\\s+"))) {
            if (token.isBlank()) continue
            val parts = token.split(",")
            if (parts.size < 2) continue
            val lon = parts[0].toDoubleOrNull() ?: continue
            val lat = parts[1].toDoubleOrNull() ?: continue
            val p = GeoPoint.tryCreate(lat, lon) ?: continue
            out.add(
                TrackPoint(
                    latitude = p.latitude,
                    longitude = p.longitude,
                    elevation = parts.getOrNull(2)?.toDoubleOrNull(),
                    time = time,
                )
            )
        }
        return out
    }
}
