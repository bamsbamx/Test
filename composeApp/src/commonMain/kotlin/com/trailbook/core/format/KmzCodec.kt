package com.trailbook.core.format

object KmzCodec {
    const val DOC_KML = "doc.kml"

    fun write(kml: String, additionalEntries: Map<String, ByteArray> = emptyMap()): ByteArray {
        val entries = linkedMapOf(DOC_KML to kml.encodeToByteArray())
        entries.putAll(additionalEntries)
        return ZipArchive.zip(entries)
    }

    fun parse(data: ByteArray, idGenerator: () -> String, now: () -> Long): ParsedTrackFile {
        val entries = ZipArchive.unzip(data)
        val kmlEntry = entries.entries.firstOrNull { it.key.endsWith(".kml") }
            ?: throw FormatParseException("KMZ archive contains no KML document")
        return KmlCodec.parse(kmlEntry.value.decodeToString(), idGenerator, now)
    }
}
