package com.trailbook.platform

import java.io.File

class JvmFileSystem : AppFileSystem {
    private val base: File = File(System.getProperty("user.home"), ".trailbook").apply { mkdirs() }

    override fun dataDirectory(): String = base.absolutePath

    private fun resolve(path: String): File {
        val f = File(path)
        return if (f.isAbsolute) f else File(base, path)
    }

    override fun readText(path: String): String? {
        val f = resolve(path)
        return if (f.exists()) f.readText() else null
    }

    override fun writeText(path: String, content: String) {
        resolve(path).apply { parentFile?.mkdirs() }.writeText(content)
    }

    override fun readBytes(path: String): ByteArray? {
        val f = resolve(path)
        return if (f.exists()) f.readBytes() else null
    }

    override fun writeBytes(path: String, content: ByteArray) {
        resolve(path).apply { parentFile?.mkdirs() }.writeBytes(content)
    }

    override fun delete(path: String): Boolean = resolve(path).delete()

    override fun listFiles(directory: String): List<String> {
        val d = resolve(directory)
        return d.listFiles()?.map { it.absolutePath } ?: emptyList()
    }

    override fun exists(path: String): Boolean = resolve(path).exists()
}

actual fun createAppFileSystem(): AppFileSystem = JvmFileSystem()
