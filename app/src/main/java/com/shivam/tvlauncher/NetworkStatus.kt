package com.shivam.tvlauncher

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

enum class NetworkKind { Wifi, Ethernet, Other, None }

/** [level] is 0–3 Wi-Fi bars, or 3 when the platform doesn't report signal strength. */
data class NetworkStatus(val kind: NetworkKind, val level: Int = 3, val online: Boolean = false)

/**
 * The default network as the system sees it. Only reads connectivity state (ACCESS_NETWORK_STATE);
 * the launcher itself never opens a connection.
 */
fun networkStatusFlow(context: Context): Flow<NetworkStatus> = callbackFlow {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            trySend(caps.toStatus())
        }

        override fun onLost(network: Network) {
            trySend(NetworkStatus(NetworkKind.None))
        }
    }
    trySend(cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }?.toStatus() ?: NetworkStatus(NetworkKind.None))
    cm.registerDefaultNetworkCallback(callback)
    awaitClose { cm.unregisterNetworkCallback(callback) }
}.conflate().distinctUntilChanged()

private fun NetworkCapabilities.toStatus(): NetworkStatus {
    val kind = when {
        hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkKind.Ethernet
        hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkKind.Wifi
        else -> NetworkKind.Other
    }
    // RSSI in dBm for Wi-Fi on API 29+; UNSPECIFIED (Int.MIN_VALUE) when not reported.
    val rssi = signalStrength
    val level = when {
        kind != NetworkKind.Wifi || rssi == NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED -> 3
        rssi >= -60 -> 3
        rssi >= -72 -> 2
        else -> 1
    }
    return NetworkStatus(kind, level, online = hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
}
