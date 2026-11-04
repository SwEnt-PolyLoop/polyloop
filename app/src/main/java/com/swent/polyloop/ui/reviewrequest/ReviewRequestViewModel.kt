// Made with Claude.

package com.swent.polyloop.ui.reviewrequest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swent.polyloop.model.auth.AuthRepository
import com.swent.polyloop.model.connectivity.ConnectivityRepository
import com.swent.polyloop.model.listing.ListingRepository
import com.swent.polyloop.model.profile.ProfileRepository
import com.swent.polyloop.model.rental.RentalRepository
import com.swent.polyloop.model.rental.RentalStatus
import com.swent.polyloop.ui.createlisting.isWholeNumberAboveZero
import com.swent.polyloop.ui.rentaldetail.OtherPerson
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How the lender's answer ended, which tells the screen where to go next. */
enum class ReviewOutcome {
  OFFER_SENT,
  DECLINED,
}

/**
 * State of the Review request screen, with raw values the screen formats itself.
 *
 * @property isLoading true while the request is read.
 * @property errorMsg non-null when the request could not be read; blank when the failure carried no
 *   message, and the screen then shows a generic text.
 * @property notFound true when there is no such rental.
 * @property canAnswer true when the signed-in user is the lender of a request with no offer yet.
 *   Otherwise the form is not shown: only [itemTitle] is set.
 * @property itemTitle the listing's title.
 * @property startDate the rental's first day.
 * @property endDate the rental's last day.
 * @property rentalPrice the total price in PolyPoints.
 * @property borrower the person asking; a blank name means the screen shows a placeholder.
 * @property deposit the deposit field, filled in with the item's value when the listing can be
 *   read.
 * @property pickupAddress the pickup address field.
 * @property returnAddress the drop-off address field.
 * @property isOffline true while the phone has no connection; both actions are then disabled.
 * @property isSubmitting true while an answer is being sent.
 * @property actionError non-null when an answer failed; blank when the failure carried no message.
 *   The form keeps its content.
 * @property outcome set once the answer has succeeded.
 */
data class ReviewRequestUiState(
    val isLoading: Boolean = false,
    val errorMsg: String? = null,
    val notFound: Boolean = false,
    val canAnswer: Boolean = false,
    val itemTitle: String = "",
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val rentalPrice: Int = 0,
    val borrower: OtherPerson? = null,
    val deposit: String = "",
    val pickupAddress: String = "",
    val returnAddress: String = "",
    val isOffline: Boolean = false,
    val isSubmitting: Boolean = false,
    val actionError: String? = null,
    val outcome: ReviewOutcome? = null,
) {
  /** True when "Decline" can be pressed. */
  val canDecline: Boolean
    get() = canAnswer && !isOffline && !isSubmitting && outcome == null

  /** True when "Send offer" can be pressed: a valid deposit and both addresses are filled in. */
  val canSendOffer: Boolean
    get() =
        canDecline &&
            deposit.isWholeNumberAboveZero() &&
            pickupAddress.isNotBlank() &&
            returnAddress.isNotBlank()
}

/**
 * Lets the lender answer a request with no offer yet: send a deposit offer, or decline.
 *
 * @param rentalId the request to answer.
 * @param rentalRepository reads the request and sends the answer.
 * @param listingRepository gives the item's value, the suggested deposit.
 * @param profileRepository gives the borrower's name and rating.
 * @param authRepository tells who the signed-in user is.
 * @param connectivityRepository tells whether the phone is online.
 */
