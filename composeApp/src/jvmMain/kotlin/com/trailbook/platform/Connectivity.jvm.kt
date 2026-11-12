package com.trailbook.platform

import java.net.InetSocketAddress
import java.net.Socket

actual class NetworkMonitor actual constructor() : com.trailbook.core.net.ConnectivityMonitor {
    private var lastCheck = 0L
    private var lastResult = false

    override fun isOnline(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastCheck < 5000) return lastResult
        lastCheck = now
        lastResult = try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("1.1.1.1", 53), 1500)
                true
            }
        } catch (e: Exception) {
            false
        }
        return lastResult
    }
}
