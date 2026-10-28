// Made with Claude.

package com.swent.polyloop.ui.createlisting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.swent.polyloop.model.auth.FakeAuthRepository
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.ui.listingdetail.FakeListingRepository
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateListingViewModelTest {

  private val viewModel = CreateListingViewModel(FakeListingRepository(), FakeAuthRepository())

  private val state: CreateListingUiState
    get() = viewModel.uiState.value

  private fun addPhotos(count: Int) = repeat(count) { viewModel.onPhotoTaken("photo$it") }

  @Test
  fun startsEmpty() {
    assertEquals(CreateListingUiState(), state)
  }

  /** Applies [change] to a new ViewModel and checks the whole state, so no other field moved. */
  private fun assertChangeGives(
      expected: CreateListingUiState,
      change: CreateListingViewModel.() -> Unit,
  ) {
    val vm = CreateListingViewModel(FakeListingRepository(), FakeAuthRepository())
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
  fun onPhotoTakenAppendsInOrder() {
    viewModel.onPhotoTaken("first")
    viewModel.onPhotoTaken("second")

    assertEquals(CreateListingUiState(photos = listOf("first", "second")), state)
  }

  @Test
  fun onPhotoTakenIsIgnoredAtMaxPhotos() {
    addPhotos(Listing.MAX_PHOTOS)
    val full = state

    viewModel.onPhotoTaken("one too many")

    assertEquals(Listing.MAX_PHOTOS, state.photos.size)
    assertEquals(full, state)
  }

  @Test
  fun openCameraOpensTheCameraAndNothingElse() {
    assertChangeGives(CreateListingUiState(isCameraOpen = true)) { openCamera() }
  }

  @Test
  fun openCameraAtMaxPhotosLeavesTheCameraClosed() {
    addPhotos(Listing.MAX_PHOTOS)

    viewModel.openCamera()

    assertFalse(state.isCameraOpen)
  }

  @Test
  fun closeCameraClosesTheCameraAndKeepsThePhotos() {
    addPhotos(2)
    viewModel.openCamera()

    viewModel.closeCamera()

    assertEquals(CreateListingUiState(photos = listOf("photo0", "photo1")), state)
  }

  @Test
  fun onPhotoTakenKeepsTheCameraOpenForTheNextPhoto() {
    viewModel.openCamera()

    viewModel.onPhotoTaken("first")
    viewModel.onPhotoTaken("second")

    assertEquals(
        CreateListingUiState(photos = listOf("first", "second"), isCameraOpen = true),
        state,
    )
  }

  @Test
  fun onPhotoTakenAtMaxPhotosKeepsTheCameraOpenAndTheSamePhotos() {
    viewModel.openCamera()
    addPhotos(Listing.MAX_PHOTOS)
    val full = state

    viewModel.onPhotoTaken("one too many")

    assertEquals(full, state)
    assertTrue(state.isCameraOpen)
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

  private val deleted = mutableListOf<String>()

  /** A ViewModel that records the deleted photos, held by [store] so the test can clear it. */
  private fun recordingViewModel(store: ViewModelStore = ViewModelStore()): CreateListingViewModel =
      ViewModelProvider(
          store,
          object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                CreateListingViewModel(
                    FakeListingRepository(),
                    FakeAuthRepository(),
                    deletePhoto = { deleted += it },
                )
                    as T
          },
      )[CreateListingViewModel::class.java]

  @Test
  fun onRemovePhotoDeletesTheRemovedFileOnly() {
    val vm = recordingViewModel()
    repeat(3) { vm.onPhotoTaken("photo$it") }

    vm.onRemovePhoto(1)

    assertEquals(listOf("photo1"), deleted)
    assertEquals(listOf("photo0", "photo2"), vm.uiState.value.photos)
  }

  @Test
  fun onRemovePhotoOutOfRangeDeletesNothing() {
    val vm = recordingViewModel()
    vm.onPhotoTaken("photo0")

    vm.onRemovePhoto(-1)
    vm.onRemovePhoto(1)

    assertTrue(deleted.isEmpty())
  }

  @Test
  fun aPhotoTakenAtMaxPhotosIsDeletedAndNotAdded() {
    val vm = recordingViewModel()
    repeat(Listing.MAX_PHOTOS) { vm.onPhotoTaken("photo$it") }
    assertTrue(deleted.isEmpty())

    vm.onPhotoTaken("the 11th")

    assertEquals(listOf("the 11th"), deleted)
    assertEquals(Listing.MAX_PHOTOS, vm.uiState.value.photos.size)
  }

  @Test
  fun closingTheCameraDeletesNothing() {
    val vm = recordingViewModel()
    vm.onPhotoTaken("photo0")
    vm.openCamera()

    vm.closeCamera()

    assertTrue(deleted.isEmpty())
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
