// Edited with Claude.

package com.swent.polyloop.ui.browse

import androidx.lifecycle.ViewModel
import com.swent.polyloop.model.auth.AuthRepository
import com.swent.polyloop.model.auth.AuthResult
import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.connectivity.ConnectivityRepository
import com.swent.polyloop.model.connectivity.FakeConnectivityRepository
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.model.listing.ListingFilter
import com.swent.polyloop.model.listing.ListingRepository
import com.swent.polyloop.model.listing.ListingStatus
import com.swent.polyloop.ui.listingdetail.FakeListingRepository
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
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

  // Signed in as "me", who owns none of the listings above.
  private lateinit var authRepository: SignedInAuthRepository

  private lateinit var connectivity: FakeConnectivityRepository

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    repository = FakeListingRepository(listings = listings)
    authRepository = SignedInAuthRepository(AuthUser("me", "me@epfl.ch", "Me"))
    connectivity = FakeConnectivityRepository(online = true)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun initLoadsListings() {
    val state = BrowseViewModel(repository, authRepository, connectivity).uiState.value

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
          override suspend fun getListingsByOwner(ownerId: String): Result<List<Listing>> =
              Result.success(emptyList())

          override suspend fun getAllListings(): Result<List<Listing>> =
              Result.success(listOf(published, listing("draft", ListingStatus.DRAFT)))

          override suspend fun getListing(id: String): Result<Listing?> = Result.success(null)
        }

    assertEquals(
        listOf(published),
        BrowseViewModel(unfiltered, authRepository, connectivity).uiState.value.listings,
    )
  }

  @Test
  fun failureSetsErrorMsgAndStopsLoading() {
    repository.failure = IllegalStateException("Network down")
    val state = BrowseViewModel(repository, authRepository, connectivity).uiState.value

    assertEquals("Network down", state.errorMsg)
    assertFalse(state.isLoading)
  }

  @Test
  fun failureWithoutMessageStillSetsAnError() {
    repository.failure = RuntimeException()

    // Blank, not null: the screen shows its generic error text.
    assertEquals(
        "",
        BrowseViewModel(repository, authRepository, connectivity).uiState.value.errorMsg,
    )
  }

  @Test
  fun failureKeepsPreviouslyLoadedListings() {
    val vm = BrowseViewModel(repository, authRepository, connectivity)
    repository.failure = IllegalStateException("Network down")

    vm.refresh()

    assertEquals("Network down", vm.uiState.value.errorMsg)
    assertEquals(listings, vm.uiState.value.listings)
  }

  @Test
  fun refreshAfterFailureClearsErrorWhileLoading() {
    repository.failure = IllegalStateException("Network down")
    val vm = BrowseViewModel(repository, authRepository, connectivity)

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

  // --- Own listings ---

  private val myListing = listing("my-camera").copy(ownerId = "me")

  @Test
  fun ownListingsAreHidden() {
    repository.listings = listings + myListing

    assertEquals(
        listings,
        BrowseViewModel(repository, authRepository, connectivity).uiState.value.listings,
    )
  }

  @Test
  fun ownListingsStayHiddenAfterRefresh() {
    val vm = BrowseViewModel(repository, authRepository, connectivity)
    val myBike = listing("my-bike").copy(ownerId = "me")
    repository.listings = listings + myListing + myBike

    vm.refresh()

    assertEquals(listings, vm.uiState.value.listings)
  }

  @Test
  fun allListingsShownWhenNobodySignedIn() {
    repository.listings = listings + myListing
    authRepository.user = null

    assertEquals(
        listings + myListing,
        BrowseViewModel(repository, authRepository, connectivity).uiState.value.listings,
    )
  }

  // --- Offline ---

  @Test
  fun startsLoadingUntilTheNetworkStatusIsKnown() {
    // A network status that never arrives, so the ViewModel cannot decide yet.
    val unknown =
        object : ConnectivityRepository {
          override val isOnline = emptyFlow<Boolean>()
        }

    val state = BrowseViewModel(repository, authRepository, unknown).uiState.value

    assertTrue(state.isLoading)
    assertFalse(state.isOffline)
  }

  @Test
  fun startingOfflineShowsOfflineStateWithoutLoading() {
    connectivity.setOnline(false)

    val state = BrowseViewModel(repository, authRepository, connectivity).uiState.value

    assertTrue(state.isOffline)
    assertFalse(state.isLoading)
    assertTrue(state.listings.isEmpty())
  }

  @Test
  fun onlineThenOfflineThenOnlineKeepsThenReloadsTheListings() {
    val vm = BrowseViewModel(repository, authRepository, connectivity)
    assertEquals(listings, vm.uiState.value.listings)

    connectivity.setOnline(false)
    assertTrue(vm.uiState.value.isOffline)
    // Kept, though hidden: they come back with the connection.
    assertEquals(listings, vm.uiState.value.listings)

    val tent = listing("tent")
    repository.listings = listOf(tent)
    connectivity.setOnline(true)

    val state = vm.uiState.value
    assertFalse(state.isOffline)
    assertFalse(state.isLoading)
    assertEquals(listOf(tent), state.listings)
  }

  @Test
  fun reconnectingClearsALoadError() {
    repository.failure = IllegalStateException("Network down")
    val vm = BrowseViewModel(repository, authRepository, connectivity)
    connectivity.setOnline(false)
    repository.failure = null

    connectivity.setOnline(true)

    assertNull(vm.uiState.value.errorMsg)
    assertEquals(listings, vm.uiState.value.listings)
  }

  @Test
  fun goingOfflineCancelsTheLoadInProgress() {
    val gate = CompletableDeferred<Unit>()
    repository.gate = gate
    val vm = BrowseViewModel(repository, authRepository, connectivity)
    assertTrue(vm.uiState.value.isLoading)

    connectivity.setOnline(false)
    gate.complete(Unit)

    assertFalse(vm.uiState.value.isLoading)
    assertTrue(vm.uiState.value.listings.isEmpty())
  }

  @Test
  fun aNewLoadReplacesTheOneInProgress() {
    val firstGate = CompletableDeferred<Unit>()
    repository.gate = firstGate
    val vm = BrowseViewModel(repository, authRepository, connectivity)

    val tent = listing("tent")
    repository.listings = listOf(tent)
    repository.gate = null
    vm.refresh()
    // The first load would return the same listings, but it was cancelled: nothing changes.
    repository.listings = listings
    firstGate.complete(Unit)

    assertEquals(listOf(tent), vm.uiState.value.listings)
  }

  // --- Filter ---

  private val tent = listing("tent").copy(category = ListingCategory.SPORTS_OUTDOOR)
  private val projector = listing("projector").copy(category = ListingCategory.ELECTRONICS)
  private val drill = listing("drill").copy(category = ListingCategory.TOOLS_DIY)

  private fun filterViewModel(): BrowseViewModel {
    repository.listings = listOf(tent, projector, drill)
    return BrowseViewModel(repository, authRepository, connectivity)
  }

  @Test
  fun allListingsAreVisibleWithoutFilter() {
    assertEquals(listOf(tent, projector, drill), filterViewModel().uiState.value.visibleListings)
  }

  @Test
  fun setQueryShowsOnlyMatchingListings() {
    val vm = filterViewModel()

    vm.setQuery("drill")

    assertEquals("drill", vm.uiState.value.filter.query)
    assertEquals(listOf(drill), vm.uiState.value.visibleListings)
  }

  @Test
  fun toggleCategorySelectsThenDeselectsIt() {
    val vm = filterViewModel()

    vm.toggleCategory(ListingCategory.ELECTRONICS)
    vm.toggleCategory(ListingCategory.SPORTS_OUTDOOR)
    assertEquals(
        setOf(ListingCategory.ELECTRONICS, ListingCategory.SPORTS_OUTDOOR),
        vm.uiState.value.filter.categories,
    )
    assertEquals(listOf(tent, projector), vm.uiState.value.visibleListings)

    vm.toggleCategory(ListingCategory.ELECTRONICS)
    assertEquals(setOf(ListingCategory.SPORTS_OUTDOOR), vm.uiState.value.filter.categories)
    assertEquals(listOf(tent), vm.uiState.value.visibleListings)
  }

  @Test
  fun clearCategoriesKeepsTheQuery() {
    val vm = filterViewModel()
    vm.setQuery("item")
    vm.toggleCategory(ListingCategory.ELECTRONICS)

    vm.clearCategories()

    assertEquals(ListingFilter(query = "item"), vm.uiState.value.filter)
    assertEquals(listOf(tent, projector, drill), vm.uiState.value.visibleListings)
  }

  @Test
  fun refreshKeepsTheFilterAndAppliesItToTheNewListings() {
    val vm = filterViewModel()
    vm.toggleCategory(ListingCategory.ELECTRONICS)
    val speaker = listing("speaker").copy(category = ListingCategory.ELECTRONICS)
    repository.listings = listOf(tent, projector, drill, speaker)

    vm.refresh()

    assertEquals(setOf(ListingCategory.ELECTRONICS), vm.uiState.value.filter.categories)
    assertEquals(listOf(projector, speaker), vm.uiState.value.visibleListings)
  }

  @Test
  fun factoryCreatesBrowseViewModel() {
    val vm =
        BrowseViewModel.Factory(repository, authRepository, connectivity)
            .create(BrowseViewModel::class.java)
    assertEquals(listings, vm.uiState.value.listings)
  }

  @Test(expected = IllegalArgumentException::class)
  fun factoryRejectsOtherViewModels() {
    BrowseViewModel.Factory(repository, authRepository, connectivity)
        .create(OtherViewModel::class.java)
  }

  private class OtherViewModel : ViewModel()

  /** An [AuthRepository] where [user] is signed in, or nobody when null; Browse only reads it. */
  private class SignedInAuthRepository(var user: AuthUser?) : AuthRepository {
    override suspend fun signUp(name: String, email: String, password: String) =
        AuthResult.Success(Unit)

    override suspend fun signIn(email: String, password: String): AuthResult<AuthUser> =
        error("Not used by Browse")

    override suspend fun resendVerificationEmail(email: String, password: String) =
        AuthResult.Success(Unit)

    override fun getCurrentUser(): AuthUser? = user

    override fun signOut() {}
  }
}
