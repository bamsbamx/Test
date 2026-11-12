package com.trailbook.core.format

import com.trailbook.core.geo.GeoPoint
import com.trailbook.core.model.ActivityType
import com.trailbook.core.model.Track
import com.trailbook.core.model.TrackPoint
import com.trailbook.core.stats.StatisticsEngine

object TcxCodec {

    fun write(track: Track): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<TrainingCenterDatabase xmlns=\"http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2\">\n")
        sb.append("<Activities><Activity Sport=\"").append(GpxCodec.escape(sportOf(track.activityType))).append("\">\n")
        sb.append("<Id>").append(GpxCodec.formatIso(track.createdAt)).append("</Id>\n")
        sb.append("<Lap><TotalTimeSeconds>").append((track.stats?.totalTimeSeconds ?: 0.0)).append("</TotalTimeSeconds>")
        sb.append("<DistanceMeters>").append(track.stats?.distanceMeters ?: 0.0).append("</DistanceMeters>")
        sb.append("<Track>\n")
        for (p in track.points) {
            sb.append("<Trackpoint>")
            sb.append("<Time>").append(GpxCodec.formatIso(p.time)).append("</Time>")
            sb.append("<Position><LatitudeDegrees>").append(GpxCodec.num(p.latitude))
            sb.append("</LatitudeDegrees><LongitudeDegrees>").append(GpxCodec.num(p.longitude))
            sb.append("</LongitudeDegrees></Position>")
            p.elevation?.let { sb.append("<AltitudeMeters>").append(GpxCodec.num(it)).append("</AltitudeMeters>") }
            p.heartRate?.let { sb.append("<HeartRateBpm><Value>").append(it).append("</Value></HeartRateBpm>") }
            sb.append("</Trackpoint>\n")
        }
        sb.append("</Track></Lap></Activity></Activities></TrainingCenterDatabase>\n")
        return sb.toString()
    }

    private fun sportOf(a: ActivityType): String = when (a) {
        ActivityType.RUNNING, ActivityType.TRAIL_RUNNING -> "Running"
        ActivityType.CYCLING, ActivityType.MOUNTAIN_BIKING -> "Biking"
        else -> "Other"
    }

    fun parse(xml: String, idGenerator: () -> String, now: () -> Long): ParsedTrackFile {
        val warnings = mutableListOf<String>()
        if (!xml.contains("TrainingCenterDatabase")) throw FormatParseException("Not a TCX document")
        val activities = GpxCodec.extractBlocks(xml, "<Activity", "</Activity>")
        val tracks = mutableListOf<Track>()
        for (act in activities) {
            val sport = GpxCodec.attrValue(act, "Sport") ?: "Other"
            val id = GpxCodec.firstTagContent(act, "Id")
            val points = mutableListOf<TrackPoint>()
            for (tp in GpxCodec.extractBlocks(act, "<Trackpoint", "</Trackpoint>")) {
                val time = GpxCodec.parseIso(GpxCodec.firstTagContent(tp, "Time"))
                val pos = GpxCodec.firstTagContent(tp, "Position") ?: continue
                val lat = GpxCodec.firstTagContent(pos, "LatitudeDegrees")?.toDoubleOrNull()
                val lon = GpxCodec.firstTagContent(pos, "LongitudeDegrees")?.toDoubleOrNull()
                if (lat == null || lon == null) continue
                val p = GeoPoint.tryCreate(lat, lon) ?: continue
                points.add(
                    TrackPoint(
                        latitude = p.latitude,
                        longitude = p.longitude,
                        elevation = GpxCodec.firstTagContent(tp, "AltitudeMeters")?.toDoubleOrNull(),
                        time = time ?: 0L,
                        heartRate = GpxCodec.firstTagContent(tp, "HeartRateBpm")?.let {
                            GpxCodec.firstTagContent(it, "Value")?.toIntOrNull()
                        },
                    )
                )
            }
            if (points.isEmpty()) {
                warnings.add("Activity \"$sport\" has no valid track points, skipped")
                continue
            }
            tracks.add(
                Track(
                    id = idGenerator(),
                    name = id ?: "Imported activity",
                    activityType = sportToActivity(sport),
                    points = points,
                    createdAt = points.first().time,
                    modifiedAt = now(),
                    source = "tcx",
                    stats = StatisticsEngine.compute(points),
                )
            )
        }
        if (tracks.isEmpty()) throw FormatParseException("TCX document contains no activities")
        return ParsedTrackFile(tracks, emptyList(), "TCX", warnings)
    }

    private fun sportToActivity(sport: String): ActivityType = when (sport.lowercase()) {
        "running" -> ActivityType.RUNNING
        "biking", "cycling" -> ActivityType.CYCLING
        else -> ActivityType.OTHER
    }
}
