// Made with Claude.

package com.swent.polyloop.ui.listingdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swent.polyloop.model.auth.AuthRepository
import com.swent.polyloop.model.connectivity.ConnectivityRepository
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingRepository
import com.swent.polyloop.model.listing.ListingStatus
import com.swent.polyloop.model.listing.bookedDays
import com.swent.polyloop.model.profile.ProfileRepository
import com.swent.polyloop.model.profile.UserProfile
import com.swent.polyloop.model.rental.ListingUnavailableException
import com.swent.polyloop.model.rental.RentalDatesUnavailableException
import com.swent.polyloop.model.rental.RentalRepository
import com.swent.polyloop.model.wallet.WalletRepository
import com.swent.polyloop.ui.components.isRequestable
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State of the Listing detail screen: the saved listing and the dates the borrower picks.
 * [errorMsg] is non-null when loading failed; it is blank when the failure carried no message, and
 * the screen then shows a generic text. [isOwnListing] is true when the signed-in user owns the
 * listing. [sendError] is non-null when sending the request failed, blank in the same way, and
 * [sentRentalId] holds the new rental's id until the screen has opened its chat. [disabledDates]
 * are the days booked by accepted rentals, and [datesTaken] is true when the picked dates are no
 * longer available (booked by someone else, or their start day passed) when the request is sent.
 * [isOffline] is true while the phone has no connection; the screen then shows the pickup area as
 * text, since the map tiles need a connection. [owner] is the lender's profile, or null when it is
 * missing or could not be read (the listing still shows, without the lender's details).
 * [availableBalance] is the signed-in user's spendable balance in whole PP (`Wallet.available`,
 * held amounts excluded), or null until the wallet has been read, or when it cannot be read or
 * nobody is signed in; the screen then disables "Request" instead of guessing. A missing wallet is
 * a real balance of 0.
 */
data class ListingDetailUiState(
    val listing: Listing? = null,
    val owner: UserProfile? = null,
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
    val disabledDates: Set<LocalDate> = emptySet(),
    val datesTaken: Boolean = false,
    val isOffline: Boolean = false,
    val availableBalance: Int? = null,
)

/**
 * Shows one published listing, holds the dates the borrower picks and sends the request. It follows
 * the network status from [connectivityRepository], so the screen knows when to replace the map.
 * [today] gives the phone's date, the same one the date picker uses. The balance the date picker
 * compares the price to comes from [walletRepository].
 */
