// Made with Claude.

package com.swent.polyloop.ui.createlisting

import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingCategory
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateListingViewModelTest {

  private val viewModel = CreateListingViewModel()

  private val state: CreateListingUiState
    get() = viewModel.uiState.value

  private fun addPhotos(count: Int) = repeat(count) { viewModel.onPhotoAdded("photo$it") }

  @Test
  fun startsEmpty() {
    assertEquals(CreateListingUiState(), state)
  }

  /** Applies [change] to a new ViewModel and checks the whole state, so no other field moved. */
  private fun assertChangeGives(
      expected: CreateListingUiState,
      change: CreateListingViewModel.() -> Unit,
  ) {
    val vm = CreateListingViewModel()
    vm.change()
    assertEquals(expected, vm.uiState.value)
  }

  @Test
  fun eachChangeMethodSetsItsFieldAndNothingElse() {
    val from = LocalDate.of(2026, 10, 6)
    val to = LocalDate.of(2026, 10, 20)

    assertChangeGives(CreateListingUiState(title = "Tent")) { onTitleChange("Tent") }
    assertChangeGives(CreateListingUiState(description = "Light tent")) {
      onDescriptionChange("Light tent")
    }
    assertChangeGives(CreateListingUiState(category = ListingCategory.MUSIC)) {
      onCategoryChange(ListingCategory.MUSIC)
    }
    assertChangeGives(CreateListingUiState(pricePerDay = "15")) { onPricePerDayChange("15") }
    assertChangeGives(CreateListingUiState(itemValue = "180")) { onItemValueChange("180") }
    assertChangeGives(CreateListingUiState(pickupAddress = "Route 1, Ecublens")) {
      onPickupAddressChange("Route 1, Ecublens")
    }
    assertChangeGives(CreateListingUiState(dropOffAddress = "Avenue 2, Lausanne")) {
      onDropOffAddressChange("Avenue 2, Lausanne")
    }
    assertChangeGives(CreateListingUiState(availableFrom = from, availableTo = to)) {
      selectDates(from, to)
    }
  }

  @Test
  fun onPhotoAddedAppendsInOrder() {
    viewModel.onPhotoAdded("first")
    viewModel.onPhotoAdded("second")

    assertEquals(CreateListingUiState(photos = listOf("first", "second")), state)
  }

  @Test
  fun onPhotoAddedIsIgnoredAtMaxPhotos() {
    addPhotos(Listing.MAX_PHOTOS)
    val full = state

    viewModel.onPhotoAdded("one too many")

    assertEquals(Listing.MAX_PHOTOS, state.photos.size)
    assertEquals(full, state)
  }

  @Test
  fun onRemovePhotoRemovesThePhotoAtThatIndex() {
    addPhotos(3)

    viewModel.onRemovePhoto(1)

    assertEquals(listOf("photo0", "photo2"), state.photos)
  }

  @Test
  fun onRemovePhotoIgnoresIndexesOutOfRange() {
    addPhotos(3)
    val before = state

    viewModel.onRemovePhoto(-1)
    viewModel.onRemovePhoto(3)

    assertEquals(before, state)
  }

  @Test
  fun onPublishSetsHasAttemptedPublishAndKeepsTheFields() {
    viewModel.onTitleChange("Tent")
    viewModel.onPricePerDayChange("15")
    addPhotos(2)
    val before = state

    viewModel.onPublish()

    assertTrue(state.hasAttemptedPublish)
    assertEquals(before.copy(hasAttemptedPublish = true), state)
  }

  @Test
  fun openDatePickerOpensIt() {
    assertChangeGives(CreateListingUiState(isDatePickerOpen = true)) { openDatePicker() }
  }

  @Test
  fun dismissDatePickerClosesItAndKeepsTheDates() {
    val from = LocalDate.of(2026, 10, 6)
    val to = LocalDate.of(2026, 10, 20)

    assertChangeGives(CreateListingUiState(availableFrom = from, availableTo = to)) {
      selectDates(from, to)
      openDatePicker()
      dismissDatePicker()
    }
  }

  @Test
  fun selectDatesSetsTheWindowAndClosesThePicker() {
    val from = LocalDate.of(2026, 10, 6)
    val to = LocalDate.of(2026, 10, 20)

    assertChangeGives(CreateListingUiState(availableFrom = from, availableTo = to)) {
      openDatePicker()
      selectDates(from, to)
    }
  }
}
