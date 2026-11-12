package com.trailbook.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.coroutines.resume

actual class FilePicker actual constructor() {
    actual suspend fun openFile(): PickedFile? = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            SwingUtilities.invokeLater {
                val chooser = JFileChooser()
                chooser.fileFilter = FileNameExtensionFilter(
                    "Track files (GPX, KML, KMZ, TCX, CSV, FIT)",
                    "gpx", "kml", "kmz", "tcx", "csv", "fit"
                )
                val result = chooser.showOpenDialog(null)
                if (result == JFileChooser.APPROVE_OPTION) {
                    val file = chooser.selectedFile
                    continuation.resume(PickedFile(file.name, file.readBytes()))
                } else {
                    continuation.resume(null)
                }
            }
        }
    }

    actual suspend fun saveFile(defaultName: String, content: ByteArray): Boolean = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            SwingUtilities.invokeLater {
                val chooser = JFileChooser()
                chooser.selectedFile = File(defaultName)
                val result = chooser.showSaveDialog(null)
                if (result == JFileChooser.APPROVE_OPTION) {
                    try {
                        chooser.selectedFile.writeBytes(content)
                        continuation.resume(true)
                    } catch (e: Exception) {
                        continuation.resume(false)
                    }
                } else {
                    continuation.resume(null)
                }
            }
        } ?: false
    }
}
