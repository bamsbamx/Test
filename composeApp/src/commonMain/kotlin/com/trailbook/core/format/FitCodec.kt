package com.trailbook.core.format

import com.trailbook.core.model.Track
import com.trailbook.core.model.TrackPoint
import com.trailbook.core.stats.StatisticsEngine

object FitCodec {
    private const val HEADER_SIZE = 12
    private const val MESSAGE_DEFINITION = 0
    private const val MESSAGE_RECORD = 20
    private const val MESSAGE_EVENT = 21
    private const val MESSAGE_COURSE_POINT = 32
    private const val MESSAGE_SESSION = 18

    private class DefinitionMessage {
        var littleEndian = true
        val fields = LinkedHashMap<Int, Pair<Int, Int>>()
    }

    fun decode(data: ByteArray, idGenerator: () -> String, now: () -> Long): ParsedTrackFile {
        val warnings = mutableListOf<String>()
        if (data.size < HEADER_SIZE) throw FormatParseException("File too small to be a FIT file")
        val headerSize = data[0].toInt() and 0xFF
        val magic = data.readBytes(8, 12)
        if (!magic.decodeToString().startsWith(".FIT")) throw FormatParseException("Not a FIT file (bad magic)")
        var offset = headerSize
        val definitions = HashMap<Int, DefinitionMessage>()
        val records = mutableListOf<TrackPoint>()
        var timeMs = 0L
        var activityName: String? = null
        val waypoints = mutableListOf<Pair<Double, Double>>()

        while (offset < data.size) {
            val headerByte = data[offset].toInt() and 0xFF
            if (headerByte == 0) break
            val messageType = headerByte and 0x0F
            val localType = (headerByte shr 4) and 0x0F
            offset += 1
            when (messageType) {
                0 -> {
                    val dm = DefinitionMessage()
                    dm.littleEndian = (data[offset].toInt() and 0x01) != 0
                    offset += 1
                    offset += 1
                    offset += 1
                    val fieldCount = data[offset].toInt() and 0xFF
                    offset += 1
                    repeat(fieldCount) {
                        val fieldDef = data[offset].toInt() and 0xFF
                        offset += 1
                        val size = data[offset].toInt() and 0xFF
                        offset += 1
                        val type = data[offset].toInt() and 0xFF
                        offset += 1
                        dm.fields[fieldDef] = Pair(size, type)
                    }
                    definitions[localType] = dm
                }
                MESSAGE_RECORD -> {
                    val dm = definitions[localType]
                    if (dm == null) {
                        warnings.add("Record message without definition, skipped")
                        break
                    }
                    var lat: Double? = null
                    var lon: Double? = null
                    var ele: Double? = null
                    var hr: Int? = null
                    for ((fieldDef, spec) in dm.fields) {
                        val (size, type) = spec
                        val bytes = data.copyOfRange(offset, (offset + size).coerceAtMost(data.size))
                        offset += size
                        when (fieldDef) {
                            0 -> lat = decodeSemicircles(bytes, dm.littleEndian)
                            1 -> lon = decodeSemicircles(bytes, dm.littleEndian)
                            2 -> ele = decodeScaled(bytes, dm.littleEndian, 5.0, 500.0)
                            3 -> timeMs = decodeUint32(bytes, dm.littleEndian) * 1000L + 631065600000L
                            4 -> hr = bytes.firstOrNull()?.toInt()?.and(0xFF)?.takeIf { it in 1..255 }
                        }
                    }
                    if (lat != null && lon != null) {
                        records.add(TrackPoint(latitude = lat, longitude = lon, elevation = ele, time = timeMs, heartRate = hr))
                    }
                }
                MESSAGE_COURSE_POINT -> {
                    val dm = definitions[localType] ?: continue
                    var lat: Double? = null
                    var lon: Double? = null
                    for ((fieldDef, spec) in dm.fields) {
                        val (size, _) = spec
                        val bytes = data.copyOfRange(offset, (offset + size).coerceAtMost(data.size))
                        offset += size
                        if (fieldDef == 1) lat = decodeSemicircles(bytes, dm.littleEndian)
                        if (fieldDef == 2) lon = decodeSemicircles(bytes, dm.littleEndian)
                    }
                    if (lat != null && lon != null) waypoints.add(lat to lon)
                }
                MESSAGE_SESSION, MESSAGE_EVENT -> {
                    val dm = definitions[localType]
                    if (dm == null) { continue }
                    var skip = 0
                    for ((_, spec) in dm.fields) skip += spec.first
                    offset += skip
                }
                else -> throw FormatParseException("Unsupported FIT message type $messageType at offset $offset")
            }
        }
        if (records.isEmpty()) throw FormatParseException("FIT file contains no track records")
        val track = Track(
            id = idGenerator(),
            name = activityName ?: "Imported FIT track",
            points = records,
            createdAt = records.first().time,
            modifiedAt = now(),
            source = "fit",
            stats = StatisticsEngine.compute(records),
        )
        return ParsedTrackFile(listOf(track), emptyList(), "FIT", warnings)
    }

    private fun decodeSemicircles(bytes: ByteArray, littleEndian: Boolean): Double? {
        val raw = decodeInt32(bytes, littleEndian) ?: return null
        if (raw == 0x7FFFFFFF) return null
        return raw * 180.0 / (1L shl 31)
    }

    private fun decodeScaled(bytes: ByteArray, littleEndian: Boolean, scale: Double, offset: Double): Double? {
        val raw = when (bytes.size) {
            1 -> bytes[0].toInt() and 0xFF
            2 -> if (littleEndian) (bytes[0].toInt() and 0xFF or ((bytes[1].toInt() and 0xFF) shl 8))
                 else (bytes[1].toInt() and 0xFF or ((bytes[0].toInt() and 0xFF) shl 8))
            4 -> decodeUint32(bytes, littleEndian).toInt()
            else -> return null
        }
        if (raw == 0x7FFFFFFF || raw == 0xFFFF) return null
        return raw / scale - offset
    }

    private fun decodeUint32(bytes: ByteArray, littleEndian: Boolean): Long {
        var v = 0L
        if (littleEndian) {
            for (i in 3 downTo 0) v = (v shl 8) or (bytes[i].toLong() and 0xFF)
        } else {
            for (i in 0..3) v = (v shl 8) or (bytes[i].toLong() and 0xFF)
        }
        return v and 0xFFFFFFFFL
    }

    private fun decodeInt32(bytes: ByteArray, littleEndian: Boolean): Int? {
        if (bytes.size < 4) return null
        var v = 0
        if (littleEndian) {
            for (i in 3 downTo 0) v = (v shl 8) or (bytes[i].toInt() and 0xFF)
        } else {
            for (i in 0..3) v = (v shl 8) or (bytes[i].toInt() and 0xFF)
        }
        return v
    }

    private fun ByteArray.readBytes(from: Int, to: Int): ByteArray = copyOfRange(from, to)
}
