// Made with Claude.

package com.swent.polyloop.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swent.polyloop.model.auth.AuthRepository
import com.swent.polyloop.model.connectivity.ConnectivityRepository
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingRepository
import com.swent.polyloop.model.listing.ListingStatus
import com.swent.polyloop.model.profile.ProfileRepository
import com.swent.polyloop.model.profile.UserProfile
import com.swent.polyloop.model.rental.RentalRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State of the Profile screen, for the signed-in user's own profile or someone else's.
 *
 * @property isOwnProfile true on the signed-in user's profile, which also shows "Edit profile",
 *   "Sign out", their drafts and where each listing stands.
 * @property profile null while loading, or when the user has no profile ([profileNotFound]).
 * @property listings on the own profile, all the user's listings with where each stands; on someone
 *   else's, only their published ones, whose rentals are not read (their rentalState is null).
 * @property isLoading true while the profile loads; the listings have their own [listingsLoading].
 * @property errorMsg non-null when the profile cannot be loaded (or nobody is signed in); blank
 *   when there is no message to show, and the screen then shows a generic text.
 * @property listingsLoading true while the listings load.
 * @property listingsError true when the listings cannot be loaded; the profile still shows, and the
 *   card shows a generic text whatever the cause.
 * @property listingsStale true when the last refresh of the listings failed, so [listings] are the
 *   ones read before, even when that read found none; false while they load, on [listingsError],
 *   while [listingsOffline], and before a first read succeeded, as there is then nothing read
 *   before.
 * @property listingsOffline true on someone else's profile while the phone is offline: nothing is
 *   read, as their listings are read from the server only, and the card says so instead of showing
 *   them. Always false on the own profile, which stays readable offline.
 * @property isSignedOut true once the user signed out; the app then goes back to sign-in.
 */
data class ProfileUiState(
    val isOwnProfile: Boolean = false,
    val profile: UserProfile? = null,
    val listings: List<MyListing> = emptyList(),
    val isLoading: Boolean = true,
    val profileNotFound: Boolean = false,
    val errorMsg: String? = null,
    val listingsLoading: Boolean = false,
    val listingsError: Boolean = false,
    val listingsStale: Boolean = false,
    val listingsOffline: Boolean = false,
    val isSignedOut: Boolean = false,
)

/**
 * Shows a profile: the signed-in user's own when [userId] is null (or is their id), someone else's
 * otherwise. The profile follows the repository live. The listings are loaded separately, again
 * each time the screen comes back ([onScreenResumed]). On the own profile their rentals are
 * followed live, so the state of each listing updates by itself; someone else's rentals are never
 * read, their drafts are left out, and their listings are only read from the server, while the
 * phone is online.
 */
