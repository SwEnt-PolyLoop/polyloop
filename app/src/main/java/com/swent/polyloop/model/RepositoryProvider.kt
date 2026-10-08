// Made with Claude.

package com.swent.polyloop.model

import com.swent.polyloop.model.auth.AuthRepository
import com.swent.polyloop.model.auth.AuthRepositoryFirebase
import com.swent.polyloop.model.listing.ListingRepository
import com.swent.polyloop.model.listing.ListingRepositoryFirestore

/**
 * The single place that creates the app's repositories, so screens and ViewModels share one
 * instance of each.
 *
 * Each repository is created on first use, not when this object loads, so Firebase is not touched
 * until it is needed (tests can still point it at the emulators first). Tests can also replace a
 * repository with a fake by assigning it before setting content, and call [reset] afterwards.
 */
object RepositoryProvider {

  private var auth: AuthRepository? = null
  private var listing: ListingRepository? = null

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

  /** Forgets every repository, so the next access creates the real ones again. For tests. */
  fun reset() {
    auth = null
    listing = null
  }
}
