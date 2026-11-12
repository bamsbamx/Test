package com.trailbook.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecItemNotFound
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlock
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

@OptIn(ExperimentalForeignApi::class)
class IosSecureStore : SecureStore {
    private val service = "com.trailbook.app.secure"

    private fun baseQuery(key: String): Map<Any?, Any?> = mapOf(
        kSecClass to kSecClassGenericPassword,
        kSecAttrService to service,
        kSecAttrAccount to key,
    )

    override fun put(key: String, value: String) {
        SecItemDelete(baseQuery(key))
        val data = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        val add = baseQuery(key) + mapOf(
            kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlock,
            kSecValueData to data,
        )
        SecItemAdd(add, null)
    }

    override fun get(key: String): String? {
        val query = baseQuery(key) + mapOf(
            kSecReturnData to kCFBooleanTrue,
        )
        val data = memScoped {
            val result = alloc<CFTypeRefVar>()
            result.value = null
            val status = SecItemCopyMatching(query, result.ptr)
            if (status == errSecItemNotFound || status != 0) {
                null
            } else {
                result.value as? NSData
            }
        } ?: return null
        return NSString.create(data, NSUTF8StringEncoding)?.toString()
    }

    override fun remove(key: String) {
        SecItemDelete(baseQuery(key))
    }
}

actual fun createSecureStore(): SecureStore = IosSecureStore()
