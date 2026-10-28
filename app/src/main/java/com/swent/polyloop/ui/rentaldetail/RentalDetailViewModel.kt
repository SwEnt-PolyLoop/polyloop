// Made with Claude.

package com.swent.polyloop.ui.rentaldetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swent.polyloop.model.auth.AuthRepository
import com.swent.polyloop.model.listing.BookedRange
import com.swent.polyloop.model.listing.ListingRepository
import com.swent.polyloop.model.listing.bookedDays
import com.swent.polyloop.model.rental.Rental
import com.swent.polyloop.model.rental.RentalRepository
import com.swent.polyloop.model.rental.RentalStatus
import com.swent.polyloop.ui.components.bufferDays
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State of the Rental detail screen, for the request review (lender side, Figma frame 12).
 *
 * @property rental the rental, once loaded.
 * @property isLender true when the signed-in user owns the listing.
 * @property pickupAddress the exact pickup address typed by the lender.
 * @property returnAddress the exact drop-off address typed by the lender.
 * @property deposit the agreed deposit as typed; prefilled with the item's value. Not stored yet.
 * @property isAnswering true while accepting or declining is being written.
 * @property answerErrorMsg the reason the answer failed; "" when there is no message to show.
 * @property isAnswered true once the request has been accepted or declined; the screen then leaves.
 * @property errorMsg non-null when the rental cannot be loaded or nobody is signed in; "" when
 *   there is no message to show, and the screen then shows a generic text.
 */
data class RentalDetailUiState(
    val rental: Rental? = null,
    val isLoading: Boolean = true,
    val isLender: Boolean = false,
    val pickupAddress: String = "",
    val returnAddress: String = "",
    val deposit: String = "",
    val isAnswering: Boolean = false,
    val answerErrorMsg: String? = null,
    val isAnswered: Boolean = false,
    val errorMsg: String? = null,
) {
  /** True when the lender can accept or decline this rental. */
  val isRequestToAnswer: Boolean
    get() = isLender && rental?.status == RentalStatus.REQUESTED

  /** The deposit as a whole number above zero, or null when it is not one. */
  val depositAmount: Int?
    get() = deposit.trim().toIntOrNull()?.takeIf { it > 0 }

  /** Accepting needs both addresses and a valid deposit, and no answer in progress. */
  val canAccept: Boolean
    get() =
        isRequestToAnswer &&
            !isAnswering &&
            pickupAddress.isNotBlank() &&
            returnAddress.isNotBlank() &&
            depositAmount != null

  /** Declining needs no input, only that no answer is in progress. */
  val canDecline: Boolean
    get() = isRequestToAnswer && !isAnswering
}

/**
 * Shows one rental and lets its lender accept or decline a request. Accepting needs the exact
 * pickup and drop-off addresses and a deposit.
 */
class RentalDetailViewModel(
    private val rentalId: String,
    private val rentalRepository: RentalRepository,
    private val listingRepository: ListingRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(RentalDetailUiState())
  val uiState: StateFlow<RentalDetailUiState> = _uiState.asStateFlow()

  init {
    load()
  }

  /** Loads the rental again, e.g. after an error. */
  fun retry() {
    _uiState.update { it.copy(isLoading = true, errorMsg = null) }
    load()
  }

  fun onPickupAddressChange(value: String) = _uiState.update { it.copy(pickupAddress = value) }

  fun onReturnAddressChange(value: String) = _uiState.update { it.copy(returnAddress = value) }

  fun onDepositChange(value: String) = _uiState.update { it.copy(deposit = value) }

  /** Accepts the request, if the form is valid. Leaves the screen once it is saved. */
  fun accept() {
    val state = _uiState.value
    if (!state.canAccept) return
    val rental = state.rental ?: return
    answer {
      val declineIds =
          overlappingRequestIds(rental).getOrElse {
            return@answer Result.failure(it)
          }
      rentalRepository.acceptRental(
          id = rentalId,
          pickupAddress = state.pickupAddress,
          returnAddress = state.returnAddress,
          declineIds = declineIds,
      )
    }
  }

  /** Declines the request. Leaves the screen once it is saved. */
  fun decline() {
    if (!_uiState.value.canDecline) return
    answer { rentalRepository.declineRental(rentalId) }
  }

  private fun load() {
    viewModelScope.launch {
      val uid = authRepository.getCurrentUser()?.uid
      if (uid == null) {
        _uiState.update { it.copy(isLoading = false, errorMsg = "") }
        return@launch
      }
      val rental =
          rentalRepository.getRental(rentalId).getOrElse { e ->
            _uiState.update { it.copy(isLoading = false, errorMsg = e.message ?: "") }
            return@launch
          }
      if (rental == null) {
        _uiState.update { it.copy(isLoading = false, errorMsg = "") }
        return@launch
      }
      // The item's value is the suggested deposit; a missing listing leaves the field empty.
      val suggested = listingRepository.getListing(rental.listingId).getOrNull()?.itemValue
      _uiState.update {
        it.copy(
            rental = rental,
            isLender = uid == rental.ownerId,
            deposit = if (it.deposit.isEmpty()) suggested?.toString().orEmpty() else it.deposit,
            isLoading = false,
            errorMsg = null,
        )
      }
    }
  }

  /**
   * The other requests for the same item that overlap [rental] or its buffer days, the same days
   * the picker greys out (see [bufferDays]). Read from the lender's rentals, so the lender's user
   * id is needed; an error is returned, not thrown.
   */
  private suspend fun overlappingRequestIds(rental: Rental): Result<List<String>> {
    val uid =
        authRepository.getCurrentUser()?.uid
            ?: return Result.failure(IllegalStateException("Signed out"))
    return try {
      val booked = bookedDays(listOf(BookedRange(rental.startDate, rental.endDate)))
      val blocked = booked + bufferDays(booked)
      val ids =
          rentalRepository
              .observeRentalsAsLender(uid)
              .first()
              .filter { other ->
                other.id != rental.id &&
                    other.listingId == rental.listingId &&
                    other.status == RentalStatus.REQUESTED &&
                    generateSequence(other.startDate) { it.plusDays(1) }
                        .takeWhile { !it.isAfter(other.endDate) }
                        .any { it in blocked }
              }
              .map { it.id }
      Result.success(ids)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  /** Runs [write] and records its outcome: the rental on success, the error otherwise. */
  private fun answer(write: suspend () -> Result<Rental>) {
    _uiState.update { it.copy(isAnswering = true, answerErrorMsg = null) }
    viewModelScope.launch {
      write()
          .onSuccess { rental ->
            _uiState.update { it.copy(rental = rental, isAnswering = false, isAnswered = true) }
          }
          .onFailure { e ->
            _uiState.update { it.copy(isAnswering = false, answerErrorMsg = e.message ?: "") }
          }
    }
  }

  /** Builds the ViewModel with its dependencies (manual constructor injection). */
  class Factory(
      private val rentalId: String,
      private val rentalRepository: RentalRepository,
      private val listingRepository: ListingRepository,
      private val authRepository: AuthRepository,
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      require(modelClass.isAssignableFrom(RentalDetailViewModel::class.java)) {
        "Unknown ViewModel class: ${modelClass.name}"
      }
      @Suppress("UNCHECKED_CAST")
      return RentalDetailViewModel(rentalId, rentalRepository, listingRepository, authRepository)
          as T
    }
  }
}
