package com.trailbook.data

import com.trailbook.platform.AppFileSystem
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

class JsonFileStore(private val fs: AppFileSystem, private val baseDir: String) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }

    fun <T> readList(file: String, serializer: KSerializer<List<T>>): List<T> {
        val text = fs.readText("$baseDir/$file") ?: return emptyList()
        return try {
            json.decodeFromString(serializer, text)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun <T> writeList(file: String, serializer: KSerializer<List<T>>, items: List<T>) {
        fs.writeText("$baseDir/$file", json.encodeToString(serializer, items))
    }

    fun <V> readValue(file: String, serializer: KSerializer<V>): V? {
        val text = fs.readText("$baseDir/$file") ?: return null
        return try {
            json.decodeFromString(serializer, text)
        } catch (e: Exception) {
            null
        }
    }

    fun <V> writeValue(file: String, serializer: KSerializer<V>, value: V) {
        fs.writeText("$baseDir/$file", json.encodeToString(serializer, value))
    }

    fun delete(file: String) {
        fs.delete("$baseDir/$file")
    }
}
