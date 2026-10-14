// Made with Claude.

package com.swent.polyloop.model.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.shareIn

/**
 * [ConnectivityRepository] backed by Android's [ConnectivityManager]. Follows the phone's default
 * network: online while it has internet that Android has validated (so a captive portal not yet
 * signed in counts as offline), offline once it is lost.
 *
 * All collectors share one network callback, registered while at least one of them collects.
 */
class ConnectivityRepositoryAndroid(
    context: Context,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : ConnectivityRepository {

  // Always present on a phone; getSystemService is only nullable for services a device may lack.
  private val connectivityManager =
      checkNotNull(context.getSystemService(ConnectivityManager::class.java))

  override val isOnline: Flow<Boolean> = callbackFlow {
    // The default network the callback last reported, to ignore a late onLost of a
    // network that has already been replaced (e.g. Wi-Fi to mobile data).
    var current: Network? = null

    val callback =
        object : ConnectivityManager.NetworkCallback() {
          override fun onCapabilitiesChanged(
              network: Network,
              capabilities: NetworkCapabilities,
          ) {
            current = network
            trySend(capabilities.hasValidatedInternet())
          }

          override fun onLost(network: Network) {
            if (current != null && network != current) return
            current = null
            trySend(false)
          }
        }
    connectivityManager.registerDefaultNetworkCallback(callback)
    // Read the current state only after registering, so a change in between is not
    // missed; a phone that starts offline would otherwise emit nothing.
    trySend(isCurrentlyOnline())

    awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
  }
      .distinctUntilChanged()
      // Drop the last state once nobody collects, so a new collector never sees a stale one.
      .shareIn(scope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), replay = 1)

  private fun isCurrentlyOnline(): Boolean =
      connectivityManager
          .getNetworkCapabilities(connectivityManager.activeNetwork)
          ?.hasValidatedInternet() == true

  private fun NetworkCapabilities.hasValidatedInternet(): Boolean =
      hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
          hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
