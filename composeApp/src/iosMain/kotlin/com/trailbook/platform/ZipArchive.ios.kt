package com.trailbook.core.format

actual object ZipArchive {
    actual fun zip(entries: Map<String, ByteArray>): ByteArray = ZipStoreWriter.write(entries)

    actual fun unzip(data: ByteArray): Map<String, ByteArray> = ZipStoreReader.read(data)
}
