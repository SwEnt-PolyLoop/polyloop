// Made with Claude.

package com.swent.polyloop.model

import android.content.Context
import com.swent.polyloop.model.auth.AuthRepository
import com.swent.polyloop.model.auth.AuthRepositoryFirebase
import com.swent.polyloop.model.connectivity.ConnectivityRepository
import com.swent.polyloop.model.connectivity.ConnectivityRepositoryAndroid
import com.swent.polyloop.model.listing.ListingRepository
import com.swent.polyloop.model.listing.ListingRepositoryFirestore
import com.swent.polyloop.model.profile.ProfileRepository
import com.swent.polyloop.model.profile.ProfileRepositoryFirestore

/**
 * The single place that creates the app's repositories, so screens and ViewModels share one
 * instance of each.
 *
 * Each repository is created on first use, not when this object loads, so Firebase is not touched
 * until it is needed (tests can still point it at the emulators first). Tests can also replace a
 * repository with a fake by assigning it before setting content, and call [reset] afterwards.
 *
 * Repositories that read the phone (such as the network status) need the app context, which
 * [com.swent.polyloop.PolyLoopApplication] passes to [init] at startup.
 */
object RepositoryProvider {

  private var appContext: Context? = null

  private var auth: AuthRepository? = null
  private var listing: ListingRepository? = null
  private var profile: ProfileRepository? = null
  private var connectivity: ConnectivityRepository? = null

  /** Keeps the app context for the repositories that need one. Called once at app startup. */
  fun init(context: Context) {
    appContext = context.applicationContext
  }

  var authRepository: AuthRepository
    get() = auth ?: AuthRepositoryFirebase().also { auth = it }
    set(value) {
      auth = value
    }

  var listingRepository: ListingRepository
    get() = listing ?: ListingRepositoryFirestore().also { listing = it }
    set(value) {
      listing = value
    }

  var profileRepository: ProfileRepository
    get() = profile ?: ProfileRepositoryFirestore().also { profile = it }
    set(value) {
      profile = value
    }

  var connectivityRepository: ConnectivityRepository
    get() =
        connectivity
            ?: ConnectivityRepositoryAndroid(
                    checkNotNull(appContext) { "RepositoryProvider.init was not called" }
                )
                .also { connectivity = it }
    set(value) {
      connectivity = value
    }

  /**
   * Forgets every repository, so the next access creates the real ones again. Keeps the app
   * context. For tests.
   */
  fun reset() {
    auth = null
    listing = null
    profile = null
    connectivity = null
  }

  /**
   * Forgets the app context given to [init], so a context set by one test does not leak into the
   * next one. [reset] keeps it, because the app only calls [init] once. For tests.
   */
  internal fun clearContext() {
    appContext = null
  }
}
