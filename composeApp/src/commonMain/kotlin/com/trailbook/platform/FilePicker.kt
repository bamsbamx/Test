package com.trailbook.platform

class PickedFile(val name: String, val bytes: ByteArray)

expect class FilePicker() {
    suspend fun openFile(): PickedFile?
    suspend fun saveFile(defaultName: String, content: ByteArray): Boolean
}
