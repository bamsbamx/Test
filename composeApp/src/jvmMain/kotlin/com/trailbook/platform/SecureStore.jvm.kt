package com.trailbook.platform

import java.io.File
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class JvmSecureStore : SecureStore {
    private val dir = File(System.getProperty("user.home"), ".trailbook").apply { mkdirs() }
    private val keyFile = File(dir, ".secure.key")

    private fun key(): SecretKey {
        if (keyFile.exists()) {
            val raw = Base64.getDecoder().decode(keyFile.readText())
            return SecretKeySpec(raw, "AES")
        }
        val generator = KeyGenerator.getInstance("AES")
        generator.init(256)
        val key = generator.generateKey()
        keyFile.writeText(Base64.getEncoder().encodeToString(key.encoded))
        keyFile.setReadable(false, false)
        keyFile.setReadable(true, true)
        return key
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, key(), GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(plain.encodeToByteArray())
        return Base64.getEncoder().encodeToString(iv + encrypted)
    }

    private fun decrypt(stored: String): String? = try {
        val data = Base64.getDecoder().decode(stored)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(0, 12)))
        cipher.doFinal(data.copyOfRange(12, data.size)).decodeToString()
    } catch (e: Exception) {
        null
    }

    private val storeFile: File get() = File(dir, "secure_store.bin")

    private fun loadAll(): MutableMap<String, String> {
        if (!storeFile.exists()) return mutableMapOf()
        val map = mutableMapOf<String, String>()
        storeFile.readLines().forEach { line ->
            val idx = line.indexOf('=')
            if (idx > 0) {
                val k = line.substring(0, idx)
                val v = decrypt(line.substring(idx + 1))
                if (v != null) map[k] = v
            }
        }
        return map
    }

    private fun saveAll(map: Map<String, String>) {
        storeFile.writeText(map.entries.joinToString("\n") { "${it.key}=${encrypt(it.value)}" })
        storeFile.setReadable(false, false)
        storeFile.setReadable(true, true)
    }

    override fun put(key: String, value: String) {
        val all = loadAll()
        all[key] = value
        saveAll(all)
    }

    override fun get(key: String): String? = loadAll()[key]

    override fun remove(key: String) {
        val all = loadAll()
        all.remove(key)
        saveAll(all)
    }
}

actual fun createSecureStore(): SecureStore = JvmSecureStore()
