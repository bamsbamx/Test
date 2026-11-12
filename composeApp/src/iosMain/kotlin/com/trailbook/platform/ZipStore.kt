package com.trailbook.core.format

internal object Crc32 {
    private val table = IntArray(256) { i ->
        var c = i
        repeat(8) {
            c = if (c and 1 != 0) 0xEDB88320.toInt() xor (c ushr 1) else c ushr 1
        }
        c
    }

    fun compute(data: ByteArray): Int {
        var crc = -1
        for (b in data) {
            crc = (crc ushr 8) xor table[(crc xor b.toInt()) and 0xFF]
        }
        return crc.inv()
    }
}

private class ByteArrayOutputStreamSafe {
    private val out = ArrayList<ByteArray>()
    private var size = 0

    fun write(b: ByteArray) {
        out.add(b)
        size += b.size
    }

    fun toByteArray(): ByteArray {
        val result = ByteArray(size)
        var pos = 0
        for (chunk in out) {
            chunk.copyInto(result, pos)
            pos += chunk.size
        }
        return result
    }
}

internal object ZipStoreWriter {
    fun write(entries: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStreamSafe()
        val central = ByteArrayOutputStreamSafe()
        var offset = 0
        val entryList = mutableListOf<Pair<String, Int>>()
        for ((name, content) in entries) {
            val nameBytes = name.encodeToByteArray()
            val crc = Crc32.compute(content)
            val header = ByteArray(30)
            writeLe(header, 0, 0x04034b50)
            writeLe16(header, 4, 20)
            writeLe16(header, 6, 0)
            writeLe16(header, 8, 0)
            writeLe16(header, 10, 0)
            writeLe16(header, 12, 0)
            writeLe(header, 14, crc)
            writeLe(header, 18, content.size)
            writeLe(header, 22, content.size)
            writeLe16(header, 26, nameBytes.size)
            writeLe16(header, 28, 0)
            out.write(header)
            out.write(nameBytes)
            out.write(content)
            entryList.add(name to crc)
            offset += 30 + nameBytes.size + content.size
        }
        val centralStart = offset
        for ((name, crc) in entryList) {
            val nameBytes = name.encodeToByteArray()
            val header = ByteArray(46)
            writeLe(header, 0, 0x02014b50)
            writeLe16(header, 4, 20)
            writeLe16(header, 6, 20)
            writeLe16(header, 8, 0)
            writeLe16(header, 10, 0)
            writeLe16(header, 12, 0)
            writeLe16(header, 14, 0)
            writeLe(header, 16, crc)
            writeLe(header, 20, 0)
            writeLe(header, 24, 0)
            writeLe16(header, 28, nameBytes.size)
            writeLe16(header, 30, 0)
            writeLe16(header, 32, 0)
            writeLe16(header, 34, 0)
            writeLe16(header, 36, 0)
            writeLe(header, 38, 0)
            writeLe(header, 42, centralStart)
            central.write(header)
            central.write(nameBytes)
            centralStart += 46 + nameBytes.size
        }
        val centralBytes = central.toByteArray()
        val eocd = ByteArray(22)
        writeLe(eocd, 0, 0x06054b50)
        writeLe16(eocd, 4, 0)
        writeLe16(eocd, 6, 0)
        writeLe16(eocd, 8, entryList.size)
        writeLe16(eocd, 10, entryList.size)
        writeLe(eocd, 12, centralBytes.size)
        writeLe(eocd, 16, centralStart)
        writeLe16(eocd, 20, 0)
        out.write(centralBytes)
        out.write(eocd)
        return out.toByteArray()
    }

    private fun writeLe(b: ByteArray, off: Int, v: Int) {
        b[off] = (v and 0xFF).toByte()
        b[off + 1] = ((v shr 8) and 0xFF).toByte()
        b[off + 2] = ((v shr 16) and 0xFF).toByte()
        b[off + 3] = ((v shr 24) and 0xFF).toByte()
    }

    private fun writeLe16(b: ByteArray, off: Int, v: Int) {
        b[off] = (v and 0xFF).toByte()
        b[off + 1] = ((v shr 8) and 0xFF).toByte()
    }
}

internal object ZipStoreReader {
    fun read(data: ByteArray): Map<String, ByteArray> {
        val central = findCentralDirectory(data) ?: return readSequential(data)
        val out = mutableMapOf<String, ByteArray>()
        var i = central.offset
        val end = central.offset + central.size
        while (i + 46 <= end) {
            if (readLe32(data, i) != 0x02014b50) break
            val method = readLe16(data, i + 10)
            val compressedSize = readLe32(data, i + 20).toInt()
            val nameLen = readLe16(data, i + 28).toInt()
            val extraLen = readLe16(data, i + 30).toInt()
            val commentLen = readLe16(data, i + 32).toInt()
            val localOffset = readLe32(data, i + 42).toInt()
            val name = data.decodeToString(i + 46, i + 46 + nameLen)
            if (method == 0 && localOffset + 30 <= data.size) {
                val localNameLen = readLe16(data, localOffset + 26).toInt()
                val localExtraLen = readLe16(data, localOffset + 28).toInt()
                val dataStart = localOffset + 30 + localNameLen + localExtraLen
                if (dataStart + compressedSize <= data.size) {
                    out[name] = data.copyOfRange(dataStart, dataStart + compressedSize)
                }
            }
            i += 46 + nameLen + extraLen + commentLen
        }
        return out
    }

    private fun findCentralDirectory(data: ByteArray): CentralDirectory? {
        var i = data.size - 22
        while (i >= 0) {
            if (readLe32(data, i) == 0x06054b50) {
                val size = readLe32(data, i + 12).toInt()
                val offset = readLe32(data, i + 16).toInt()
                if (offset + size <= data.size) return CentralDirectory(offset, size)
            }
            i--
        }
        return null
    }

    private fun readSequential(data: ByteArray): Map<String, ByteArray> {
        val out = mutableMapOf<String, ByteArray>()
        var i = 0
        while (i + 4 <= data.size) {
            val sig = readLe32(data, i)
            if (sig == 0x04034b50) {
                if (i + 30 > data.size) break
                val method = readLe16(data, i + 8)
                val compressedSize = readLe32(data, i + 18).toInt()
                val nameLen = readLe16(data, i + 26).toInt()
                val extraLen = readLe16(data, i + 28).toInt()
                val nameStart = i + 30
                if (nameStart + nameLen > data.size) break
                val name = data.decodeToString(nameStart, nameStart + nameLen)
                val dataStart = nameStart + nameLen + extraLen
                if (method == 0) {
                    if (dataStart + compressedSize <= data.size) {
                        out[name] = data.copyOfRange(dataStart, dataStart + compressedSize)
                    }
                } else {
                    throw FormatParseException("Compressed KMZ entries are not supported on this platform; store-only KMZ is supported")
                }
                i = dataStart + compressedSize
            } else {
                i++
            }
        }
        return out
    }

    private data class CentralDirectory(val offset: Int, val size: Int)

    private fun readLe32(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8) or
            ((b[off + 2].toInt() and 0xFF) shl 16) or ((b[off + 3].toInt() and 0xFF) shl 24)

    private fun readLe16(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)
}
