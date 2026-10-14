// Made with Claude.

package com.swent.polyloop.model.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * [ConnectivityRepository] backed by Android's [ConnectivityManager]. Follows the phone's default
 * network: online while there is one, offline once it is lost.
 */
class ConnectivityRepositoryAndroid(context: Context) : ConnectivityRepository {

  // Always present on a phone; getSystemService is only nullable for services a device may lack.
  private val connectivityManager =
      checkNotNull(context.getSystemService(ConnectivityManager::class.java))

  override val isOnline: Flow<Boolean> = callbackFlow {
    // The callback only fires on changes (and once if a network is already up), so send
    // the current state first: otherwise a phone that starts offline would emit nothing.
    trySend(isCurrentlyOnline())

    val callback =
        object : ConnectivityManager.NetworkCallback() {
          override fun onAvailable(network: Network) {
            trySend(true)
          }

          // When the default network switches (e.g. Wi-Fi to mobile data), Android calls
          // onAvailable for the new one; onLost only comes when no network is left.
          override fun onLost(network: Network) {
            trySend(false)
          }
        }
    connectivityManager.registerDefaultNetworkCallback(callback)

    awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
  }
      .distinctUntilChanged()

  private fun isCurrentlyOnline(): Boolean =
      connectivityManager
          .getNetworkCapabilities(connectivityManager.activeNetwork)
          ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
}
