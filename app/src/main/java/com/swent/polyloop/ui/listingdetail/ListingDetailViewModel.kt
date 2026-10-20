// Made with Claude.

package com.swent.polyloop.ui.listingdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swent.polyloop.model.auth.AuthRepository
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingRepository
import com.swent.polyloop.model.listing.ListingStatus
import com.swent.polyloop.model.profile.ProfileRepository
import com.swent.polyloop.model.rental.RentalRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State of the Listing detail screen: the saved listing and the dates the borrower picks.
 * [errorMsg] is non-null when loading failed; it is blank when the failure carried no message, and
 * the screen then shows a generic text. [isOwnListing] is true when the signed-in user owns the
 * listing. [sendError] is non-null when sending the request failed, blank in the same way, and
 * [sentRentalId] holds the new rental's id until the screen has opened its chat.
 */
data class ListingDetailUiState(
    val listing: Listing? = null,
    val isLoading: Boolean = false,
    val listingNotFound: Boolean = false,
    val errorMsg: String? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val isDatePickerOpen: Boolean = false,
    val isOwnListing: Boolean = false,
    val isSending: Boolean = false,
    val sendError: String? = null,
    val sentRentalId: String? = null,
)

/** Shows one published listing, holds the dates the borrower picks and sends the request. */
class ListingDetailViewModel(
    private val listingId: String,
    private val listingRepository: ListingRepository,
    private val rentalRepository: RentalRepository,
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(ListingDetailUiState())
  val uiState: StateFlow<ListingDetailUiState> = _uiState.asStateFlow()

  init {
    load()
  }

  /** Loads the listing again, e.g. after an error. */
  fun retry() = load()

  private fun load() {
    _uiState.update { it.copy(isLoading = true, errorMsg = null, listingNotFound = false) }
    viewModelScope.launch {
      listingRepository
          .getListing(listingId)
          .onSuccess { listing ->
            val published = listing?.takeIf { it.status == ListingStatus.PUBLISHED }
            val uid = authRepository.getCurrentUser()?.uid
            _uiState.update {
              it.copy(
                  isLoading = false,
                  listing = published,
                  listingNotFound = published == null,
                  isOwnListing = uid != null && published?.ownerId == uid,
              )
            }
          }
          .onFailure { e ->
            _uiState.update { it.copy(isLoading = false, errorMsg = e.message ?: "") }
          }
    }
  }

  /**
   * Opens the date picker and clears an earlier send error. Ignored until the listing is loaded.
   */
  fun openDatePicker() {
    if (_uiState.value.listing == null) return
    _uiState.update { it.copy(isDatePickerOpen = true, sendError = null) }
  }

  /** Closes the date picker and keeps any previous selection. */
  fun dismissDatePicker() {
    _uiState.update { it.copy(isDatePickerOpen = false) }
  }

  /**
   * Selects the range [start]..[end] (both days included) and closes the date picker. Ignored if
   * the listing is not loaded, [end] is before [start], or the range leaves the availability
   * window.
   */
  fun selectDates(start: LocalDate, end: LocalDate) {
    validListing(start, end) ?: return
    _uiState.update { it.copy(startDate = start, endDate = end, isDatePickerOpen = false) }
  }

  /**
   * Sends a request to rent the listing from [start] to [end] (both days included). On success it
   * closes the date picker and sets the new rental's id; on failure the picker stays open with the
   * error. Ignored on the user's own listing and for the same ranges as [selectDates]. The owner's
   * name is left blank if their profile is missing or cannot be read.
   */
  fun sendRequest(start: LocalDate, end: LocalDate) {
    val listing = validListing(start, end) ?: return
    if (_uiState.value.isOwnListing) return
    val borrower = authRepository.getCurrentUser()
    if (borrower == null) {
      _uiState.update { it.copy(sendError = "") }
      return
    }
    _uiState.update { it.copy(isSending = true, sendError = null) }
    viewModelScope.launch {
      val ownerName = profileRepository.getProfile(listing.ownerId).getOrNull()?.name ?: ""
      rentalRepository
          .requestRental(listing, ownerName, borrower, start, end)
          .onSuccess { rental ->
            _uiState.update {
              it.copy(isSending = false, sentRentalId = rental.id, isDatePickerOpen = false)
            }
          }
          .onFailure { e ->
            _uiState.update { it.copy(isSending = false, sendError = e.message ?: "") }
          }
    }
  }

  /** Clears the sent rental's id once the screen has opened its chat. */
  fun onRequestSentHandled() {
    _uiState.update { it.copy(sentRentalId = null) }
  }

  /**
   * The loaded listing if [start]..[end] can be requested, or null if the listing is not loaded,
   * [end] is before [start], or the range leaves the availability window.
   */
  private fun validListing(start: LocalDate, end: LocalDate): Listing? {
    val listing = _uiState.value.listing ?: return null
    val valid =
        !end.isBefore(start) &&
            !start.isBefore(listing.availableFrom) &&
            !end.isAfter(listing.availableTo)
    return listing.takeIf { valid }
  }

  /** Clears the selected dates. */
  fun clearDates() {
    _uiState.update { it.copy(startDate = null, endDate = null) }
  }

  /** Builds the ViewModel with its dependencies (manual constructor injection). */
  class Factory(
      private val listingId: String,
      private val listingRepository: ListingRepository,
      private val rentalRepository: RentalRepository,
      private val authRepository: AuthRepository,
      private val profileRepository: ProfileRepository,
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      require(modelClass.isAssignableFrom(ListingDetailViewModel::class.java)) {
        "Unknown ViewModel class: ${modelClass.name}"
      }
      @Suppress("UNCHECKED_CAST")
      return ListingDetailViewModel(
          listingId,
          listingRepository,
          rentalRepository,
          authRepository,
          profileRepository,
      )
          as T
    }
  }
}
