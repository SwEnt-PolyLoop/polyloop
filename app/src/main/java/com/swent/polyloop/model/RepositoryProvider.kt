// Made with Claude.

package com.swent.polyloop.model

import android.content.Context
import android.provider.Settings
import com.google.firebase.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storage
import com.swent.polyloop.model.auth.AuthRepository
import com.swent.polyloop.model.auth.AuthRepositoryFirebase
import com.swent.polyloop.model.chat.ChatRepository
import com.swent.polyloop.model.chat.ChatRepositoryFirestore
import com.swent.polyloop.model.connectivity.ConnectivityRepository
import com.swent.polyloop.model.connectivity.ConnectivityRepositoryAndroid
import com.swent.polyloop.model.dispute.DisputeRepository
import com.swent.polyloop.model.dispute.DisputeRepositoryFirestore
import com.swent.polyloop.model.handover.HandoverRepository
import com.swent.polyloop.model.handover.HandoverRepositoryFirestore
import com.swent.polyloop.model.listing.ListingRepository
import com.swent.polyloop.model.listing.ListingRepositoryFirestore
import com.swent.polyloop.model.profile.ProfileRepository
import com.swent.polyloop.model.profile.ProfileRepositoryFirestore
import com.swent.polyloop.model.rental.RentalRepository
import com.swent.polyloop.model.rental.RentalRepositoryFirestore
import com.swent.polyloop.model.rental.ServerClock
import com.swent.polyloop.model.rental.SharedPreferencesServerClockStore
import com.swent.polyloop.model.wallet.WalletRepository
import com.swent.polyloop.model.wallet.WalletRepositoryFirestore

/**
 * The single place that creates the app's repositories and the [ServerClock], so screens and
 * ViewModels share one instance of each.
 *
 * Each repository is created on first use, not when this object loads, so Firebase is not touched
 * until it is needed (tests can still point it at the emulators first). Tests can also replace a
 * repository with a fake by assigning it before setting content, and call [reset] afterwards.
 *
 * Repositories that read the phone (such as the network status), and the server clock, need the app
 * context, which [com.swent.polyloop.PolyLoopApplication] passes to [init] at startup.
 */
object RepositoryProvider {

  private var appContext: Context? = null

  private var auth: AuthRepository? = null
  private var listing: ListingRepository? = null
  private var chat: ChatRepository? = null
  private var profile: ProfileRepository? = null
  private var rental: RentalRepository? = null
  private var connectivity: ConnectivityRepository? = null
  private var handover: HandoverRepository? = null
  private var wallet: WalletRepository? = null
  private var dispute: DisputeRepository? = null
  private var clock: ServerClock? = null

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

  var chatRepository: ChatRepository
    get() = chat ?: ChatRepositoryFirestore().also { chat = it }
    set(value) {
      chat = value
    }

  var profileRepository: ProfileRepository
    get() = profile ?: ProfileRepositoryFirestore().also { profile = it }
    set(value) {
      profile = value
    }

  var rentalRepository: RentalRepository
    get() = rental ?: RentalRepositoryFirestore().also { rental = it }
    set(value) {
      rental = value
    }

  var handoverRepository: HandoverRepository
    get() = handover ?: HandoverRepositoryFirestore().also { handover = it }
    set(value) {
      handover = value
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
   * Today by the server's clock, for Start Pickup. Built here rather than in a composable, because
   * composables do not read the phone (it reads the boot count), and so that every screen shares
   * one clock and one saved pair. Built on first use from [rentalRepository] as it is then, so
   * tests that replace [rentalRepository] assign a clock built on their fake too.
   */
  var serverClock: ServerClock
    get() =
        clock
            ?: checkNotNull(appContext) { "RepositoryProvider.init was not called" }
                .let { context ->
                  ServerClock(
                      rentalRepository,
                      SharedPreferencesServerClockStore(context),
                      bootCount = {
                        Settings.Global.getInt(
                            context.contentResolver,
                            Settings.Global.BOOT_COUNT,
                            0,
                        )
                      },
                  )
                }
                .also { clock = it }
    set(value) {
      clock = value
    }

  /** The wallet repository: Firestore by default, read-only for the app (Cloud Functions write). */
  var walletRepository: WalletRepository
    get() = wallet ?: WalletRepositoryFirestore().also { wallet = it }
    set(value) {
      wallet = value
    }

  /**
   * The dispute repository: Firestore by default, read-only for the app (Cloud Functions write).
   */
  var disputeRepository: DisputeRepository
    get() = dispute ?: DisputeRepositoryFirestore().also { dispute = it }
    set(value) {
      dispute = value
    }

  /**
   * The Cloud Storage instance that repositories upload photos and PDFs to. Read on each access, so
   * Storage is not touched until a repository needs it. Repositories should take it as a lazy
   * provider defaulting to `{ RepositoryProvider.storage }` and read it through `by lazy`, so
   * Storage is only initialized when something is uploaded (a plain default argument would
   * initialize it as soon as the repository is created) and their tests can pass another instance.
   */
  val storage: FirebaseStorage
    get() = Firebase.storage

  /**
   * Forgets every repository and the server clock, so the next access creates the real ones again.
   * Keeps the app context. For tests.
   */
  fun reset() {
    auth = null
    listing = null
    chat = null
    profile = null
    rental = null
    connectivity = null
    handover = null
    wallet = null
    dispute = null
    clock = null
  }

  /**
   * Forgets the app context given to [init], so a context set by one test does not leak into the
   * next one. [reset] keeps it, because the app only calls [init] once. For tests.
   */
  internal fun clearContext() {
    appContext = null
  }
}
