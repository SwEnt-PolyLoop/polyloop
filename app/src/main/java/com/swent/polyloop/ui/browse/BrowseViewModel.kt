// Made with Claude.

package com.swent.polyloop.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State of the Browse screen: the published listings. [errorMsg] is non-null when loading failed;
 * it is blank when the failure carried no message, and the screen then shows a generic text.
 */
data class BrowseUiState(
    val isLoading: Boolean = false,
    val listings: List<Listing> = emptyList(),
    val errorMsg: String? = null,
)

/** Loads the published listings shown on the Browse screen. */
class BrowseViewModel(private val listingRepository: ListingRepository) : ViewModel() {

  private val _uiState = MutableStateFlow(BrowseUiState())
  val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()

  init {
    load()
  }

  /** Loads the listings again, e.g. after an error. */
  fun refresh() = load()

  private fun load() {
    _uiState.update { it.copy(isLoading = true, errorMsg = null) }
    viewModelScope.launch {
      listingRepository
          .getAllListings()
          .onSuccess { listings ->
            _uiState.update { it.copy(isLoading = false, listings = listings) }
          }
          .onFailure { e ->
            _uiState.update { it.copy(isLoading = false, errorMsg = e.message ?: "") }
          }
    }
  }

  /** Builds the ViewModel with its dependencies (manual constructor injection). */
  class Factory(private val listingRepository: ListingRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      require(modelClass.isAssignableFrom(BrowseViewModel::class.java)) {
        "Unknown ViewModel class: ${modelClass.name}"
      }
      @Suppress("UNCHECKED_CAST")
      return BrowseViewModel(listingRepository) as T
    }
  }
}
