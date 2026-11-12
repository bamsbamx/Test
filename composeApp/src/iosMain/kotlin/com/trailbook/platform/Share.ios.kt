package com.trailbook.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

@OptIn(ExperimentalForeignApi::class)
actual class FileSharer actual constructor() {
    actual fun shareFile(name: String, content: ByteArray, mimeType: String): Boolean {
        val dir = documentsDirectory() ?: return false
        val path = dir + "/" + name
        val data = content.toNSData()
        if (!data.writeToFile(path, true)) return false
        val controller = UIActivityViewController(
            activityItems = listOf(NSURL.URLWithString("file://" + path)),
            applicationActivities = null,
        )
        val root = UIApplication.sharedApplication.keyWindow?.rootViewController
        root?.presentViewController(controller, true, null)
        return true
    }

    actual fun saveToDownloads(name: String, content: ByteArray): Boolean {
        val dir = documentsDirectory() ?: return false
        val data = content.toNSData()
        return data.writeToFile(dir + "/" + name, true)
    }

    private fun documentsDirectory(): String? =
        NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).firstOrNull() as? String
}
