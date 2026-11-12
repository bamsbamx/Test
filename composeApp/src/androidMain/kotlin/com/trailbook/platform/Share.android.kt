package com.trailbook.platform

import android.content.Intent
import androidx.core.content.FileProvider

actual class FileSharer actual constructor() {
    private val shareDir get() = java.io.File(AndroidContext.context.cacheDir, "shared").apply { mkdirs() }

    actual fun shareFile(name: String, content: ByteArray, mimeType: String): Boolean = try {
        val file = java.io.File(shareDir, name)
        file.writeBytes(content)
        val uri = FileProvider.getUriForFile(AndroidContext.context, AndroidContext.context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        AndroidContext.context.startActivity(
            Intent.createChooser(intent, "Share track").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (e: Exception) {
        false
    }

    actual fun saveToDownloads(name: String, content: ByteArray): Boolean = try {
        val downloads = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        val dir = java.io.File(downloads, "TrailBook").apply { mkdirs() }
        java.io.File(dir, name).writeBytes(content)
        true
    } catch (e: Exception) {
        false
    }
}
