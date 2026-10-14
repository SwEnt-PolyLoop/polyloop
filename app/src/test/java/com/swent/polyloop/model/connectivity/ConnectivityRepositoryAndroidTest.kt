// Made with Claude.

package com.swent.polyloop.model.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import io.mockk.Ordering
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
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectivityRepositoryAndroidTest {

  private val network = mockk<Network>()
  private val otherNetwork = mockk<Network>()
  private val callback = slot<ConnectivityManager.NetworkCallback>()
  private val connectivityManager =
      mockk<ConnectivityManager>(relaxUnitFun = true) {
        every { registerDefaultNetworkCallback(capture(callback)) } just Runs
      }
  private val context =
      mockk<Context> {
        every { getSystemService(ConnectivityManager::class.java) } returns connectivityManager
      }

  private fun capabilities(hasInternet: Boolean = true, validated: Boolean = true) =
      mockk<NetworkCapabilities> {
        every { hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns hasInternet
        every { hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns validated
      }

  /** The phone starts with a default network, with or without validated internet. */
  private fun startWithNetwork(hasInternet: Boolean = true, validated: Boolean = true) {
    every { connectivityManager.activeNetwork } returns network
    every { connectivityManager.getNetworkCapabilities(network) } returns
        capabilities(hasInternet, validated)
  }

  private fun TestScope.newRepository() =
      ConnectivityRepositoryAndroid(
          context,
          backgroundScope + UnconfinedTestDispatcher(testScheduler),
      )

  /** Collects isOnline into [values] until the returned job is cancelled. */
  private fun TestScope.collect(
      values: MutableList<Boolean>,
      repository: ConnectivityRepositoryAndroid = newRepository(),
  ): Job = launch(UnconfinedTestDispatcher(testScheduler)) { repository.isOnline.toList(values) }

  // ---------- initial state ----------

  @Test
  fun emitsOnlineWhenThePhoneStartsWithValidatedInternet() = runTest {
    startWithNetwork()
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

  @Test
  fun emitsOfflineBehindACaptivePortal() = runTest {
    startWithNetwork(validated = false)
    val values = mutableListOf<Boolean>()

    val job = collect(values)

    assertEquals(listOf(false), values)
    job.cancel()
  }

  @Test
  fun readsTheCurrentStateAfterRegisteringTheCallback() = runTest {
    startWithNetwork()

    val job = collect(mutableListOf())

    verify(ordering = Ordering.ORDERED) {
      connectivityManager.registerDefaultNetworkCallback(any())
      connectivityManager.activeNetwork
    }
    job.cancel()
  }

  // ---------- changes ----------

  @Test
  fun emitsOfflineWhenTheNetworkIsLostAndOnlineWhenItComesBack() = runTest {
    startWithNetwork()
    val values = mutableListOf<Boolean>()
    val job = collect(values)

    callback.captured.onLost(network)
    callback.captured.onCapabilitiesChanged(network, capabilities())

    assertEquals(listOf(true, false, true), values)
    job.cancel()
  }

  @Test
  fun emitsOnlineOnceACaptivePortalIsSignedIn() = runTest {
    startWithNetwork(validated = false)
    val values = mutableListOf<Boolean>()
    val job = collect(values)

    callback.captured.onCapabilitiesChanged(network, capabilities())

    assertEquals(listOf(false, true), values)
    job.cancel()
  }

  @Test
  fun ignoresALateLossOfTheReplacedNetwork() = runTest {
    startWithNetwork()
    val values = mutableListOf<Boolean>()
    val job = collect(values)
    callback.captured.onCapabilitiesChanged(network, capabilities())

    // Wi-Fi to mobile data: the new network arrives, then the old one is reported lost.
    callback.captured.onCapabilitiesChanged(otherNetwork, capabilities())
    callback.captured.onLost(network)

    assertEquals(listOf(true), values)
    job.cancel()
  }

  @Test
  fun doesNotRepeatTheSameState() = runTest {
    startWithNetwork()
    val values = mutableListOf<Boolean>()
    val job = collect(values)

    // Android reports the default network right after registering when one is already up.
    callback.captured.onCapabilitiesChanged(network, capabilities())

    assertEquals(listOf(true), values)
    job.cancel()
  }

  // ---------- sharing and cleanup ----------

  @Test
  fun collectorsShareOneCallback() = runTest {
    startWithNetwork()
    val repository = newRepository()

    val first = collect(mutableListOf(), repository)
    val second = collect(mutableListOf(), repository)

    verify(exactly = 1) { connectivityManager.registerDefaultNetworkCallback(any()) }
    first.cancel()
    second.cancel()
  }

  @Test
  fun aNewCollectorDoesNotSeeTheStateFromBeforeCollectionStopped() = runTest {
    startWithNetwork()
    val repository = newRepository()
    val first = collect(mutableListOf(), repository)
    first.cancel()
    runCurrent()

    every { connectivityManager.activeNetwork } returns null
    every { connectivityManager.getNetworkCapabilities(null) } returns null
    val values = mutableListOf<Boolean>()
    val second = collect(values, repository)

    assertEquals(listOf(false), values)
    second.cancel()
  }

  @Test
  fun unregistersTheCallbackWhenCollectionStops() = runTest {
    startWithNetwork()
    val job = collect(mutableListOf())

    job.cancel()
    runCurrent()

    verify(exactly = 1) { connectivityManager.unregisterNetworkCallback(callback.captured) }
  }
}
