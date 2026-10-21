// Made with Claude.

package com.swent.polyloop.ui.myrentals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swent.polyloop.model.auth.AuthRepository
import com.swent.polyloop.model.rental.Rental
import com.swent.polyloop.model.rental.RentalRepository
import com.swent.polyloop.model.rental.RentalStatus
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The two tabs of My rentals. */
enum class RentalsTab {
  BORROWING,
  LENDING,
}

/**
 * One row of My rentals, ready to display.
 *
 * @property otherPersonName the lender on a borrowed item, the borrower on a lent one; "" when the
 *   name is missing, and the screen then shows a placeholder.
 * @property returnDate the last day of the rental, when the item must be back.
 * @property location the public pickup area until the item is picked up, then the drop-off area.
 * @property isPickupArea true while [location] is the pickup area, false once it is the drop-off
 *   area.
 * @property showReturnDate true while the item still has to come back, so [returnDate] matters.
 * @property totalPrice the rental price in PP, reserved from the borrower while it is requested.
 * @property listingPhotoUrl the listing's first photo; "" for older rentals, and the screen then
 *   shows a grey box.
 */
data class RentalRow(
    val rentalId: String,
    val itemTitle: String,
    val otherPersonName: String,
    val startDate: LocalDate,
    val returnDate: LocalDate,
    val location: String,
    val status: RentalStatus,
    val isPickupArea: Boolean,
    val showReturnDate: Boolean,
    val totalPrice: Int = 0,
    val listingPhotoUrl: String = "",
)

/**
 * State of the My rentals screen. [borrowing] and [lendingRequests] are newest first;
 * [lendingCurrent] puts what needs attention first (overdue, then disputed, frozen and lost, then
 * active, accepted, finished, and declined or cancelled last), newest first within each group. On
 * an error the last rows are kept. [errorMsg] is non-null when the rentals cannot be loaded (or
 * nobody is signed in); it is blank when there is no message to show, and the screen then shows a
 * generic text.
 *
 * @property lendingRequests the pending requests on the user's own listings.
 * @property lendingCurrent every other rental the user lends.
 */
data class MyRentalsUiState(
    val selectedTab: RentalsTab = RentalsTab.BORROWING,
    val borrowing: List<RentalRow> = emptyList(),
    val lendingRequests: List<RentalRow> = emptyList(),
    val lendingCurrent: List<RentalRow> = emptyList(),
    val isLoading: Boolean = true,
    val errorMsg: String? = null,
) {
  /** Everything the user lends: the requests, then the current rentals. */
  val lending: List<RentalRow>
    get() = lendingRequests + lendingCurrent

  /** The rows of the selected tab. */
  val selectedRows: List<RentalRow>
    get() = if (selectedTab == RentalsTab.BORROWING) borrowing else lending
}

/**
 * Shows what the signed-in user borrows and lends. Both lists follow the repository live, so a new
 * request appears without reloading.
 */
class MyRentalsViewModel(
    private val rentalRepository: RentalRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(MyRentalsUiState())
  val uiState: StateFlow<MyRentalsUiState> = _uiState.asStateFlow()

  private var observing: Job? = null

  init {
    observe()
  }

  /** Shows the rows of [tab]. */
  fun selectTab(tab: RentalsTab) = _uiState.update { it.copy(selectedTab = tab) }

  /** Starts following the rentals again, e.g. after an error. */
  fun retry() = observe()

  private fun observe() {
    observing?.cancel()
    _uiState.update { it.copy(isLoading = true, errorMsg = null) }
    val uid = authRepository.getCurrentUser()?.uid
    if (uid == null) {
      _uiState.update {
        it.copy(
            borrowing = emptyList(),
            lendingRequests = emptyList(),
            lendingCurrent = emptyList(),
            isLoading = false,
            errorMsg = "",
        )
      }
      return
    }
    observing = viewModelScope.launch {
      combine(
              rentalRepository.observeRentalsAsBorrower(uid),
              rentalRepository.observeRentalsAsLender(uid),
          ) { borrowed, lent ->
            // Both lists come newest first, and sortedBy keeps that order within each group.
            val (requests, current) = lent.partition { it.status == RentalStatus.REQUESTED }
            Triple(
                borrowed.map { it.toRow(uid) },
                requests.map { it.toRow(uid) },
                current.sortedBy { ATTENTION_ORDER.indexOf(it.status) }.map { it.toRow(uid) },
            )
          }
          .catch { e -> _uiState.update { it.copy(isLoading = false, errorMsg = e.message ?: "") } }
          .collect { (borrowing, requests, current) ->
            _uiState.update {
              it.copy(
                  borrowing = borrowing,
                  lendingRequests = requests,
                  lendingCurrent = current,
                  isLoading = false,
                  errorMsg = null,
              )
            }
          }
    }
  }

  /** Builds the ViewModel with its dependencies (manual constructor injection). */
  class Factory(
      private val rentalRepository: RentalRepository,
      private val authRepository: AuthRepository,
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      require(modelClass.isAssignableFrom(MyRentalsViewModel::class.java)) {
        "Unknown ViewModel class: ${modelClass.name}"
      }
      @Suppress("UNCHECKED_CAST")
      return MyRentalsViewModel(rentalRepository, authRepository) as T
    }
  }

  internal companion object {
    /** Statuses in which the item has not been picked up yet: the pickup area is what matters. */
    val BEFORE_PICKUP =
        setOf(
            RentalStatus.REQUESTED,
            RentalStatus.ACCEPTED,
            RentalStatus.DECLINED,
            RentalStatus.CANCELLED,
        )

    /** Statuses in which the item still has to come back, so the return date matters. */
    val AWAITING_RETURN =
        setOf(
            RentalStatus.REQUESTED,
            RentalStatus.ACCEPTED,
            RentalStatus.ACTIVE,
            RentalStatus.OVERDUE,
        )

    /** The order of lent rentals other than requests: what needs the lender's attention first. */
    val ATTENTION_ORDER =
        listOf(
            RentalStatus.OVERDUE,
            RentalStatus.DISPUTED,
            RentalStatus.FROZEN,
            RentalStatus.LOST,
            RentalStatus.ACTIVE,
            RentalStatus.ACCEPTED,
            RentalStatus.RETURNED,
            RentalStatus.CLOSED,
            RentalStatus.DECLINED,
            RentalStatus.CANCELLED,
        )

    fun Rental.toRow(uid: String) =
        RentalRow(
            rentalId = id,
            itemTitle = listingTitle,
            otherPersonName = otherPersonName(uid),
            startDate = startDate,
            returnDate = endDate,
            location = if (status in BEFORE_PICKUP) pickupArea else dropOffArea,
            status = status,
            isPickupArea = status in BEFORE_PICKUP,
            showReturnDate = status in AWAITING_RETURN,
            totalPrice = totalPrice,
            listingPhotoUrl = listingPhotoUrl,
        )
  }
}