class ListingDetailViewModel(
    private val listingId: String,
    private val listingRepository: ListingRepository,
    private val rentalRepository: RentalRepository,
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val connectivityRepository: ConnectivityRepository,
    private val walletRepository: WalletRepository,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {

  private val _uiState = MutableStateFlow(ListingDetailUiState())
  val uiState: StateFlow<ListingDetailUiState> = _uiState.asStateFlow()

  private var observingWallet: Job? = null

  init {
    load()
    observeWallet()
    viewModelScope.launch {
      connectivityRepository.isOnline.collect { online ->
        _uiState.update { it.copy(isOffline = !online) }
      }
    }
  }

  /** Loads the listing and follows the wallet again, e.g. after an error. */
  fun retry() {
    load()
    observeWallet()
  }

  private fun observeWallet() {
    observingWallet?.cancel()
    _uiState.update { it.copy(availableBalance = null) }
    val uid = authRepository.getCurrentUser()?.uid ?: return
    observingWallet = viewModelScope.launch {
      walletRepository
          .observeWallet(uid)
          .catch { _uiState.update { it.copy(availableBalance = null) } }
          .collect { wallet -> _uiState.update { it.copy(availableBalance = wallet.available) } }
    }
  }

  private fun load() {
    _uiState.update { it.copy(isLoading = true, errorMsg = null, listingNotFound = false) }
    viewModelScope.launch {
      listingRepository
          .getListing(listingId)
          .onSuccess { listing ->
            val published = listing?.takeIf { it.status == ListingStatus.PUBLISHED }
            val uid = authRepository.getCurrentUser()?.uid
            // Only the listing is required: a missing or unreadable profile must not fail the
            // screen. It is read before isLoading turns false, so the card does not fill in late.
            val owner = published?.let { profileRepository.getProfile(it.ownerId).getOrNull() }
            _uiState.update {
              it.copy(
                  isLoading = false,
                  listing = published,
                  owner = owner,
                  listingNotFound = published == null,
                  isOwnListing = uid != null && published?.ownerId == uid,
                  disabledDates = bookedDays(published?.bookedRanges.orEmpty()),
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
    _uiState.update { it.copy(isDatePickerOpen = true, sendError = null, datesTaken = false) }
  }

  /** Closes the date picker and keeps any previous selection. */
  fun dismissDatePicker() {
    _uiState.update { it.copy(isDatePickerOpen = false) }
  }

  /**
   * Selects the range [start]..[end] (both days included) and closes the date picker. Ignored if
   * the listing is not loaded or the range could not be picked in the date picker (see
   * [isRequestable]).
   */
  fun selectDates(start: LocalDate, end: LocalDate) {
    validListing(start, end) ?: return
    _uiState.update { it.copy(startDate = start, endDate = end, isDatePickerOpen = false) }
  }

  /**
   * Sends a request to rent the listing from [start] to [end] (both days included). It first reads
   * the listing's booked ranges from the server: if the range is no longer free, nothing is sent,
   * [ListingDetailUiState.datesTaken] is set and the newly booked days are disabled; if the listing
   * was deleted or is no longer published, the picker closes and the listing shows as not found; if
   * they cannot be read, the picker stays open with a blank error (the generic text). On success it
   * closes the date picker and sets the new rental's id; on failure the picker stays open with the
   * error. A range that can no longer be picked (see [selectDates]), e.g. because its start day has
   * passed, also sets [ListingDetailUiState.datesTaken] without reading anything. Ignored until the
   * listing is loaded, on the user's own listing, or while a request is already being sent. The
   * owner's name is the one read with the listing, blank if their profile was missing or could not
   * be read.
   */
  fun sendRequest(start: LocalDate, end: LocalDate) {
    val state = _uiState.value
    if (state.isSending || state.listing == null || state.isOwnListing) return
    val listing = validListing(start, end)
    if (listing == null) {
      _uiState.update { it.copy(datesTaken = true) }
      return
    }
    val borrower = authRepository.getCurrentUser()
    if (borrower == null) {
      _uiState.update { it.copy(sendError = "", datesTaken = false) }
      return
    }
    _uiState.update { it.copy(isSending = true, sendError = null, datesTaken = false) }
    viewModelScope.launch {
      val ranges =
          listingRepository.getBookedRanges(listingId).getOrElse {
            // Firestore's message (e.g. offline) is not for users: show the generic text.
            _uiState.update { it.copy(isSending = false, sendError = "") }
            return@launch
          }
      if (ranges == null) {
        showListingNotFound()
        return@launch
      }
      val disabledDates = bookedDays(ranges)
      if (!isRequestable(start, end, listing.window, disabledDates, today())) {
        _uiState.update {
          it.copy(isSending = false, datesTaken = true, disabledDates = disabledDates)
        }
        return@launch
      }
      val ownerName = state.owner?.name.orEmpty()
      rentalRepository
          .requestRental(listing, ownerName, borrower, start, end)
          .onSuccess { rental ->
            _uiState.update {
              it.copy(isSending = false, sentRentalId = rental.id, isDatePickerOpen = false)
            }
          }
          .onFailure { e ->
            when (e) {
              // Booked or unpublished between the check above and the server's: same as above.
              is RentalDatesUnavailableException -> showDatesTaken()
              is ListingUnavailableException -> showListingNotFound()
              else -> _uiState.update { it.copy(isSending = false, sendError = e.message ?: "") }
            }
          }
    }
  }

  /** Closes the date picker and shows the listing as not found: it was deleted or unpublished. */
  private fun showListingNotFound() {
    _uiState.update {
      it.copy(isSending = false, isDatePickerOpen = false, listing = null, listingNotFound = true)
    }
  }

  /**
   * Shows that the picked days were booked meanwhile, and greys out the booked days read again from
   * the server. If they cannot be read, the days greyed out so far stay.
   */
  private suspend fun showDatesTaken() {
    val disabledDates =
        listingRepository.getBookedRanges(listingId).getOrNull()?.let { bookedDays(it) }
            ?: _uiState.value.disabledDates
    _uiState.update { it.copy(isSending = false, datesTaken = true, disabledDates = disabledDates) }
  }

  /** Clears the send error and the dates-taken message, e.g. once the user taps a new day. */
  fun clearSendError() {
    _uiState.update { it.copy(sendError = null, datesTaken = false) }
  }

  /** Clears the sent rental's id once the screen has opened its chat. */
  fun onRequestSentHandled() {
    _uiState.update { it.copy(sentRentalId = null) }
  }

  /**
   * The loaded listing if [start]..[end] can be requested, or null if the listing is not loaded or
   * the range could not be picked in the date picker, given the days booked so far (see
   * [isRequestable]).
   */
  private fun validListing(start: LocalDate, end: LocalDate): Listing? {
    val state = _uiState.value
    val listing = state.listing ?: return null
    return listing.takeIf {
      isRequestable(start, end, listing.window, state.disabledDates, today())
    }
  }

  private val Listing.window: ClosedRange<LocalDate>
    get() = availableFrom..availableTo

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
      private val connectivityRepository: ConnectivityRepository,
      private val walletRepository: WalletRepository,
      private val today: () -> LocalDate = LocalDate::now,
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
          connectivityRepository,
          walletRepository,
          today,
      )
          as T
    }
  }
}
