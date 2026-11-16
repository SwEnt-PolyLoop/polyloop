// Made with Claude.

package com.swent.polyloop.model.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [LocationRepository] backed by Android's [LocationManager], with the coarse location permission
 * only. It asks the best enabled provider for one fresh fix (fused on API 31+, else network), gives
 * up after [timeoutMillis], then falls back to the last known location. It never throws: a missing
 * permission, provider or fix gives null.
 *
 * The logic is kept thin because it depends on the system's location services, which unit tests
 * cannot reproduce.
 */
class LocationRepositoryAndroid(
    private val context: Context,
    private val timeoutMillis: Long = FIX_TIMEOUT_MILLIS,
) : LocationRepository {

  // Lazy, so that creating the repository does not touch the system service. Null on a phone
  // without location support.
  private val locationManager by lazy { context.getSystemService(LocationManager::class.java) }

  override suspend fun currentPosition(): MapPoint? {
    val manager = locationManager ?: return null
    return try {
      if (!hasCoarsePermission()) return null
      val location = withTimeoutOrNull(timeoutMillis) { currentFix(manager) } ?: lastKnown(manager)
      location?.let { MapPoint(it.latitude, it.longitude) }
    } catch (_: SecurityException) {
      // The permission was revoked meanwhile.
      null
    }
  }

  private fun hasCoarsePermission(): Boolean =
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
          PackageManager.PERMISSION_GRANTED

  /**
   * The enabled providers that can give a fix with the coarse permission, best first. GPS needs the
   * fine permission, so it is not used.
   */
  private fun freshProviders(manager: LocationManager): List<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      add(LocationManager.FUSED_PROVIDER)
    }
    add(LocationManager.NETWORK_PROVIDER)
  }
      .filter { manager.isProviderEnabled(it) }

  /** One fresh fix from the best enabled provider; cancelled with the coroutine (the timeout). */
  private suspend fun currentFix(manager: LocationManager): Location? {
    val provider = freshProviders(manager).firstOrNull() ?: return null
    return suspendCancellableCoroutine { continuation ->
      val signal = CancellationSignal()
      continuation.invokeOnCancellation { signal.cancel() }
      LocationManagerCompat.getCurrentLocation(
          manager,
          provider,
          signal,
          ContextCompat.getMainExecutor(context),
      ) {
        continuation.resume(it)
      }
    }
  }

  /** The newest last known location. The passive provider is only useful here, never for a fix. */
  private fun lastKnown(manager: LocationManager): Location? =
      (freshProviders(manager) + LocationManager.PASSIVE_PROVIDER)
          .mapNotNull { manager.getLastKnownLocation(it) }
          .maxByOrNull { it.time }

  companion object {
    const val FIX_TIMEOUT_MILLIS = 5_000L
  }
}
