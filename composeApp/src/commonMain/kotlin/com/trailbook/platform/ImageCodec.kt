package com.trailbook.platform

import androidx.compose.ui.graphics.ImageBitmap

expect object ImageCodec {
    fun decode(bytes: ByteArray): ImageBitmap?
    fun createImage(width: Int, height: Int): ImageBitmap?
    fun encodePng(bitmap: ImageBitmap): ByteArray?
}
