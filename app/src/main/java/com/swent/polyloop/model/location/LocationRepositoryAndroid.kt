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
 * only. It asks the enabled providers in turn (fused on API 31+, then network) for one fresh fix,
 * giving each [PROVIDER_TIMEOUT_MILLIS] and all of them [timeoutMillis] in all, then falls back to
 * the last known location. It never throws: a missing permission, provider or fix gives null.
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

  /**
   * The first fresh fix from the enabled providers, best first, or null if none gives one. Each
   * provider gets [PROVIDER_TIMEOUT_MILLIS], so one that stays silent does not use up the whole
   * budget of the next one.
   */
  private suspend fun currentFix(manager: LocationManager): Location? =
      freshProviders(manager).firstNotNullOfOrNull { provider ->
        withTimeoutOrNull(PROVIDER_TIMEOUT_MILLIS) { fixFrom(manager, provider) }
      }

  /** One fresh fix from [provider]; cancelled with the coroutine (a timeout). */
  private suspend fun fixFrom(manager: LocationManager, provider: String): Location? =
      suspendCancellableCoroutine { continuation ->
        val signal = CancellationSignal()
        continuation.invokeOnCancellation { signal.cancel() }
        try {
          LocationManagerCompat.getCurrentLocation(
              manager,
              provider,
              signal,
              ContextCompat.getMainExecutor(context),
          ) {
            continuation.resume(it)
          }
        } catch (_: SecurityException) {
          // The permission was revoked meanwhile: no fix.
          continuation.resume(null)
        }
      }

  /** The newest last known location. The passive provider is only useful here, never for a fix. */
  private fun lastKnown(manager: LocationManager): Location? =
      (freshProviders(manager) + LocationManager.PASSIVE_PROVIDER)
          .mapNotNull { provider ->
            try {
              manager.getLastKnownLocation(provider)
            } catch (_: SecurityException) {
              // The permission was revoked meanwhile: no known location.
              null
            }
          }
          .maxByOrNull { it.time }

  companion object {
    const val FIX_TIMEOUT_MILLIS = 6_000L
    const val PROVIDER_TIMEOUT_MILLIS = 3_000L
  }
}
