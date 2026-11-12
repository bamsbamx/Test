package com.trailbook.platform

interface SecureStore {
    fun put(key: String, value: String)
    fun get(key: String): String?
    fun remove(key: String)
}

expect fun createSecureStore(): SecureStore
