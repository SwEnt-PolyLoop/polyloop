// Made with Claude.

package com.swent.polyloop.ui.createlisting

import androidx.lifecycle.ViewModel
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingCategory
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * State of the Create listing screen: the fields as typed, plus checks that follow the rules of
 * [Listing.canPublish].
 *
 * @property pricePerDay the price per day as typed; valid when it is a whole number of PP from 1 to
 *   [Listing.MAX_AMOUNT].
 * @property itemValue the item value as typed, shown as the suggested deposit; same rule as
 *   [pricePerDay].
 * @property photos the URIs of the photos taken, in order. Reordering photos (SCREENS.md) comes
 *   later.
 * @property hasAttemptedPublish true once the user pressed "Publish", so the screen can show the
 *   errors of empty fields from then on.
 * @property isPublishing true while the listing is being published; "Publish" is disabled then.
 */
data class CreateListingUiState(
    val title: String = "",
    val description: String = "",
    val category: ListingCategory? = null,
    val pricePerDay: String = "",
    val itemValue: String = "",
    val availableFrom: LocalDate? = null,
    val availableTo: LocalDate? = null,
    val pickupAddress: String = "",
    val dropOffAddress: String = "",
    val photos: List<String> = emptyList(),
    val hasAttemptedPublish: Boolean = false,
    val isPublishing: Boolean = false,
) {
  val isTitleBlank: Boolean
    get() = title.isBlank()

  val isDescriptionBlank: Boolean
    get() = description.isBlank()

  val isCategoryMissing: Boolean
    get() = category == null

  val isPickupAddressBlank: Boolean
    get() = pickupAddress.isBlank()

  val isDropOffAddressBlank: Boolean
    get() = dropOffAddress.isBlank()

  /** How many photos are still needed to reach [Listing.MIN_PHOTOS], 0 if there are enough. */
  val missingPhotos: Int
    get() = (Listing.MIN_PHOTOS - photos.size).coerceAtLeast(0)

  val canAddPhoto: Boolean
    get() = photos.size < Listing.MAX_PHOTOS

  val isPricePerDayValid: Boolean
    get() = pricePerDay.isWholeNumberAboveZero()

  val isItemValueValid: Boolean
    get() = itemValue.isWholeNumberAboveZero()

  /**
   * True when both dates are set and the window ends after it starts, so it spans at least 2 days.
   */
  val isAvailabilityValid: Boolean
    get() = availableFrom != null && availableTo != null && availableFrom.isBefore(availableTo)

  /** True when "Publish" would succeed: every check passes and nothing is already running. */
  val canPublish: Boolean
    get() =
        !isPublishing &&
            !isTitleBlank &&
            !isDescriptionBlank &&
            !isCategoryMissing &&
            photos.size in Listing.MIN_PHOTOS..Listing.MAX_PHOTOS &&
            isPricePerDayValid &&
            isItemValueValid &&
            isAvailabilityValid &&
            !isPickupAddressBlank &&
            !isDropOffAddressBlank
}

/** Holds the Create listing form while the user fills it in. */
class CreateListingViewModel : ViewModel() {

  private val _uiState = MutableStateFlow(CreateListingUiState())
  val uiState: StateFlow<CreateListingUiState> = _uiState.asStateFlow()

  fun onTitleChange(title: String) = _uiState.update { it.copy(title = title) }

  fun onDescriptionChange(description: String) = _uiState.update {
    it.copy(description = description)
  }

  fun onCategoryChange(category: ListingCategory) = _uiState.update { it.copy(category = category) }

  fun onPricePerDayChange(pricePerDay: String) = _uiState.update {
    it.copy(pricePerDay = pricePerDay)
  }

  fun onItemValueChange(itemValue: String) = _uiState.update { it.copy(itemValue = itemValue) }

  fun onPickupAddressChange(pickupAddress: String) = _uiState.update {
    it.copy(pickupAddress = pickupAddress)
  }

  fun onDropOffAddressChange(dropOffAddress: String) = _uiState.update {
    it.copy(dropOffAddress = dropOffAddress)
  }

  /** Sets the availability window, as picked in the date picker. */
  fun onAvailabilityChange(from: LocalDate, to: LocalDate) = _uiState.update {
    it.copy(availableFrom = from, availableTo = to)
  }

  /** Appends the photo at [uri]. Ignored once there are [Listing.MAX_PHOTOS] photos. */
  fun onPhotoAdded(uri: String) = _uiState.update {
    if (it.canAddPhoto) it.copy(photos = it.photos + uri) else it
  }

  /** Removes the photo at [index]. Ignored when there is no photo at that index. */
  fun onRemovePhoto(index: Int) = _uiState.update {
    if (index in it.photos.indices) it.copy(photos = it.photos.filterIndexed { i, _ -> i != index })
    else it
  }

  /**
   * Marks the publish attempt, so the screen shows the errors of empty fields. Saving the listing
   * comes with #102.
   */
  fun onPublish() = _uiState.update { it.copy(hasAttemptedPublish = true) }
}

/** Only the digits 0-9 ("007" is 7), from 1 to [Listing.MAX_AMOUNT]. */
private fun String.isWholeNumberAboveZero(): Boolean =
    isNotEmpty() && all { it in '0'..'9' } && (toIntOrNull() ?: 0) in 1..Listing.MAX_AMOUNT