class ProfileViewModel(
    private val userId: String?,
    private val profileRepository: ProfileRepository,
    private val listingRepository: ListingRepository,
    private val rentalRepository: RentalRepository,
    private val authRepository: AuthRepository,
    private val connectivityRepository: ConnectivityRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(ProfileUiState())
  val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

  private var loadingProfile: Job? = null
  private var loadingListings: Job? = null
  private var followingConnection: Job? = null
  private var listingsOwner: String? = null
  private val listingsRefresh = MutableStateFlow(0)
  private var screenShown = false

  init {
    load()
  }

  /** Loads the profile again, e.g. after an error. */
  fun retry() = load()

  /**
   * Loads the listings again, after an error or when the screen comes back (they may have been
   * edited or deleted meanwhile); the profile stays as it is. Ignored while they are loading, and
   * while [ProfileUiState.listingsOffline], as they load by themselves once back online. After an
   * error they load from the start, with the spinner. Otherwise the rentals of the own listings
   * stay followed, so only the listings are read again, without showing the spinner.
   */
  fun refreshListings() {
    val uid = listingsOwner ?: return
    when {
      _uiState.value.listingsLoading || _uiState.value.listingsOffline -> Unit
      loadingListings?.isActive == true -> listingsRefresh.update { it + 1 }
      else -> loadListings(uid)
    }
  }

  /**
   * Called each time the screen is resumed. The first time comes right after the listings were
   * loaded, so only coming back (e.g. from Listing detail, where a listing may have been edited or
   * deleted) loads them again. Kept here because the screen leaves the composition while another
   * screen is shown, and would forget it.
   */
  fun onScreenResumed() {
    if (screenShown) refreshListings() else screenShown = true
  }

  /** Signs the user out. Only on their own profile; ignored on someone else's. */
  fun signOut() {
    if (!_uiState.value.isOwnProfile) return
    // Stop listening first: once signed out, the profile and rentals can no longer be read.
    loadingProfile?.cancel()
    loadingListings?.cancel()
    // So that a later refresh (resume or "Retry") reads nothing.
    listingsOwner = null
    authRepository.signOut()
    _uiState.update { it.copy(isSignedOut = true, listingsLoading = false) }
  }

  private fun load() {
    loadingProfile?.cancel()
    followingConnection?.cancel()
    loadingListings?.cancel()
    val signedInUid = authRepository.getCurrentUser()?.uid
    val uid = userId ?: signedInUid
    val isOwnProfile = uid != null && uid == signedInUid
    _uiState.update {
      it.copy(
          isOwnProfile = isOwnProfile,
          isLoading = true,
          profileNotFound = false,
          errorMsg = null,
          listingsOffline = false,
      )
    }
    if (uid == null) {
      // Own profile asked for, but nobody is signed in (the start screen normally prevents this).
      _uiState.update { it.copy(isLoading = false, errorMsg = "") }
      return
    }
    loadingProfile = viewModelScope.launch {
      profileRepository
          .observeProfile(uid)
          .catch { e -> _uiState.update { it.copy(isLoading = false, errorMsg = e.message ?: "") } }
          .collect { profile ->
            _uiState.update {
              it.copy(
                  profile = profile,
                  isLoading = false,
                  profileNotFound = profile == null,
                  errorMsg = null,
              )
            }
          }
    }
    if (isOwnProfile) loadListings(uid) else loadListingsWhileOnline(uid)
  }

  /**
   * Loads someone else's listings each time the phone is online, at start and again whenever the
   * connection comes back, and reads nothing while it is offline: the read goes to the server, so
   * it could only fail.
   */
  private fun loadListingsWhileOnline(uid: String) {
    // Loading until the first network status arrives, so the empty text never flashes at start.
    _uiState.update {
      it.copy(listingsLoading = true, listingsError = false, listingsStale = false)
    }
    followingConnection = viewModelScope.launch {
      connectivityRepository.isOnline.collect { online ->
        if (online) {
          _uiState.update { it.copy(listingsOffline = false) }
          loadListings(uid)
        } else {
          loadingListings?.cancel()
          _uiState.update {
            it.copy(
                listingsOffline = true,
                listingsLoading = false,
                listingsError = false,
                listingsStale = false,
            )
          }
        }
      }
    }
  }

  /**
   * Loads the listings of [uid], again at each [listingsRefresh]: on the own profile all of them,
   * whose rentals are followed live; on someone else's only the published ones (their drafts are
   * left out), without reading their rentals, which belong to other people. Someone else's are read
   * from the server only: the cache may hold only some of them, or none, which would look like all
   * they have. Called for the first load, after an error and, on someone else's profile, each time
   * the connection comes back, so the spinner always shows: a retry never shows the old listings as
   * if they were current. A refresh that fails (e.g. a network error) keeps the listings already
   * shown, marked as stale until a refresh succeeds (even when there are none, so the user knows
   * why a new listing is missing), and the rentals of the own listings stay followed.
   */
  private fun loadListings(uid: String) {
    loadingListings?.cancel()
    listingsOwner = uid
    _uiState.update {
      it.copy(listingsLoading = true, listingsError = false, listingsStale = false)
    }
    // Set by load() before it loads the listings, and changes only there.
    val isOwnProfile = _uiState.value.isOwnProfile
    var shown: List<Listing>? = null
    // Each read gives the listings to show, and whether they are the old ones kept after a failure.
    val listings = listingsRefresh.map {
      listingRepository
          .getListingsByOwner(uid, fromServer = !isOwnProfile)
          .fold(
              onSuccess = { read -> read.also { shown = it } to false },
              onFailure = { e -> (shown ?: throw e) to true },
          )
    }
    val rows =
        if (isOwnProfile) {
          val lent = rentalRepository.observeRentalsAsLender(uid)
          combine(listings, lent) { (owned, stale), rentals ->
            owned.map { MyListing(it, rentalStateOf(it.id, rentals)) } to stale
          }
        } else {
          listings.map { (theirs, stale) ->
            val published = theirs.filter { it.status == ListingStatus.PUBLISHED }
            published.map { MyListing(it, rentalState = null) } to stale
          }
        }
    loadingListings = viewModelScope.launch {
      rows
          .catch {
            _uiState.update {
              it.copy(listingsLoading = false, listingsError = true, listingsStale = false)
            }
          }
          .collect { (shownRows, stale) ->
            _uiState.update {
              it.copy(
                  listings = shownRows,
                  listingsLoading = false,
                  listingsError = false,
                  listingsStale = stale,
              )
            }
          }
    }
  }

  /** Builds the ViewModel with its dependencies (manual constructor injection). */
  class Factory(
      private val userId: String?,
      private val profileRepository: ProfileRepository,
      private val listingRepository: ListingRepository,
      private val rentalRepository: RentalRepository,
      private val authRepository: AuthRepository,
      private val connectivityRepository: ConnectivityRepository,
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      require(modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
        "Unknown ViewModel class: ${modelClass.name}"
      }
      @Suppress("UNCHECKED_CAST")
      return ProfileViewModel(
          userId,
          profileRepository,
          listingRepository,
          rentalRepository,
          authRepository,
          connectivityRepository,
      )
          as T
    }
  }
}
