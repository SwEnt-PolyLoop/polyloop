// Made with Claude.

package com.swent.polyloop.model.location

import android.Manifest
import android.app.Application
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLocationManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocationRepositoryAndroidTest {

  private val context: Context = ApplicationProvider.getApplicationContext()
  private lateinit var shadowManager: ShadowLocationManager
  private val repository = LocationRepositoryAndroid(context)

  @Before
  fun setUp() {
    shadowOf(context as Application).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
    shadowManager = shadowOf(context.getSystemService(LocationManager::class.java))
  }

  /** A location [ageMinutes] old by the elapsed realtime clock (negative: in the future). */
  private fun location(
      provider: String,
      latitude: Double,
      ageMinutes: Long,
      time: Long = 0L,
  ): Location =
      Location(provider).apply {
        this.latitude = latitude
        longitude = 6.0
        this.time = time
        elapsedRealtimeNanos =
            SystemClock.elapsedRealtimeNanos() - TimeUnit.MINUTES.toNanos(ageMinutes)
      }

  private fun setLastKnown(provider: String, location: Location, enabled: Boolean = true) {
    shadowManager.setProviderEnabled(provider, enabled)
    shadowManager.simulateLocation(location)
  }

  @Test
  fun oldLastKnownLocationIsIgnored() = runTest {
    setLastKnown(
        LocationManager.NETWORK_PROVIDER,
        location(LocationManager.NETWORK_PROVIDER, 46.0, ageMinutes = 31),
    )
    assertNull(repository.currentPosition())
  }

  @Test
  fun lastKnownLocationAtTheLimitIsUsed() = runTest {
    setLastKnown(
        LocationManager.NETWORK_PROVIDER,
        location(LocationManager.NETWORK_PROVIDER, 46.0, ageMinutes = 30),
    )
    assertEquals(46.0, repository.currentPosition()?.latitude ?: Double.NaN, 0.0)
  }

  @Test
  fun recentLastKnownLocationIsUsed() = runTest {
    setLastKnown(
        LocationManager.NETWORK_PROVIDER,
        location(LocationManager.NETWORK_PROVIDER, 46.1, ageMinutes = 5),
    )
    assertEquals(46.1, repository.currentPosition()?.latitude ?: Double.NaN, 0.0)
  }

  @Test
  fun futureLastKnownLocationIsIgnored() = runTest {
    setLastKnown(
        LocationManager.NETWORK_PROVIDER,
        location(LocationManager.NETWORK_PROVIDER, 46.0, ageMinutes = -10),
    )
    assertNull(repository.currentPosition())
  }

  @Test
  fun newestOfTwoLastKnownLocationsIsUsedByElapsedRealtimeNotByTime() = runTest {
    // The network one is older by elapsed realtime but has the newer wall-clock time.
    setLastKnown(
        LocationManager.NETWORK_PROVIDER,
        location(LocationManager.NETWORK_PROVIDER, 46.2, ageMinutes = 20, time = 2_000L),
    )
    setLastKnown(
        LocationManager.FUSED_PROVIDER,
        location(LocationManager.FUSED_PROVIDER, 46.3, ageMinutes = 10, time = 1_000L),
    )
    assertEquals(46.3, repository.currentPosition()?.latitude ?: Double.NaN, 0.0)
  }

  @Test
  fun passiveLastKnownLocationIsUsedOnApi31() = runTest {
    setLastKnown(
        LocationManager.PASSIVE_PROVIDER,
        location(LocationManager.PASSIVE_PROVIDER, 46.4, ageMinutes = 5),
        enabled = false,
    )
    assertEquals(46.4, repository.currentPosition()?.latitude ?: Double.NaN, 0.0)
  }

  @Test
  @Config(sdk = [30])
  fun passiveLastKnownLocationIsIgnoredBeforeApi31() = runTest {
    setLastKnown(
        LocationManager.PASSIVE_PROVIDER,
        location(LocationManager.PASSIVE_PROVIDER, 46.4, ageMinutes = 5),
        enabled = false,
    )
    assertNull(repository.currentPosition())
  }

  @Test
  fun missingPermissionGivesNull() = runTest {
    shadowOf(context as Application).denyPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
    setLastKnown(
        LocationManager.NETWORK_PROVIDER,
        location(LocationManager.NETWORK_PROVIDER, 46.0, ageMinutes = 1),
    )
    assertNull(repository.currentPosition())
  }
}
