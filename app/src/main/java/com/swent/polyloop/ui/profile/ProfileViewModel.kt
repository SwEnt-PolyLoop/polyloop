// Made with Claude.

package com.swent.polyloop.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swent.polyloop.model.auth.AuthRepository
import com.swent.polyloop.model.listing.ListingRepository
import com.swent.polyloop.model.profile.ProfileRepository
import com.swent.polyloop.model.profile.UserProfile
import com.swent.polyloop.model.rental.RentalRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State of the Profile screen, for the signed-in user's own profile or someone else's.
 *
 * @property isOwnProfile true on the signed-in user's profile, which also shows their listings and
 *   "Sign out".
 * @property profile null while loading, or when the user has no profile ([profileNotFound]).
 * @property listings the signed-in user's listings with where each stands; always empty on someone
 *   else's profile.
 * @property isLoading true while the profile loads; the listings have their own [listingsLoading].
 * @property errorMsg non-null when the profile cannot be loaded (or nobody is signed in); blank
 *   when there is no message to show, and the screen then shows a generic text.
 * @property listingsLoading true while the own listings load; false on someone else's profile.
 * @property listingsError like [errorMsg], for the own listings only: the profile still shows.
 * @property isSignedOut true once the user signed out; the app then goes back to sign-in.
 */
data class ProfileUiState(
    val isOwnProfile: Boolean = false,
    val profile: UserProfile? = null,
    val listings: List<MyListing> = emptyList(),
    val isLoading: Boolean = true,
    val profileNotFound: Boolean = false,
    val errorMsg: String? = null,
    val listingsLoading: Boolean = true,
    val listingsError: String? = null,
    val isSignedOut: Boolean = false,
)

/**
 * Shows a profile: the signed-in user's own when [userId] is null (or is their id), someone else's
 * otherwise. The profile follows the repository live. The own listings are loaded once, separately,
 * and their rentals are followed live, so the state of each listing updates by itself.
 */
class ProfileViewModel(
    private val userId: String?,
    private val profileRepository: ProfileRepository,
    private val listingRepository: ListingRepository,
    private val rentalRepository: RentalRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(ProfileUiState())
  val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

  private var loadingProfile: Job? = null
  private var loadingListings: Job? = null

  init {
    load()
  }

  /** Loads the profile again, e.g. after an error. */
  fun retry() = load()

  /** Signs the user out. Only on their own profile; ignored on someone else's. */
  fun signOut() {
    if (!_uiState.value.isOwnProfile) return
    // Stop listening first: once signed out, the profile and rentals can no longer be read.
    loadingProfile?.cancel()
    loadingListings?.cancel()
    authRepository.signOut()
    _uiState.update { it.copy(isSignedOut = true) }
  }

  private fun load() {
    loadingProfile?.cancel()
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
          listingsLoading = isOwnProfile,
          listingsError = null,
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
    if (isOwnProfile) loadListings(uid)
  }

  /** Loads the own listings once and follows their rentals live. */
  private fun loadListings(uid: String) {
    loadingListings?.cancel()
    _uiState.update { it.copy(listingsLoading = true, listingsError = null) }
    val listings = flow { emit(listingRepository.getListingsByOwner(uid).getOrThrow()) }
    loadingListings = viewModelScope.launch {
      combine(listings, rentalRepository.observeRentalsAsLender(uid)) { owned, rentals ->
            owned.map { MyListing(it, rentalStateOf(it.id, rentals)) }
          }
          .catch { e ->
            _uiState.update { it.copy(listingsLoading = false, listingsError = e.message ?: "") }
          }
          .collect { mine ->
            _uiState.update {
              it.copy(listings = mine, listingsLoading = false, listingsError = null)
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
      )
          as T
    }
  }
}
