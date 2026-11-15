package com.vivescriptsolutions.roomready.data.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live connectivity state for the offline-proof banner. Registers a
 * [ConnectivityManager.NetworkCallback] once — no polling. Requires the
 * normal-level ACCESS_NETWORK_STATE permission (no Play declaration needed).
 */
class ConnectivityMonitor(context: Context) {
  private val connectivityManager =
    context.applicationContext.getSystemService(ConnectivityManager::class.java)

  private val _isOnline = MutableStateFlow(currentOnline())
  val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

  private val callback =
    object : ConnectivityManager.NetworkCallback() {
      override fun onAvailable(network: Network) {
        _isOnline.value = currentOnline()
      }

      override fun onLost(network: Network) {
        _isOnline.value = currentOnline()
      }

      override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
        _isOnline.value = currentOnline()
      }
    }

  private var registered = false

  @Synchronized
  fun start() {
    if (registered) return
    connectivityManager.registerDefaultNetworkCallback(callback)
    _isOnline.value = currentOnline()
    registered = true
  }

  @Synchronized
  fun stop() {
    if (!registered) return
    connectivityManager.unregisterNetworkCallback(callback)
    registered = false
  }

  private fun currentOnline(): Boolean {
    val network = connectivityManager.activeNetwork ?: return false
    val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
  }
}
