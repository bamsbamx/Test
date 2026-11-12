package com.trailbook.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream

actual object ImageCodec {
    actual fun decode(bytes: ByteArray): ImageBitmap? = try {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    } catch (e: Exception) {
        null
    }

    actual fun createImage(width: Int, height: Int): ImageBitmap? = try {
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
    } catch (e: Exception) {
        null
    }

    actual fun encodePng(bitmap: ImageBitmap): ByteArray? = try {
        val out = ByteArrayOutputStream()
        bitmap.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)
        out.toByteArray()
    } catch (e: Exception) {
        null
    }
}
