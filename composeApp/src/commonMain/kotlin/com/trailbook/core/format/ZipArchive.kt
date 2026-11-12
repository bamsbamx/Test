package com.trailbook.core.format

expect object ZipArchive {
    fun zip(entries: Map<String, ByteArray>): ByteArray
    fun unzip(data: ByteArray): Map<String, ByteArray>
}
