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
 */
data class RentalRow(
    val rentalId: String,
    val itemTitle: String,
    val otherPersonName: String,
    val startDate: LocalDate,
    val returnDate: LocalDate,
    val location: String,
    val status: RentalStatus,
)

/**
 * State of the My rentals screen. The lists are newest first, except that incoming requests come
 * first in [lending] (docs/SCREENS.md). On an error the last rows are kept. [errorMsg] is non-null
 * when the rentals cannot be loaded (or nobody is signed in); it is blank when there is no message
 * to show, and the screen then shows a generic text.
 */
data class MyRentalsUiState(
    val selectedTab: RentalsTab = RentalsTab.BORROWING,
    val borrowing: List<RentalRow> = emptyList(),
    val lending: List<RentalRow> = emptyList(),
    val isLoading: Boolean = true,
    val errorMsg: String? = null,
) {
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
        it.copy(borrowing = emptyList(), lending = emptyList(), isLoading = false, errorMsg = "")
      }
      return
    }
    observing = viewModelScope.launch {
      combine(
              rentalRepository.observeRentalsAsBorrower(uid),
              rentalRepository.observeRentalsAsLender(uid),
          ) { borrowed, lent ->
            val requestsFirst = lent.sortedBy { it.status != RentalStatus.REQUESTED }
            borrowed.map { it.toRow(uid) } to requestsFirst.map { it.toRow(uid) }
          }
          .catch { e -> _uiState.update { it.copy(isLoading = false, errorMsg = e.message ?: "") } }
          .collect { (borrowing, lending) ->
            _uiState.update {
              it.copy(
                  borrowing = borrowing,
                  lending = lending,
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

  private companion object {
    /** Statuses in which the item has not been picked up yet: the pickup area is what matters. */
    val BEFORE_PICKUP =
        setOf(
            RentalStatus.REQUESTED,
            RentalStatus.ACCEPTED,
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
        )
  }
}
