package com.trailbook.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

actual object ImageCodec {
    actual fun decode(bytes: ByteArray): ImageBitmap? = try {
        Image.makeFromEncoded(bytes).toComposeImageBitmap()
    } catch (e: Exception) {
        null
    }

    actual fun createImage(width: Int, height: Int): ImageBitmap? = try {
        val surface = org.jetbrains.skia.Surface.makeRasterN32Premul(width, height)
        surface.canvas.clear(org.jetbrains.skia.Color.WHITE)
        surface.makeImageSnapshot().toComposeImageBitmap()
    } catch (e: Exception) {
        null
    }

    actual fun encodePng(bitmap: ImageBitmap): ByteArray? = try {
        val surface = org.jetbrains.skia.Surface.makeRasterN32Premul(bitmap.width, bitmap.height)
        surface.canvas.drawImage(Image.makeFromBitmap(bitmap.asSkiaBitmap()), 0f, 0f)
        surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)?.bytes
    } catch (e: Exception) {
        null
    }
}
