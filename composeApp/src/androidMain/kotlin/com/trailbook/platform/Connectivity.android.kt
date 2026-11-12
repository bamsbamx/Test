package com.trailbook.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.trailbook.core.net.ConnectivityMonitor

actual class NetworkMonitor actual constructor() : ConnectivityMonitor {
    override fun isOnline(): Boolean = try {
        val cm = AndroidContext.context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    } catch (e: Exception) {
        false
    }
}
