package com.trailbook.platform

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine

class AndroidPermissionManager : PermissionManager {
    override fun status(type: PermissionType): PermissionStatus = when (type) {
        PermissionType.LOCATION_FINE ->
            if (ContextCompat.checkSelfPermission(AndroidContext.context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                PermissionStatus.GRANTED else PermissionStatus.NOT_DETERMINED
        PermissionType.LOCATION_BACKGROUND ->
            if (android.os.Build.VERSION.SDK_INT >= 29 &&
                ContextCompat.checkSelfPermission(AndroidContext.context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
            ) PermissionStatus.GRANTED else if (android.os.Build.VERSION.SDK_INT >= 29) PermissionStatus.NOT_DETERMINED else PermissionStatus.GRANTED
        PermissionType.CAMERA ->
            if (ContextCompat.checkSelfPermission(AndroidContext.context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                PermissionStatus.GRANTED else PermissionStatus.NOT_DETERMINED
    }

    override suspend fun request(type: PermissionType): PermissionStatus {
        val activity = CurrentActivityHolder.activity ?: return PermissionStatus.UNAVAILABLE
        val permission = when (type) {
            PermissionType.LOCATION_FINE -> Manifest.permission.ACCESS_FINE_LOCATION
            PermissionType.LOCATION_BACKGROUND -> Manifest.permission.ACCESS_BACKGROUND_LOCATION
            PermissionType.CAMERA -> Manifest.permission.CAMERA
        }
        if (ContextCompat.checkSelfPermission(AndroidContext.context, permission) == PackageManager.PERMISSION_GRANTED) {
            return PermissionStatus.GRANTED
        }
        return suspendCancellableCoroutine { cont ->
            PermissionRequestRegistry.register(permission) { granted ->
                if (cont.isActive) cont.resumeWith(Result.success(if (granted) PermissionStatus.GRANTED else PermissionStatus.DENIED))
            }
            androidx.core.app.ActivityCompat.requestPermissions(activity, arrayOf(permission), PERMISSION_REQUEST_CODE)
        }
    }

    companion object {
        const val PERMISSION_REQUEST_CODE = 4201
    }
}

object PermissionRequestRegistry {
    private val callbacks = mutableMapOf<String, (Boolean) -> Unit>()

    fun register(permission: String, callback: (Boolean) -> Unit) {
        callbacks[permission] = callback
    }

    fun onResult(permission: String, granted: Boolean) {
        callbacks.remove(permission)?.invoke(granted)
    }
}

object CurrentActivityHolder {
    var activity: android.app.Activity? = null
}
