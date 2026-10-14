// Made with Claude.

package com.swent.polyloop.model.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectivityRepositoryAndroidTest {

  private val network = mockk<Network>()
  private val callback = slot<ConnectivityManager.NetworkCallback>()
  private val connectivityManager =
      mockk<ConnectivityManager>(relaxUnitFun = true) {
        every { registerDefaultNetworkCallback(capture(callback)) } just Runs
      }
  private val context =
      mockk<Context> {
        every { getSystemService(ConnectivityManager::class.java) } returns connectivityManager
      }

  /** The phone starts with a default network, which has internet or not. */
  private fun startWithNetwork(hasInternet: Boolean) {
    val capabilities =
        mockk<NetworkCapabilities> {
          every { hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns hasInternet
        }
    every { connectivityManager.activeNetwork } returns network
    every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
  }

  /** Collects isOnline into [values] until the returned job is cancelled. */
  private fun TestScope.collect(values: MutableList<Boolean>): Job {
    val repository = ConnectivityRepositoryAndroid(context)
    return launch(UnconfinedTestDispatcher(testScheduler)) { repository.isOnline.toList(values) }
  }

  // ---------- initial state ----------

  @Test
  fun emitsOnlineWhenThePhoneStartsWithInternet() = runTest {
    startWithNetwork(hasInternet = true)
    val values = mutableListOf<Boolean>()

    val job = collect(values)

    assertEquals(listOf(true), values)
    job.cancel()
  }

  @Test
  fun emitsOfflineWhenThePhoneStartsWithoutNetwork() = runTest {
    every { connectivityManager.activeNetwork } returns null
    every { connectivityManager.getNetworkCapabilities(null) } returns null
    val values = mutableListOf<Boolean>()

    val job = collect(values)

    assertEquals(listOf(false), values)
    job.cancel()
  }

  @Test
  fun emitsOfflineWhenTheNetworkHasNoInternet() = runTest {
    startWithNetwork(hasInternet = false)
    val values = mutableListOf<Boolean>()

    val job = collect(values)

    assertEquals(listOf(false), values)
    job.cancel()
  }

  // ---------- changes ----------

  @Test
  fun emitsOfflineWhenTheNetworkIsLostAndOnlineWhenItComesBack() = runTest {
    startWithNetwork(hasInternet = true)
    val values = mutableListOf<Boolean>()
    val job = collect(values)

    callback.captured.onLost(network)
    callback.captured.onAvailable(network)

    assertEquals(listOf(true, false, true), values)
    job.cancel()
  }

  @Test
  fun doesNotRepeatTheSameState() = runTest {
    startWithNetwork(hasInternet = true)
    val values = mutableListOf<Boolean>()
    val job = collect(values)

    // Android calls onAvailable right after registering when a network is already up.
    callback.captured.onAvailable(network)

    assertEquals(listOf(true), values)
    job.cancel()
  }

  // ---------- cleanup ----------

  @Test
  fun unregistersTheCallbackWhenCollectionStops() = runTest {
    startWithNetwork(hasInternet = true)
    val job = collect(mutableListOf())

    job.cancel()

    verify(exactly = 1) { connectivityManager.unregisterNetworkCallback(callback.captured) }
  }
}
