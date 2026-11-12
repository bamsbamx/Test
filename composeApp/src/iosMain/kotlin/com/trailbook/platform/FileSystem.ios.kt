package com.trailbook.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentationDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSString
import platform.Foundation.writeStringToFile

@OptIn(ExperimentalForeignApi::class)
class IosFileSystem : AppFileSystem {
    private val fileManager = NSFileManager.defaultManager

    private val baseDir: String
        get() {
            val paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
            return (paths.firstOrNull() as? NSString)?.toString() ?: "/"
        }

    private fun resolve(path: String): String =
        if (path.startsWith("/")) path else baseDir + "/" + path

    override fun dataDirectory(): String = baseDir

    override fun readText(path: String): String? {
        val p = resolve(path)
        if (!fileManager.fileExistsAtPath(p)) return null
        return fileManager.contentsAtPath(p)?.let { NSString.create(it, encoding = platform.Foundation.NSUTF8StringEncoding)?.toString() }
    }

    override fun writeText(path: String, content: String) {
        val p = resolve(path)
        val dir = p.substringBeforeLast('/')
        if (dir.isNotEmpty()) fileManager.createDirectoryAtPath(dir, true, null, null)
        (content as NSString).writeToFile(p, atomically = true, encoding = platform.Foundation.NSUTF8StringEncoding, error = null)
    }

    override fun readBytes(path: String): ByteArray? {
        val p = resolve(path)
        if (!fileManager.fileExistsAtPath(p)) return null
        val data = fileManager.contentsAtPath(p) ?: return null
        return data.toByteArray()
    }

    override fun writeBytes(path: String, content: ByteArray) {
        val p = resolve(path)
        val dir = p.substringBeforeLast('/')
        if (dir.isNotEmpty()) fileManager.createDirectoryAtPath(dir, true, null, null)
        content.toNSData().writeToFile(p, atomically = true)
    }

    override fun delete(path: String): Boolean =
        fileManager.removeItemAtPath(resolve(path), null)

    override fun listFiles(directory: String): List<String> {
        val p = resolve(directory)
        return fileManager.contentsOfDirectoryAtPath(p, null)?.map { p + "/" + it } ?: emptyList()
    }

    override fun exists(path: String): Boolean = fileManager.fileExistsAtPath(resolve(path))
}

actual fun createAppFileSystem(): AppFileSystem = IosFileSystem()
