// Made with Claude.

package com.swent.polyloop.ui.browse

import androidx.lifecycle.ViewModel
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.model.listing.ListingRepository
import com.swent.polyloop.model.listing.ListingStatus
import com.swent.polyloop.ui.listingdetail.FakeListingRepository
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
class BrowseViewModelTest {

  private fun listing(id: String, status: ListingStatus = ListingStatus.PUBLISHED) =
      Listing(
          id = id,
          ownerId = "user1",
          title = "Item $id",
          description = "Description of $id",
          category = ListingCategory.OTHER,
          photoUrls = List(3) { "https://example.com/$id$it.jpg" },
          pricePerDay = 10,
          itemValue = 100,
          availableFrom = LocalDate.of(2026, 10, 6),
          availableTo = LocalDate.of(2026, 10, 20),
          status = status,
      )

  private val listings = listOf(listing("tent"), listing("drill"))

  private lateinit var repository: FakeListingRepository

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    repository = FakeListingRepository(listings = listings)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun initLoadsListings() {
    val state = BrowseViewModel(repository).uiState.value

    assertEquals(listings, state.listings)
    assertFalse(state.isLoading)
    assertNull(state.errorMsg)
  }

  @Test
  fun draftListingIsHidden() {
    val published = listing("tent")
    // Returns the DRAFT unfiltered, unlike FakeListingRepository, so the ViewModel must hide it.
    val unfiltered =
        object : ListingRepository {
          override suspend fun getAllListings(): Result<List<Listing>> =
              Result.success(listOf(published, listing("draft", ListingStatus.DRAFT)))

          override suspend fun getListing(id: String): Result<Listing?> = Result.success(null)
        }

    assertEquals(listOf(published), BrowseViewModel(unfiltered).uiState.value.listings)
  }

  @Test
  fun failureSetsErrorMsgAndStopsLoading() {
    repository.failure = IllegalStateException("Network down")
    val state = BrowseViewModel(repository).uiState.value

    assertEquals("Network down", state.errorMsg)
    assertFalse(state.isLoading)
  }

  @Test
  fun failureWithoutMessageStillSetsAnError() {
    repository.failure = RuntimeException()

    // Blank, not null: the screen shows its generic error text.
    assertEquals("", BrowseViewModel(repository).uiState.value.errorMsg)
  }

  @Test
  fun failureKeepsPreviouslyLoadedListings() {
    val vm = BrowseViewModel(repository)
    repository.failure = IllegalStateException("Network down")

    vm.refresh()

    assertEquals("Network down", vm.uiState.value.errorMsg)
    assertEquals(listings, vm.uiState.value.listings)
  }

  @Test
  fun refreshAfterFailureClearsErrorWhileLoading() {
    repository.failure = IllegalStateException("Network down")
    val vm = BrowseViewModel(repository)

    repository.failure = null
    val gate = CompletableDeferred<Unit>()
    repository.gate = gate
    vm.refresh()
    assertTrue(vm.uiState.value.isLoading)
    assertNull(vm.uiState.value.errorMsg)

    gate.complete(Unit)

    assertFalse(vm.uiState.value.isLoading)
    assertEquals(listings, vm.uiState.value.listings)
  }

  @Test
  fun factoryCreatesBrowseViewModel() {
    val vm = BrowseViewModel.Factory(repository).create(BrowseViewModel::class.java)
    assertEquals(listings, vm.uiState.value.listings)
  }

  @Test(expected = IllegalArgumentException::class)
  fun factoryRejectsOtherViewModels() {
    BrowseViewModel.Factory(repository).create(OtherViewModel::class.java)
  }

  private class OtherViewModel : ViewModel()
}
