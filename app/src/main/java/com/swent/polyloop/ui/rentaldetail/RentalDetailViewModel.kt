// Made with Claude.

package com.swent.polyloop.ui.rentaldetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swent.polyloop.model.auth.AuthRepository
import com.swent.polyloop.model.listing.BookedRange
import com.swent.polyloop.model.listing.ListingRepository
import com.swent.polyloop.model.listing.conflictsWithBuffer
import com.swent.polyloop.model.profile.ProfileRepository
import com.swent.polyloop.model.rental.Rental
import com.swent.polyloop.model.rental.RentalRepository
import com.swent.polyloop.model.rental.RentalStatus
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State of the Rental detail screen for one rental, with raw values the screen formats itself.
 * [errorMsg] is non-null when the rental could not be read; it is blank when the failure carried no
 * message, and the screen then shows a generic text. [notFound] is true when there is no such
 * rental. [isSupported] is false for a status the screen does not handle yet (anything but
 * Requested): only [itemTitle] is then set, for the placeholder. [isLender] is true when the
 * signed-in user owns the item. [sentAt] is when the request was sent, [endDate] the return day and
 * [rentalPrice] the total price in PolyPoints. [suggestedDeposit] is the listing's item value in
 * PolyPoints, or null when the listing is missing or could not be read. [otherPerson] is the
 * borrower for the lender and the owner for the borrower.
 *
 * The lender's answer: [depositInput] starts with [suggestedDeposit] and is what the lender types,
 * [pickupAddress] and [returnAddress] are the exact addresses, and [overlapCount] is how many other
 * requests on the listing accepting this one would decline. [actionError] is non-null when
 * accepting or declining failed (blank means the generic text), [isSubmitting] is true while the
 * answer is sent, and [answered] is true once it was accepted or declined, until the screen has
 * left.
 */
data class RentalDetailUiState(
    val isLoading: Boolean = false,
    val errorMsg: String? = null,
    val notFound: Boolean = false,
    val isSupported: Boolean = true,
    val isLender: Boolean = false,
    val itemTitle: String = "",
    val status: RentalStatus? = null,
    val sentAt: Instant? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val rentalPrice: Int = 0,
    val suggestedDeposit: Int? = null,
    val pickupArea: String = "",
    val otherPerson: OtherPerson? = null,
    val depositInput: String = "",
    val pickupAddress: String = "",
    val returnAddress: String = "",
    val overlapCount: Int = 0,
    val isSubmitting: Boolean = false,
    val actionError: String? = null,
    val answered: Boolean = false,
) {
  /** The deposit typed, if it is a whole number above 0; otherwise null. */
  val deposit: Int?
    get() = depositInput.trim().toIntOrNull()?.takeIf { it > 0 }

  /** True when the lender can answer: the request is waiting and nothing is being sent. */
  private val canAnswer: Boolean
    get() = isLender && status == RentalStatus.REQUESTED && !isSubmitting

  /** Accepting needs a valid deposit and both addresses, on top of [canAnswer]. */
  val canAccept: Boolean
    get() = canAnswer && deposit != null && pickupAddress.isNotBlank() && returnAddress.isNotBlank()

  /** Declining needs nothing else than [canAnswer]. */
  val canDecline: Boolean
    get() = canAnswer
}

/**
 * The other side of the rental, shown on the person card with their initial. [name] comes from
 * their profile, or from the copy on the rental when the profile has none; it can still be blank,
 * and the screen then shows a placeholder. [averageRating] and [reviewCount] are null when the
 * profile is missing or could not be read (the rating is unknown), while 0.0 and 0 mean the person
 * has no reviews yet.
 */
data class OtherPerson(
    val uid: String,
    val name: String,
    val averageRating: Double?,
    val reviewCount: Int?,
)

/**
 * Shows one rental, read once, as seen by the signed-in user, and lets the lender answer a
 * requested rental: accept it with the deposit and both addresses, or decline it. The answer is
 * made by Cloud Functions through [RentalRepository]; this ViewModel only checks what the lender
 * typed and reports the result. Only Requested is handled so far.
 */
