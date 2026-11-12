package com.trailbook.core.format

import com.trailbook.core.model.Track

class FormatRegistry(
    private val idGenerator: () -> String,
    private val now: () -> Long,
) {
    fun detect(fileName: String, data: ByteArray): SupportedFormat {
        val lower = fileName.lowercase()
        val byExt = when {
            lower.endsWith(".gpx") -> SupportedFormat.GPX
            lower.endsWith(".kml") -> SupportedFormat.KML
            lower.endsWith(".kmz") -> SupportedFormat.KMZ
            lower.endsWith(".tcx") -> SupportedFormat.TCX
            lower.endsWith(".csv") -> SupportedFormat.CSV
            lower.endsWith(".fit") -> SupportedFormat.FIT
            else -> null
        }
        if (byExt != null) return byExt
        val head = data.decodeToString(0, minOf(256, data.size))
        return when {
            head.contains("<gpx") -> SupportedFormat.GPX
            head.contains("<kml") -> SupportedFormat.KML
            head.contains("TrainingCenterDatabase") -> SupportedFormat.TCX
            data.size > 12 && data[8] == '.'.code.toByte() && data[9] == 'F'.code.toByte() -> SupportedFormat.FIT
            head.contains(",") -> SupportedFormat.CSV
            else -> throw FormatParseException("Unsupported file format for \"$fileName\"")
        }
    }

    fun parse(fileName: String, data: ByteArray): ParsedTrackFile = when (detect(fileName, data)) {
        SupportedFormat.GPX -> GpxCodec.parse(data.decodeToString(), idGenerator, now)
        SupportedFormat.KML -> KmlCodec.parse(data.decodeToString(), idGenerator, now)
        SupportedFormat.KMZ -> KmzCodec.parse(data, idGenerator, now)
        SupportedFormat.TCX -> TcxCodec.parse(data.decodeToString(), idGenerator, now)
        SupportedFormat.CSV -> CsvCodec.parse(data.decodeToString(), idGenerator, now)
        SupportedFormat.FIT -> FitCodec.decode(data, idGenerator, now)
    }

    fun export(track: Track, format: SupportedFormat, waypoints: List<com.trailbook.core.model.Marker> = emptyList()): ByteArray =
        when (format) {
            SupportedFormat.GPX -> GpxCodec.write(track, waypoints).encodeToByteArray()
            SupportedFormat.KML -> KmlCodec.write(track).encodeToByteArray()
            SupportedFormat.KMZ -> KmzCodec.write(KmlCodec.write(track))
            SupportedFormat.TCX -> TcxCodec.write(track).encodeToByteArray()
            SupportedFormat.CSV -> CsvCodec.write(track).encodeToByteArray()
            SupportedFormat.FIT -> throw FormatParseException("FIT export is not supported; export as GPX instead")
        }
}

enum class SupportedFormat(val extension: String, val label: String, val supportsExport: Boolean) {
    GPX("gpx", "GPX", true),
    KML("kml", "KML", true),
    KMZ("kmz", "KMZ", true),
    TCX("tcx", "TCX", true),
    CSV("csv", "CSV", true),
    FIT("fit", "FIT", false);

    companion object {
        fun byExtension(ext: String): SupportedFormat? = entries.firstOrNull { it.extension == ext.lowercase() }
    }
}
