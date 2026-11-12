package com.trailbook.core.format

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

actual object ZipArchive {
    actual fun zip(entries: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            for ((name, content) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content)
                zos.closeEntry()
            }
        }
        return out.toByteArray()
    }

    actual fun unzip(data: ByteArray): Map<String, ByteArray> {
        val out = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(data)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                if (!entry.isDirectory) {
                    val buffer = ByteArrayOutputStream()
                    val chunk = ByteArray(8192)
                    while (true) {
                        val read = zis.read(chunk)
                        if (read < 0) break
                        buffer.write(chunk, 0, read)
                    }
                    out[entry.name] = buffer.toByteArray()
                }
                zis.closeEntry()
            }
        }
        return out
    }
}
