// Made with Claude.

package com.swent.polyloop.ui.listingdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingRepository
import com.swent.polyloop.model.listing.ListingStatus
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** State of the Listing detail screen: the saved listing and the dates the borrower picks. */
data class ListingDetailUiState(
    val listing: Listing? = null,
    val isLoading: Boolean = false,
    val listingNotFound: Boolean = false,
    val errorMsg: String? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val isDatePickerOpen: Boolean = false,
) {
  /** Days in the selected range, counting both the start and the end day. 0 if none is picked. */
  val numberOfDays: Int
    get() =
        if (startDate == null || endDate == null) 0
        else ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1

  /** Price per day times [numberOfDays], in PP. */
  val totalPrice: Int
    get() = (listing?.pricePerDay ?: 0) * numberOfDays

  /** True once the listing is loaded and a date range is picked. */
  val canRequest: Boolean
    get() = listing != null && numberOfDays > 0
}

/** Shows one published listing and holds the dates the borrower picks to request it. */
class ListingDetailViewModel(
    private val listingId: String,
    private val listingRepository: ListingRepository,
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
            _uiState.update {
              it.copy(isLoading = false, listing = published, listingNotFound = published == null)
            }
          }
          .onFailure { e ->
            _uiState.update {
              it.copy(isLoading = false, errorMsg = e.message ?: LOAD_ERROR_FALLBACK)
            }
          }
    }
  }

  /** Opens the date picker. Ignored until the listing is loaded. */
  fun openDatePicker() {
    if (_uiState.value.listing == null) return
    _uiState.update { it.copy(isDatePickerOpen = true) }
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
    val listing = _uiState.value.listing ?: return
    val valid =
        !end.isBefore(start) &&
            !start.isBefore(listing.availableFrom) &&
            !end.isAfter(listing.availableTo)
    if (!valid) return
    _uiState.update { it.copy(startDate = start, endDate = end, isDatePickerOpen = false) }
  }

  /** Clears the selected dates. */
  fun clearDates() {
    _uiState.update { it.copy(startDate = null, endDate = null) }
  }

  /** Builds the ViewModel with its dependencies (manual constructor injection). */
  class Factory(
      private val listingId: String,
      private val listingRepository: ListingRepository,
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      require(modelClass.isAssignableFrom(ListingDetailViewModel::class.java)) {
        "Unknown ViewModel class: ${modelClass.name}"
      }
      @Suppress("UNCHECKED_CAST")
      return ListingDetailViewModel(listingId, listingRepository) as T
    }
  }

  private companion object {
    /** Used when a failure carries no message, so the error state is never silently empty. */
    const val LOAD_ERROR_FALLBACK = "Could not load the listing"
  }
}
