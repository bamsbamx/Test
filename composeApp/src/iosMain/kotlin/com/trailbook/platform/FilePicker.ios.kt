package com.trailbook.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CompletableDeferred
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.writeToFile
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIApplication
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
private class IosPickerDelegate : NSObject(), UIDocumentPickerDelegateProtocol {
    val picked = CompletableDeferred<NSURL?>()

    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentAtURL: NSURL) {
        picked.complete(didPickDocumentAtURL)
    }

    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        picked.complete(didPickDocumentsAtURLs.firstOrNull() as? NSURL)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        picked.complete(null)
    }
}

@OptIn(ExperimentalForeignApi::class)
actual class FilePicker actual constructor() {
    actual suspend fun openFile(): PickedFile? {
        val root = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return null
        val delegate = IosPickerDelegate()
        val controller = UIDocumentPickerViewController(
            forOpeningContentTypes = listOf(platform.UniformTypeIdentifiers.UTType.data),
            asCopy = true,
        )
        controller.delegate = delegate
        root.presentViewController(controller, true, null)
        val url = delegate.picked.await() ?: return null
        val data = platform.Foundation.NSData.dataWithContentsOfURL(url) ?: return null
        val name = url.lastPathComponent ?: "imported.gpx"
        return PickedFile(name, data.toByteArray())
    }

    actual suspend fun saveFile(defaultName: String, content: ByteArray): Boolean {
        val dir = documentsDirectory() ?: return false
        return content.toNSData().writeToFile(dir + "/" + defaultName, true)
    }

    private fun documentsDirectory(): String? =
        NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).firstOrNull() as? String
}
