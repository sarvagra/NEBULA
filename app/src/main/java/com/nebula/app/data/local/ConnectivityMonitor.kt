package com.nebula.app.data.local

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether this device currently has a usable network.
 *
 * Chat needed this for one reason: without it, a message typed on a plane sat in
 * "Sending…" until an HTTP connect timeout expired, which reads as a frozen app rather
 * than a message that did not go out. With it, the send fails in the same frame the
 * user taps, and the bubble carries an honest failure marker.
 *
 * A missing network is reported immediately; a network that exists but has not yet
 * validated (captive portal, DNS still resolving) is optimistically treated as online,
 * because guessing "offline" there would block sends that would have worked.
 */
object ConnectivityMonitor {

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    @Volatile private var started = false

    fun start(context: Context) {
        if (started) return
        started = true
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return
        _isOnline.value = currentlyOnline(manager)
        try {
            manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _isOnline.value = true
                }

                override fun onLost(network: Network) {
                    // Another network may still be up (Wi-Fi drops as mobile takes over).
                    _isOnline.value = currentlyOnline(manager)
                }

                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    _isOnline.value = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                }
            })
        } catch (_: Exception) {
            // A device that refuses the callback keeps the optimistic default; the
            // network attempt itself still produces a real answer.
        }
    }

    /** Read by the send path, which must not block on a network call to find out. */
    fun isOnlineNow(): Boolean = _isOnline.value

    private fun currentlyOnline(manager: ConnectivityManager): Boolean = try {
        val network = manager.activeNetwork
        val capabilities = network?.let { manager.getNetworkCapabilities(it) }
        capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    } catch (_: Exception) {
        true
    }
}
