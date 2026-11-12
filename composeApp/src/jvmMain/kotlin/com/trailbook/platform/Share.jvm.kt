package com.trailbook.platform

import java.io.File

actual class FileSharer actual constructor() {
    private val shareDir = File(System.getProperty("user.home"), ".trailbook/exports").apply { mkdirs() }

    actual fun shareFile(name: String, content: ByteArray, mimeType: String): Boolean = try {
        val f = File(shareDir, name)
        f.writeBytes(content)
        true
    } catch (e: Exception) {
        false
    }

    actual fun saveToDownloads(name: String, content: ByteArray): Boolean = try {
        val downloads = File(System.getProperty("user.home"), "Downloads").apply { mkdirs() }
        File(downloads, name).writeBytes(content)
        true
    } catch (e: Exception) {
        false
    }
}
