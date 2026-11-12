package com.trailbook

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.trailbook.app.App
import com.trailbook.platform.AndroidContext
import com.trailbook.platform.CurrentActivityHolder
import com.trailbook.platform.PermissionRequestRegistry

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidContext.init(this)
        CurrentActivityHolder.activity = this
        setContent { App() }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        for (i in permissions.indices) {
            PermissionRequestRegistry.onResult(permissions[i], grantResults.getOrNull(i) == android.content.pm.PackageManager.PERMISSION_GRANTED)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            7001 -> com.trailbook.platform.FileRequestRegistry.onOpenResult(
                if (resultCode == RESULT_OK) data?.data else null, this
            )
            7002 -> com.trailbook.platform.FileRequestRegistry.onSaveResult(
                if (resultCode == RESULT_OK) data?.data else null, this, pendingSaveContent
            )
        }
    }

    companion object {
        var pendingSaveContent: ByteArray = ByteArray(0)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (CurrentActivityHolder.activity === this) CurrentActivityHolder.activity = null
    }
}
