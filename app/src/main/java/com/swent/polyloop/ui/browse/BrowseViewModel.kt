// Made with Claude.

package com.swent.polyloop.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.model.listing.ListingFilter
import com.swent.polyloop.model.listing.ListingRepository
import com.swent.polyloop.model.listing.ListingStatus
import com.swent.polyloop.model.listing.search
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State of the Browse screen: the published listings and the search and categories typed by the
 * user. [errorMsg] is non-null when loading failed; it is blank when the failure carried no
 * message, and the screen then shows a generic text.
 */
data class BrowseUiState(
    val isLoading: Boolean = false,
    val listings: List<Listing> = emptyList(),
    val errorMsg: String? = null,
    val filter: ListingFilter = ListingFilter(),
) {
  /** The listings passing [filter], best match first: the ones the screen shows. */
  val visibleListings: List<Listing> by lazy { listings.search(filter) }
}

/** Loads the published listings shown on the Browse screen and keeps the user's filter. */
class BrowseViewModel(private val listingRepository: ListingRepository) : ViewModel() {

  private val _uiState = MutableStateFlow(BrowseUiState())
  val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()

  init {
    load()
  }

  /** Loads the listings again, e.g. after an error. The filter is kept. */
  fun refresh() = load()

  /** Shows only the listings matching [query]. */
  fun setQuery(query: String) = updateFilter { it.copy(query = query) }

  /** Selects [category] if it is not selected, deselects it otherwise. */
  fun toggleCategory(category: ListingCategory) = updateFilter {
    val categories = it.categories
    it.copy(
        categories = if (category in categories) categories - category else categories + category
    )
  }

  /** Deselects every category, so listings of all categories are shown ("All"). */
  fun clearCategories() = updateFilter { it.copy(categories = emptySet()) }

  private fun updateFilter(change: (ListingFilter) -> ListingFilter) {
    _uiState.update { it.copy(filter = change(it.filter)) }
  }

  private fun load() {
    _uiState.update { it.copy(isLoading = true, errorMsg = null) }
    viewModelScope.launch {
      listingRepository
          .getAllListings()
          .onSuccess { listings ->
            val published = listings.filter { it.status == ListingStatus.PUBLISHED }
            _uiState.update { it.copy(isLoading = false, listings = published) }
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
