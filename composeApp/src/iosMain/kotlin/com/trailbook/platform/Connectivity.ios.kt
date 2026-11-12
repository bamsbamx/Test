package com.trailbook.platform

import com.trailbook.core.net.ConnectivityMonitor
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import platform.posix.AF_INET
import platform.posix.SOCK_STREAM
import platform.posix.connect
import platform.posix.socket
import platform.posix.sockaddr_in
import platform.posix.htons
import platform.posix.inet_addr
import kotlinx.cinterop.convert

@OptIn(ExperimentalForeignApi::class)
actual class NetworkMonitor actual constructor() : ConnectivityMonitor {
    private var lastCheck = 0L
    private var lastResult = false

    override fun isOnline(): Boolean {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        if (now - lastCheck < 5000) return lastResult
        lastCheck = now
        lastResult = memScoped {
            val fd = socket(AF_INET, SOCK_STREAM, 0)
            if (fd < 0) return@memScoped false
            val addr = alloc<sockaddr_in>()
            platform.posix.memset(addr.ptr, 0, sizeOfSockaddrIn())
            addr.sin_family = AF_INET.convert()
            addr.sin_port = htons(53u.convert())
            addr.sin_addr.s_addr = inet_addr("1.1.1.1")
            val result = connect(fd, addr.ptr, sizeOfSockaddrIn().convert()) == 0
            platform.posix.close(fd)
            result
        }
        return lastResult
    }

    private fun sizeOfSockaddrIn(): Int = 16
}
