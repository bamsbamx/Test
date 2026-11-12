package com.trailbook.core.format


import com.trailbook.core.geo.GeoPoint
import com.trailbook.core.model.ActivityType
import com.trailbook.core.model.Marker
import com.trailbook.core.model.Track
import com.trailbook.core.model.TrackPoint
import com.trailbook.core.model.TrackStats
import com.trailbook.core.stats.StatisticsEngine

object GpxCodec {
    private const val NS = "http://www.topografix.com/GPX/1/1"

    fun write(track: Track, waypoints: List<Marker> = emptyList()): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"TrailBook\" xmlns=\"$NS\">\n")
        sb.append("<metadata><name>").append(escape(track.name)).append("</name><time>")
        sb.append(formatIso(track.createdAt)).append("</time></metadata>\n")
        for (w in waypoints) {
            sb.append("<wpt lat=\"").append(num(w.latitude)).append("\" lon=\"").append(num(w.longitude)).append("\">")
            w.elevation?.let { sb.append("<ele>").append(num(it)).append("</ele>") }
            sb.append("<time>").append(formatIso(w.createdAt)).append("</time>")
            sb.append("<name>").append(escape(w.name)).append("</name>")
            w.description?.let { sb.append("<desc>").append(escape(it)).append("</desc>") }
            sb.append("<sym>").append(escape(w.iconId)).append("</sym>")
            sb.append("</wpt>\n")
        }
        sb.append("<trk>\n")
        sb.append("<name>").append(escape(track.name)).append("</name>")
        track.description?.let { sb.append("<desc>").append(escape(it)).append("</desc>") }
        sb.append("<type>").append(escape(track.activityType.id)).append("</type>\n")
        track.points.groupBySequence().forEach { segment ->
            sb.append("<trkseg>\n")
            for (p in segment) {
                sb.append("<trkpt lat=\"").append(num(p.latitude)).append("\" lon=\"").append(num(p.longitude)).append("\">")
                p.elevation?.let { sb.append("<ele>").append(num(it)).append("</ele>") }
                sb.append("<time>").append(formatIso(p.time)).append("</time>")
                p.speedMps?.let { sb.append("<speed>").append(num(it)).append("</speed>") }
                p.bearingDeg?.let { sb.append("<course>").append(num(it)).append("</course>") }
                p.heartRate?.let {
                    sb.append("<extensions><gpxtpx:TrackPointExtension xmlns:gpxtpx=\"http://www.garmin.com/xmlschemas/TrackPointExtension/v1\">")
                    sb.append("<gpxtpx:hr>").append(it).append("</gpxtpx:hr></gpxtpx:TrackPointExtension></extensions>")
                }
                sb.append("</trkpt>\n")
            }
            sb.append("</trkseg>\n")
        }
        sb.append("</trk>\n</gpx>\n")
        return sb.toString()
    }

    private fun List<TrackPoint>.groupBySequence(): List<List<TrackPoint>> {
        if (isEmpty()) return emptyList()
        val segments = mutableListOf<List<TrackPoint>>()
        var current = mutableListOf(this[0])
        for (i in 1 until size) {
            val prev = this[i - 1]
            val cur = this[i]
            val gap = GeoPoint(prev.latitude, prev.longitude).distanceTo(GeoPoint(cur.latitude, cur.longitude))
            if (gap > 5000.0) {
                segments.add(current)
                current = mutableListOf(cur)
            } else {
                current.add(cur)
            }
        }
        segments.add(current)
        return segments
    }

    fun parse(xml: String, idGenerator: () -> String, now: () -> Long): ParsedTrackFile {
        val warnings = mutableListOf<String>()
        if (!xml.contains("<gpx")) throw FormatParseException("Not a GPX document")
        val tracks = mutableListOf<Track>()
        val waypoints = mutableListOf<Marker>()
        val trkBlocks = extractBlocks(xml, "<trk", "</trk>")
        for (trk in trkBlocks) {
            val name = firstTagContent(trk, "name") ?: "Imported track"
            val desc = firstTagContent(trk, "desc")
            val type = firstTagContent(trk, "type")
            val points = mutableListOf<TrackPoint>()
            val segs = extractBlocks(trk, "<trkseg", "</trkseg>")
            val segBlocks = if (segs.isEmpty()) listOf(trk) else segs
            for (seg in segBlocks) {
                val pts = extractBlocks(seg, "<trkpt", "</trkpt>")
                for (pt in pts) {
                    val parsed = parsePointTag(pt, warnings) ?: continue
                    points.add(parsed)
                }
            }
            if (points.isEmpty()) {
                warnings.add("Track \"$name\" has no valid points and was skipped")
                continue
            }
            val stats = StatisticsEngine.compute(points)
            tracks.add(
                Track(
                    id = idGenerator(),
                    name = name,
                    activityType = ActivityType.byId(type),
                    points = points,
                    description = desc,
                    createdAt = points.first().time,
                    modifiedAt = now(),
                    source = "gpx",
                    stats = stats,
                )
            )
        }
        val wpts = extractBlocks(xml, "<wpt", "</wpt>")
        for (w in wpts) {
            val lat = attrValue(w, "lat")?.toDoubleOrNull()
            val lon = attrValue(w, "lon")?.toDoubleOrNull()
            if (lat == null || lon == null) { warnings.add("Waypoint with invalid coordinates skipped"); continue }
            val p = GeoPoint.tryCreate(lat, lon)
            if (p == null) { warnings.add("Waypoint out of range skipped"); continue }
            waypoints.add(
                Marker(
                    id = idGenerator(),
                    name = firstTagContent(w, "name") ?: "Waypoint",
                    latitude = p.latitude,
                    longitude = p.longitude,
                    elevation = firstTagContent(w, "ele")?.toDoubleOrNull(),
                    iconId = com.trailbook.core.model.MarkerIcons.fallback(firstTagContent(w, "sym")),
                    description = firstTagContent(w, "desc"),
                    createdAt = parseIso(firstTagContent(w, "time")) ?: now(),
                )
            )
        }
        if (tracks.isEmpty() && waypoints.isEmpty()) throw FormatParseException("GPX document contains no tracks or waypoints")
        return ParsedTrackFile(tracks, waypoints, "GPX", warnings)
    }

    internal fun parsePointTag(tag: String, warnings: MutableList<String>): TrackPoint? {
        val lat = attrValue(tag, "lat")?.toDoubleOrNull()
        val lon = attrValue(tag, "lon")?.toDoubleOrNull()
        if (lat == null || lon == null) return null
        val p = GeoPoint.tryCreate(lat, lon) ?: return null
        val time = parseIso(firstTagContent(tag, "time"))
        if (time == null) warnings.add("Point without timestamp at ${p.latitude},${p.longitude}")
        return TrackPoint(
            latitude = p.latitude,
            longitude = p.longitude,
            elevation = firstTagContent(tag, "ele")?.toDoubleOrNull(),
            time = time ?: 0L,
            speedMps = firstTagContent(tag, "speed")?.toDoubleOrNull(),
            bearingDeg = firstTagContent(tag, "course")?.toDoubleOrNull(),
            heartRate = Regex("hr>(\\d+)<").find(tag)?.groupValues?.get(1)?.toIntOrNull(),
        )
    }

    internal fun extractBlocks(xml: String, open: String, close: String): List<String> {
        val out = mutableListOf<String>()
        var idx = 0
        while (true) {
            val start = xml.indexOf(open, idx)
            if (start < 0) break
            val end = xml.indexOf(close, start)
            if (end < 0) break
            out.add(xml.substring(start, end + close.length))
            idx = end + close.length
        }
        return out
    }

    internal fun firstTagContent(xml: String, tag: String): String? {
        val open = "<$tag"
        val start = xml.indexOf(open)
        if (start < 0) return null
        val gt = xml.indexOf('>', start)
        if (gt < 0) return null
        if (xml[gt - 1] == '/') return null
        val close = "</$tag>"
        val end = xml.indexOf(close, gt)
        if (end < 0) return null
        return unescape(xml.substring(gt + 1, end).trim())
    }

    internal fun attrValue(tag: String, attr: String): String? {
        val m = Regex("$attr\\s*=\\s*\"([^\"]*)\"").find(tag) ?: return null
        return m.groupValues[1]
    }

    internal fun escape(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    internal fun unescape(s: String): String = s
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")

    internal fun num(v: Double): String {
        val r = kotlin.math.round(v * 1e7) / 1e7
        return if (r == r.toLong().toDouble()) r.toLong().toString() else r.toString()
    }

    internal fun formatIso(epochMillis: Long): String {
        val seconds = epochMillis / 1000
        val millis = (epochMillis % 1000).toInt()
        val days = seconds / 86400
        val daySecs = seconds % 86400
        val (y, mo, d) = civilFromDays(days)
        val h = daySecs / 3600
        val mi = (daySecs % 3600) / 60
        val s = daySecs % 60
        val base = Str.fmt("%04d-%02d-%02dT%02d:%02d:%02d", y, mo, d, h, mi, s)
        return if (millis == 0) "$base+00:00" else "$base.${millis.toString().padStart(3, '0')}+00:00"
    }

    internal fun parseIso(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        val m = Regex("(\\d{4})-(\\d{2})-(\\d{2})[Tt ](\\d{2}):(\\d{2}):(\\d{2})(?:\\.(\\d{1,3}))?(Z|[+-]\\d{2}:?\\d{2})?")
            .find(s.trim()) ?: return null
        val (y, mo, d) = Triple(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        val h = m.groupValues[4].toInt(); val mi = m.groupValues[5].toInt(); val sec = m.groupValues[6].toInt()
        val ms = m.groupValues[7].ifEmpty { "0" }.padEnd(3, '0').toInt()
        val tz = m.groupValues[8]
        var epoch = daysFromCivil(y, mo, d) * 86400L + h * 3600L + mi * 60L + sec
        epoch *= 1000
        var total = epoch + ms
        if (tz.isNotEmpty() && tz != "Z") {
            val sign = if (tz[0] == '-') 1 else -1
            val digits = tz.substring(1).replace(":", "")
            val oh = digits.take(2).toIntOrNull() ?: 0
            val om = digits.drop(2).toIntOrNull() ?: 0
            total += sign * (oh * 3600L + om * 60L) * 1000
        }
        return total
    }

    internal fun civilFromDays(z: Long): Triple<Int, Int, Int> {
        var z2 = z + 719468
        val era = (if (z2 >= 0) z2 else z2 - 146096) / 146097
        val doe = z2 - era * 146097
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val y = yoe + era * 400
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = if (mp < 10) mp + 3 else mp - 9
        z2 = 0
        return Triple((if (m <= 2) y + 1 else y).toInt(), m.toInt(), d.toInt())
    }

    internal fun daysFromCivil(y: Int, m: Int, d: Int): Long {
        val yy = if (m <= 2) y - 1 else y
        val era = (if (yy >= 0) yy else yy - 399) / 400
        val yoe = yy - era * 400
        val mp = if (m > 2) m - 3 else m + 9
        val doy = (153 * mp + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era.toLong() * 146097L + doe - 719468L
    }
}
