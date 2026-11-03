// Made with Claude.

package com.swent.polyloop.ui.rentaldetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swent.polyloop.model.auth.AuthRepository
import com.swent.polyloop.model.listing.ListingRepository
import com.swent.polyloop.model.profile.ProfileRepository
import com.swent.polyloop.model.rental.RentalRepository
import com.swent.polyloop.model.rental.RentalStatus
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * PolyPoints, or null when the listing is missing or could not be read.
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
)

/** Shows one rental, read once, as seen by the signed-in user. Only Requested is handled so far. */
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

  init {
    load()
  }

  /** Loads the rental again, e.g. after an error. */
  fun retry() = load()

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
      if (rental.status != RentalStatus.REQUESTED) {
        // Only Requested is built so far: the screen shows a placeholder with the title until the
        // other statuses are, so everything else stays at its default and the listing is not read.
        _uiState.value = RentalDetailUiState(isSupported = false, itemTitle = rental.listingTitle)
        return@launch
      }
      // Only the rental is required: a missing or unreadable listing just leaves the suggested
      // deposit out instead of failing the screen. Its read finishes before isLoading turns false,
      // so the screen does not flash a card without its deposit.
      val suggestedDeposit = listingRepository.getListing(rental.listingId).getOrNull()?.itemValue
      val uid = authRepository.getCurrentUser()?.uid.orEmpty()
      _uiState.value =
          RentalDetailUiState(
              isLender = uid == rental.ownerId,
              itemTitle = rental.listingTitle,
              status = rental.status,
              sentAt = rental.createdAt,
              startDate = rental.startDate,
              endDate = rental.endDate,
              rentalPrice = rental.totalPrice,
              suggestedDeposit = suggestedDeposit,
              pickupArea = rental.pickupArea,
          )
    }
  }
}