class ReviewRequestViewModel(
    private val rentalId: String,
    private val rentalRepository: RentalRepository,
    private val listingRepository: ListingRepository,
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
    private val connectivityRepository: ConnectivityRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(ReviewRequestUiState())
  val uiState: StateFlow<ReviewRequestUiState> = _uiState.asStateFlow()

  private var loading: Job? = null

  init {
    // Apart from the load, so that retry() neither stops nor duplicates it.
    viewModelScope.launch {
      connectivityRepository.isOnline.collect { online ->
        _uiState.update { it.copy(isOffline = !online) }
      }
    }
    load()
  }

  /** Loads the request again, e.g. after an error. */
  fun retry() = load()

  fun onDepositChange(deposit: String) = _uiState.update { it.copy(deposit = deposit) }

  fun onPickupAddressChange(address: String) = _uiState.update { it.copy(pickupAddress = address) }

  fun onReturnAddressChange(address: String) = _uiState.update { it.copy(returnAddress = address) }

  /** Sends the offer; does nothing unless [ReviewRequestUiState.canSendOffer] is true. */
  fun sendOffer() {
    val state = _uiState.value
    if (!state.canSendOffer) return
    submit(ReviewOutcome.OFFER_SENT) {
      rentalRepository.makeOffer(
          rentalId,
          state.deposit.toInt(),
          state.pickupAddress.trim(),
          state.returnAddress.trim(),
      )
    }
  }

  /** Declines the request; does nothing unless [ReviewRequestUiState.canDecline] is true. */
  fun decline() {
    if (!_uiState.value.canDecline) return
    submit(ReviewOutcome.DECLINED) { rentalRepository.declineRequest(rentalId) }
  }

  private fun submit(outcome: ReviewOutcome, call: suspend () -> Result<Unit>) {
    _uiState.update { it.copy(isSubmitting = true, actionError = null) }
    viewModelScope.launch {
      call()
          .onSuccess { _uiState.update { it.copy(isSubmitting = false, outcome = outcome) } }
          .onFailure { e ->
            _uiState.update { it.copy(isSubmitting = false, actionError = e.message ?: "") }
          }
    }
  }

  private fun load() {
    // A retry while an earlier load is still running would otherwise let the older, slower result
    // overwrite the newer one.
    loading?.cancel()
    _uiState.update { it.copy(isLoading = true, errorMsg = null, notFound = false) }
    loading = viewModelScope.launch {
      val rental =
          rentalRepository.getRental(rentalId).getOrElse { e ->
            _uiState.update { it.copy(isLoading = false, errorMsg = e.message ?: "") }
            return@launch
          }
      if (rental == null) {
        _uiState.update { it.copy(isLoading = false, notFound = true) }
        return@launch
      }
      val uid = authRepository.getCurrentUser()?.uid
      if (
          uid != rental.ownerId || rental.status != RentalStatus.REQUESTED || rental.offer != null
      ) {
        _uiState.update { it.copy(isLoading = false, itemTitle = rental.listingTitle) }
        return@launch
      }
      // Only the rental is required: an unreadable listing leaves the deposit empty, and an
      // unreadable profile falls back to the name copied on the rental.
      val listing = async { listingRepository.getListing(rental.listingId).getOrNull() }
      val profileRead = async { profileRepository.getProfile(rental.borrowerId).getOrNull() }
      val itemValue = listing.await()?.itemValue
      val profile = profileRead.await()
      _uiState.update {
        it.copy(
            isLoading = false,
            canAnswer = true,
            itemTitle = rental.listingTitle,
            startDate = rental.startDate,
            endDate = rental.endDate,
            rentalPrice = rental.totalPrice,
            borrower =
                OtherPerson(
                    uid = rental.borrowerId,
                    name = profile?.name.orEmpty().ifBlank { rental.borrowerName },
                    averageRating = profile?.averageRating,
                    reviewCount = profile?.reviewCount,
                ),
            deposit = itemValue?.toString().orEmpty(),
        )
      }
    }
  }

  /** Builds the ViewModel with its dependencies (manual constructor injection). */
  class Factory(
      private val rentalId: String,
      private val rentalRepository: RentalRepository,
      private val listingRepository: ListingRepository,
      private val profileRepository: ProfileRepository,
      private val authRepository: AuthRepository,
      private val connectivityRepository: ConnectivityRepository,
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      require(modelClass.isAssignableFrom(ReviewRequestViewModel::class.java)) {
        "Unknown ViewModel class: ${modelClass.name}"
      }
      @Suppress("UNCHECKED_CAST")
      return ReviewRequestViewModel(
          rentalId,
          rentalRepository,
          listingRepository,
          profileRepository,
          authRepository,
          connectivityRepository,
      )
          as T
    }
  }
}
