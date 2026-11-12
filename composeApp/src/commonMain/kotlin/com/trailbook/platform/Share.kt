package com.trailbook.platform

expect class FileSharer() {
    fun shareFile(name: String, content: ByteArray, mimeType: String): Boolean
    fun saveToDownloads(name: String, content: ByteArray): Boolean
}
