// Made with Claude.

package com.swent.polyloop.ui.listingdetail

import androidx.lifecycle.ViewModel
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.model.listing.ListingStatus
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ListingDetailViewModelTest {

  private val listing =
      Listing(
          id = "listing1",
          ownerId = "user1",
          title = "Camping tent, 2 people",
          description = "Light 2-person tent, used three times.",
          category = ListingCategory.SPORTS_OUTDOOR,
          photoUrls = List(3) { "https://example.com/photo$it.jpg" },
          pricePerDay = 15,
          itemValue = 180,
          availableFrom = LocalDate.of(2026, 10, 6),
          availableTo = LocalDate.of(2026, 10, 20),
          status = ListingStatus.PUBLISHED,
      )

  private val oct6 = LocalDate.of(2026, 10, 6)
  private val oct8 = LocalDate.of(2026, 10, 8)

  private lateinit var repository: FakeListingRepository

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    repository = FakeListingRepository(listings = listOf(listing))
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun viewModel(id: String = listing.id) = ListingDetailViewModel(id, repository)

  @Test
  fun showsLoadingThenHoldsTheListing() {
    val gate = CompletableDeferred<Unit>()
    repository.gate = gate
    val vm = viewModel()
    assertTrue(vm.uiState.value.isLoading)

    gate.complete(Unit)

    val state = vm.uiState.value
    assertFalse(state.isLoading)
    assertEquals(listing, state.listing)
    assertFalse(state.listingNotFound)
    assertNull(state.errorMsg)
  }

  @Test
  fun missingOrUnpublishedListingIsNotFound() {
    assertTrue(viewModel(id = "unknown").uiState.value.listingNotFound)

    repository.listings = listOf(listing.copy(status = ListingStatus.DRAFT))
    val state = viewModel().uiState.value
    assertTrue(state.listingNotFound)
    assertNull(state.listing)
  }

  @Test
  fun repositoryFailureSetsErrorMsg() {
    repository.failure = IllegalStateException("Network down")
    assertEquals("Network down", viewModel().uiState.value.errorMsg)

    repository.failure = RuntimeException()
    assertFalse(viewModel().uiState.value.errorMsg.isNullOrBlank())
  }

  @Test
  fun retryClearsErrorAndLoadsAgain() {
    repository.failure = IllegalStateException("Network down")
    val vm = viewModel()

    repository.failure = null
    vm.retry()

    assertNull(vm.uiState.value.errorMsg)
    assertEquals(listing, vm.uiState.value.listing)
    assertEquals(2, repository.getListingCalls)
  }

  @Test
  fun validRangeSetsDaysPriceAndCanRequest() {
    val vm = viewModel()
    assertFalse(vm.uiState.value.canRequest)

    vm.selectDates(oct6, oct8)

    val state = vm.uiState.value
    assertEquals(oct6, state.startDate)
    assertEquals(oct8, state.endDate)
    assertEquals(3, state.numberOfDays)
    assertEquals(45, state.totalPrice)
    assertTrue(state.canRequest)
  }

  @Test
  fun invalidRangesAreIgnored() {
    val vm = viewModel()
    vm.selectDates(oct6, oct8)
    val before = vm.uiState.value

    vm.selectDates(oct6.minusDays(1), oct8) // starts before the window
    vm.selectDates(oct6, listing.availableTo.plusDays(1)) // ends after the window
    vm.selectDates(oct8, oct6) // ends before it starts

    assertEquals(before, vm.uiState.value)
  }

  @Test
  fun clearDatesRemovesSelection() {
    val vm = viewModel()
    vm.selectDates(oct6, oct8)
    vm.clearDates()

    assertNull(vm.uiState.value.startDate)
    assertNull(vm.uiState.value.endDate)
  }

  @Test
  fun datePickerOpensDismissesAndClosesOnValidPick() {
    val vm = viewModel()
    vm.selectDates(oct6, oct8)

    vm.openDatePicker()
    assertTrue(vm.uiState.value.isDatePickerOpen)
    vm.dismissDatePicker()
    assertFalse(vm.uiState.value.isDatePickerOpen)
    assertEquals(oct6, vm.uiState.value.startDate)

    vm.openDatePicker()
    vm.selectDates(oct8, oct8)
    assertFalse(vm.uiState.value.isDatePickerOpen)
  }

  @Test
  fun datesAndPickerAreIgnoredWithoutListing() {
    repository.listings = emptyList()
    val vm = viewModel()

    vm.openDatePicker()
    vm.selectDates(oct6, oct8)

    assertFalse(vm.uiState.value.isDatePickerOpen)
    assertNull(vm.uiState.value.startDate)
    assertFalse(ListingDetailUiState(startDate = oct6, endDate = oct8).canRequest)
    assertEquals(0, ListingDetailUiState(startDate = oct6, endDate = oct8).totalPrice)
  }

  @Test
  fun factoryCreatesViewModelForTheListing() {
    val vm =
        ListingDetailViewModel.Factory(listing.id, repository)
            .create(ListingDetailViewModel::class.java)
    assertEquals(listing, vm.uiState.value.listing)
  }

  @Test(expected = IllegalArgumentException::class)
  fun factoryRejectsOtherViewModels() {
    ListingDetailViewModel.Factory(listing.id, repository).create(OtherViewModel::class.java)
  }

  private class OtherViewModel : ViewModel()
}
