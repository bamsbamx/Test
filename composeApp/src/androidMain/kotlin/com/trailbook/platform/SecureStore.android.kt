package com.trailbook.platform

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidSecureStore : SecureStore {
    private val prefs = AndroidContext.context.getSharedPreferences("trailbook_secure", android.content.Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey("trailbook_master", null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder("trailbook_master", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
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

    override fun put(key: String, value: String) {
        prefs.edit().putString(key, encrypt(value)).apply()
    }

    override fun get(key: String): String? = prefs.getString(key, null)?.let { decrypt(it) }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
}

actual fun createSecureStore(): SecureStore = AndroidSecureStore()