class RentalDetailViewModel(
    private val rentalId: String,
    private val rentalRepository: RentalRepository,
    private val listingRepository: ListingRepository,
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(RentalDetailUiState())
  val uiState: StateFlow<RentalDetailUiState> = _uiState.asStateFlow()

  private var loading: Job? = null
  private var overlaps: Job? = null

  init {
    load()
  }

  /** Loads the rental again, e.g. after an error. */
  fun retry() = load()

  private fun load() {
    // A retry while an earlier load is still running would otherwise let the older, slower result
    // overwrite the newer one.
    loading?.cancel()
    overlaps?.cancel()
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
      if (rental.status != RentalStatus.REQUESTED) {
        // Only Requested is built so far: the screen shows a placeholder with the title until the
        // other statuses are, so everything else stays at its default and neither the listing nor
        // the other person's profile is read.
        _uiState.value = RentalDetailUiState(isSupported = false, itemTitle = rental.listingTitle)
        return@launch
      }
      val uid = authRepository.getCurrentUser()?.uid.orEmpty()
      val isLender = uid == rental.ownerId
      val otherUid = if (isLender) rental.borrowerId else rental.ownerId
      // Only the rental is required: a missing or unreadable listing or profile falls back (no
      // deposit, the name copied on the rental) instead of failing the screen. The two reads need
      // nothing from each other, so they run at the same time; both are awaited before isLoading
      // turns false, so the screen does not flash a half-filled card.
      val listing = async { listingRepository.getListing(rental.listingId).getOrNull() }
      val profileRead = async { profileRepository.getProfile(otherUid).getOrNull() }
      val suggestedDeposit = listing.await()?.itemValue
      val profile = profileRead.await()
      val otherPerson =
          OtherPerson(
              uid = otherUid,
              name = profile?.name.orEmpty().ifBlank { rental.otherPersonName(uid) },
              averageRating = profile?.averageRating,
              reviewCount = profile?.reviewCount,
          )
      // update, not a fresh state: the lender may already have typed in the form while this loaded.
      _uiState.update {
        it.copy(
            isLoading = false,
            isLender = isLender,
            itemTitle = rental.listingTitle,
            status = rental.status,
            sentAt = rental.createdAt,
            startDate = rental.startDate,
            endDate = rental.endDate,
            rentalPrice = rental.totalPrice,
            suggestedDeposit = suggestedDeposit,
            pickupArea = rental.pickupArea,
            otherPerson = otherPerson,
            depositInput =
                if (it.depositInput.isEmpty()) suggestedDeposit?.toString().orEmpty()
                else it.depositInput,
        )
      }
      if (isLender) observeOverlaps(rental)
    }
  }

  /**
   * Counts, as the lender's requests change, the other requests on the same listing that accepting
   * [rental] would decline: their dates overlap, buffer day included.
   */
  private fun observeOverlaps(rental: Rental) {
    val dates = BookedRange(rental.startDate, rental.endDate)
    overlaps = viewModelScope.launch {
      rentalRepository
          .observeRentalsAsLender(rental.ownerId)
          .catch {
            // Keep the last count: the lender can still answer without it.
          }
          .collect { rentals ->
            val count = rentals.count { other ->
              other.id != rental.id &&
                  other.listingId == rental.listingId &&
                  other.status == RentalStatus.REQUESTED &&
                  conflictsWithBuffer(dates, BookedRange(other.startDate, other.endDate))
            }
            _uiState.update { it.copy(overlapCount = count) }
          }
    }
  }

  /** Sets the deposit the lender types. */
  fun updateDeposit(text: String) {
    _uiState.update { it.copy(depositInput = text) }
  }

  /** Sets the exact pickup address the lender types. */
  fun updatePickupAddress(text: String) {
    _uiState.update { it.copy(pickupAddress = text) }
  }

  /** Sets the exact return address the lender types. */
  fun updateReturnAddress(text: String) {
    _uiState.update { it.copy(returnAddress = text) }
  }

  /** Accepts the request with the typed deposit and addresses. Ignored unless canAccept. */
  fun accept() {
    val state = _uiState.value
    val deposit = state.deposit
    if (!state.canAccept || deposit == null) return
    _uiState.update { it.copy(isSubmitting = true, actionError = null) }
    viewModelScope.launch {
      rentalRepository
          .acceptRental(rentalId, deposit, state.pickupAddress.trim(), state.returnAddress.trim())
          .onSuccess { _uiState.update { it.copy(isSubmitting = false, answered = true) } }
          .onFailure { e ->
            _uiState.update { it.copy(isSubmitting = false, actionError = e.message ?: "") }
          }
    }
  }

  /** Declines the request. Ignored unless canDecline. */
  fun decline() {
    if (!_uiState.value.canDecline) return
    _uiState.update { it.copy(isSubmitting = true, actionError = null) }
    viewModelScope.launch {
      rentalRepository
          .declineRental(rentalId)
          .onSuccess { _uiState.update { it.copy(isSubmitting = false, answered = true) } }
          .onFailure { e ->
            _uiState.update { it.copy(isSubmitting = false, actionError = e.message ?: "") }
          }
    }
  }

  /** Clears the answered flag once the screen has left. */
  fun onAnsweredHandled() {
    _uiState.update { it.copy(answered = false) }
  }

  /** Builds the ViewModel with its dependencies (manual constructor injection). */
  class Factory(
      private val rentalId: String,
      private val rentalRepository: RentalRepository,
      private val listingRepository: ListingRepository,
      private val profileRepository: ProfileRepository,
      private val authRepository: AuthRepository,
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      require(modelClass.isAssignableFrom(RentalDetailViewModel::class.java)) {
        "Unknown ViewModel class: ${modelClass.name}"
      }
      @Suppress("UNCHECKED_CAST")
      return RentalDetailViewModel(
          rentalId,
          rentalRepository,
          listingRepository,
          profileRepository,
          authRepository,
      )
          as T
    }
  }
}
