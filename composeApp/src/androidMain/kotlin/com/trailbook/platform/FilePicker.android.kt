package com.trailbook.platform

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FileRequestRegistry {
    private var openCallback: ((PickedFile?) -> Unit)? = null
    private var saveCallback: ((Boolean) -> Unit)? = null

    fun onOpenResult(uri: Uri?, activity: Activity?) {
        val callback = openCallback
        openCallback = null
        if (uri == null || activity == null) {
            callback?.invoke(null)
            return
        }
        val name = queryName(activity, uri) ?: "imported.gpx"
        val bytes = activity.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        callback?.invoke(if (bytes != null) PickedFile(name, bytes) else null)
    }

    fun onSaveResult(uri: Uri?, activity: Activity?, content: ByteArray) {
        val callback = saveCallback
        saveCallback = null
        if (uri == null || activity == null) {
            callback?.invoke(false)
            return
        }
        val ok = try {
            activity.contentResolver.openOutputStream(uri)?.use { it.write(content) } != null
        } catch (e: Exception) {
            false
        }
        callback?.invoke(ok)
    }

    private fun queryName(activity: Activity, uri: Uri): String? =
        activity.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }

    fun launchOpen(activity: Activity, callback: (PickedFile?) -> Unit): Boolean {
        openCallback = callback
        return try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            activity.startActivityForResult(intent, 7001)
            true
        } catch (e: Exception) {
            openCallback = null
            false
        }
    }

    fun launchSave(activity: Activity, defaultName: String, content: ByteArray, callback: (Boolean) -> Unit): Boolean {
        saveCallback = callback
        return try {
            com.trailbook.MainActivity.pendingSaveContent = content
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_TITLE, defaultName)
            }
            activity.startActivityForResult(intent, 7002)
            true
        } catch (e: Exception) {
            saveCallback = null
            false
        }
    }
}

actual class FilePicker actual constructor() {
    actual suspend fun openFile(): PickedFile? = withContext(Dispatchers.Main) {
        val activity = com.trailbook.platform.CurrentActivityHolder.activity
            ?: return@withContext null
        val deferred = CompletableDeferred<PickedFile?>()
        val launched = FileRequestRegistry.launchOpen(activity) { deferred.complete(it) }
        if (launched) deferred.await() else null
    }

    actual suspend fun saveFile(defaultName: String, content: ByteArray): Boolean = withContext(Dispatchers.Main) {
        val activity = com.trailbook.platform.CurrentActivityHolder.activity
            ?: return@withContext false
        val deferred = CompletableDeferred<Boolean>()
        val launched = FileRequestRegistry.launchSave(activity, defaultName, content) { deferred.complete(it) }
        if (launched) deferred.await() else false
    }
}
