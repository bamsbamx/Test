package com.trailbook

import com.trailbook.core.format.CsvCodec
import com.trailbook.core.format.FitCodec
import com.trailbook.core.format.FormatParseException
import com.trailbook.core.format.FormatRegistry
import com.trailbook.core.format.GpxCodec
import com.trailbook.core.format.KmlCodec
import com.trailbook.core.format.KmzCodec
import com.trailbook.core.format.SupportedFormat
import com.trailbook.core.format.TcxCodec
import com.trailbook.core.model.Marker
import com.trailbook.core.model.Track
import com.trailbook.core.model.TrackPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class FormatTest {

    private val track = Track(
        id = "t1",
        name = "Test hike",
        points = listOf(
            TrackPoint(47.01, 8.01, 500.0, 1_700_000_000_000L),
            TrackPoint(47.02, 8.02, 600.0, 1_700_000_060_000L),
        ),
        createdAt = 1_700_000_000_000L,
    )

    private val waypoint = Marker(
        id = "m1", name = "Summit", latitude = 47.03, longitude = 8.03, elevation = 1200.0, createdAt = 1L,
    )

    private fun roundtrip(name: String, write: (Track) -> ByteArray, assertions: (com.trailbook.core.format.ParsedTrackFile) -> Unit = {}, expectName: String = "Test hike") {
        val data = write(track)
        val parsed = FormatRegistry({ "gen-id" }, { 42L }).parse(name, data)
        assertEquals(1, parsed.tracks.size)
        val back = parsed.tracks.first()
        assertEquals(expectName, back.name)
        assertEquals(2, back.points.size)
        assertEquals(47.01, back.points[0].latitude, 1e-6)
        assertEquals(8.02, back.points[1].longitude, 1e-6)
        assertEquals(600.0, back.points[1].elevation!!, 0.001)
        assertions(parsed)
    }

    @Test
    fun gpxRoundtripPreservesTrackAndWaypoints() {
        roundtrip("t.gpx", write = { GpxCodec.write(it, listOf(waypoint)).encodeToByteArray() })
        val gpx = GpxCodec.write(track, listOf(waypoint))
        val parsed = GpxCodec.parse(gpx, { "gen" }, { 42L })
        assertEquals(1, parsed.waypoints.size)
        assertEquals("Summit", parsed.waypoints[0].name)
    }

    @Test
    fun gpxRoundtripPreservesTimestamps() {
        val parsed = GpxCodec.parse(GpxCodec.write(track), { "gen" }, { 42L })
        assertEquals(1_700_000_000_000L, parsed.tracks.first().points[0].time)
        assertEquals(1_700_000_060_000L, parsed.tracks.first().points[1].time)
    }

    @Test
    fun kmlRoundtrip() {
        roundtrip("t.kml", write = { KmlCodec.write(it).encodeToByteArray() })
    }

    @Test
    fun tcxRoundtrip() {
        roundtrip("t.tcx", write = { TcxCodec.write(it).encodeToByteArray() }, expectName = "2023-11-14T22:13:20+00:00")
    }

    @Test
    fun csvRoundtrip() {
        roundtrip("t.csv", write = { CsvCodec.write(it).encodeToByteArray() }, expectName = "Imported CSV track")
    }

    @Test
    fun kmzRoundtrip() {
        val kmz = KmzCodec.write(KmlCodec.write(track))
        val parsed = KmzCodec.parse(kmz, { "gen" }, { 42L })
        assertEquals(1, parsed.tracks.size)
        assertEquals(2, parsed.tracks.first().points.size)
    }

    @Test
    fun malformedGpxThrows() {
        try {
            GpxCodec.parse("<gpx></gpx>", { "gen" }, { 42L })
            fail("Expected FormatParseException")
        } catch (e: FormatParseException) {
        }
    }

    @Test
    fun fitGarbageThrows() {
        try {
            FitCodec.decode(ByteArray(64) { it.toByte() }, { "gen" }, { 42L })
            fail("Expected FormatParseException")
        } catch (e: FormatParseException) {
            assertTrue(e.message!!.isNotEmpty())
        }
    }

    @Test
    fun fitTooSmallThrows() {
        try {
            FitCodec.decode(ByteArray(4), { "gen" }, { 42L })
            fail("Expected FormatParseException")
        } catch (e: FormatParseException) {
        }
    }

    @Test
    fun detectByExtensionAndContent() {
        val registry = FormatRegistry({ "gen" }, { 42L })
        assertEquals(SupportedFormat.GPX, registry.detect("a.gpx", ByteArray(0)))
        assertEquals(SupportedFormat.KMZ, registry.detect("a.KMZ", ByteArray(0)))
        assertEquals(SupportedFormat.GPX, registry.detect("unknown", "<gpx".encodeToByteArray()))
        assertEquals(SupportedFormat.CSV, registry.detect("unknown", "lat,lon".encodeToByteArray()))
    }

    @Test
    fun detectUnknownThrows() {
        val registry = FormatRegistry({ "gen" }, { 42L })
        try {
            registry.detect("unknown.bin", "plain text".encodeToByteArray())
            fail("Expected FormatParseException")
        } catch (e: FormatParseException) {
        }
    }

    @Test
    fun exportRejectsFit() {
        try {
            FormatRegistry({ "gen" }, { 42L }).export(track, SupportedFormat.FIT)
            fail("Expected FormatParseException")
        } catch (e: FormatParseException) {
        }
    }

    @Test
    fun exportAllSupportedFormats() {
        val registry = FormatRegistry({ "gen" }, { 42L })
        for (format in SupportedFormat.entries.filter { it.supportsExport }) {
            val data = registry.export(track, format)
            assertTrue(data.isNotEmpty(), "Export for $format produced no data")
        }
    }
}
