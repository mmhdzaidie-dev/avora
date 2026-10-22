package com.zai.avora.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NetworkMonitor private constructor(context: Context) {

    private val cm = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val initial = currentlyOnline()
    private val _isOnline = MutableStateFlow(initial)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _isOffline = MutableStateFlow(!initial)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    private fun publish() {
        val online = validNetworks.isNotEmpty()
        _isOnline.value = online
        _isOffline.value = !online
    }

    private val validNetworks = HashSet<Network>()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            synchronized(validNetworks) {
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                    validNetworks.add(network)
                } else {
                    validNetworks.remove(network)
                }
                publish()
            }
        }

        override fun onLost(network: Network) {
            synchronized(validNetworks) {
                validNetworks.remove(network)
                publish()
            }
        }
    }

    init {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, callback)
        } catch (_: Exception) {
        }
    }

    private fun currentlyOnline(): Boolean {
        return try {
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            caps != null &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (_: Exception) {
            true
        }
    }

    companion object {
        @Volatile private var instance: NetworkMonitor? = null

        fun init(context: Context): NetworkMonitor =
            instance ?: synchronized(this) {
                instance ?: NetworkMonitor(context).also { instance = it }
            }

        fun getInstance(): NetworkMonitor =
            instance ?: error("NetworkMonitor.init() must be called first")
    }
}
