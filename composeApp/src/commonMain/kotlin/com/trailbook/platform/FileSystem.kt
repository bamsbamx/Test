package com.trailbook.platform

interface AppFileSystem {
    fun dataDirectory(): String
    fun readText(path: String): String?
    fun writeText(path: String, content: String)
    fun readBytes(path: String): ByteArray?
    fun writeBytes(path: String, content: ByteArray)
    fun delete(path: String): Boolean
    fun listFiles(directory: String): List<String>
    fun exists(path: String): Boolean
}

expect fun createAppFileSystem(): AppFileSystem
