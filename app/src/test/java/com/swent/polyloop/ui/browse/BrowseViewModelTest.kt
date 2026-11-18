// Edited with Claude.

package com.swent.polyloop.ui.browse

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.swent.polyloop.model.auth.AuthRepository
import com.swent.polyloop.model.auth.AuthResult
import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.connectivity.ConnectivityRepository
import com.swent.polyloop.model.connectivity.FakeConnectivityRepository
import com.swent.polyloop.model.listing.BookedRange
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingAddresses
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.model.listing.ListingFilter
import com.swent.polyloop.model.listing.ListingRepository
import com.swent.polyloop.model.listing.ListingStatus
import com.swent.polyloop.model.location.FakeLocationRepository
import com.swent.polyloop.model.location.LocationRepository
import com.swent.polyloop.model.location.MapPoint
import com.swent.polyloop.ui.listingdetail.FakeListingRepository
import java.time.LocalDate
import java.util.Locale
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

  private lateinit var locationRepository: FakeLocationRepository

  private lateinit var originalLocale: Locale

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    repository = FakeListingRepository(listings = listings)
    authRepository = SignedInAuthRepository(AuthUser("me", "me@epfl.ch", "Me"))
    connectivity = FakeConnectivityRepository(online = true)
    locationRepository = FakeLocationRepository()
    // The distance texts use the default locale.
    originalLocale = Locale.getDefault()
    Locale.setDefault(Locale.US)
  }

  @After
  fun tearDown() {
    // The locale is restored even if resetting the dispatcher fails.
    try {
      Dispatchers.resetMain()
    } finally {
      Locale.setDefault(originalLocale)
    }
  }

  @Test
  fun initLoadsListings() {
    val state =
        BrowseViewModel(repository, authRepository, connectivity, locationRepository).uiState.value

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
          override suspend fun getListingsByOwner(
              ownerId: String,
              fromServer: Boolean,
          ): Result<List<Listing>> = Result.success(emptyList())

          override suspend fun getAllListings(): Result<List<Listing>> =
              Result.success(listOf(published, listing("draft", ListingStatus.DRAFT)))

          override suspend fun getListing(id: String): Result<Listing?> = Result.success(null)

          override suspend fun getBookedRanges(id: String) =
              Result.success(emptyList<BookedRange>())

          override suspend fun createListing(
              listing: Listing,
              addresses: ListingAddresses,
              photos: List<Uri>,
          ): Result<String> = error("Not used in this test")
        }

    assertEquals(
        listOf(published),
        BrowseViewModel(unfiltered, authRepository, connectivity, locationRepository)
            .uiState
            .value
            .listings,
    )
  }

  @Test
  fun failureSetsErrorMsgAndStopsLoading() {
    repository.failure = IllegalStateException("Network down")
    val state =
        BrowseViewModel(repository, authRepository, connectivity, locationRepository).uiState.value

    assertEquals("Network down", state.errorMsg)
    assertFalse(state.isLoading)
  }

  @Test
  fun failureWithoutMessageStillSetsAnError() {
    repository.failure = RuntimeException()

    // Blank, not null: the screen shows its generic error text.
    assertEquals(
        "",
        BrowseViewModel(repository, authRepository, connectivity, locationRepository)
            .uiState
            .value
            .errorMsg,
    )
  }

  @Test
  fun failureKeepsPreviouslyLoadedListings() {
    val vm = BrowseViewModel(repository, authRepository, connectivity, locationRepository)
    repository.failure = IllegalStateException("Network down")

    vm.refresh()

    assertEquals("Network down", vm.uiState.value.errorMsg)
    assertEquals(listings, vm.uiState.value.listings)
  }

  @Test
  fun refreshAfterFailureClearsErrorWhileLoading() {
    repository.failure = IllegalStateException("Network down")
    val vm = BrowseViewModel(repository, authRepository, connectivity, locationRepository)

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
        BrowseViewModel(repository, authRepository, connectivity, locationRepository)
            .uiState
            .value
            .listings,
    )
  }

  @Test
  fun ownListingsStayHiddenAfterRefresh() {
    val vm = BrowseViewModel(repository, authRepository, connectivity, locationRepository)
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
        BrowseViewModel(repository, authRepository, connectivity, locationRepository)
            .uiState
            .value
            .listings,
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

    val state =
        BrowseViewModel(repository, authRepository, unknown, locationRepository).uiState.value

    assertTrue(state.isLoading)
    assertFalse(state.isOffline)
  }

  @Test
  fun startingOfflineShowsOfflineStateWithoutLoading() {
    connectivity.setOnline(false)

    val state =
        BrowseViewModel(repository, authRepository, connectivity, locationRepository).uiState.value

    assertTrue(state.isOffline)
    assertFalse(state.isLoading)
    assertTrue(state.listings.isEmpty())
  }

  @Test
  fun onlineThenOfflineThenOnlineKeepsThenReloadsTheListings() {
    val vm = BrowseViewModel(repository, authRepository, connectivity, locationRepository)
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
    val vm = BrowseViewModel(repository, authRepository, connectivity, locationRepository)
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
    val vm = BrowseViewModel(repository, authRepository, connectivity, locationRepository)
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
    val vm = BrowseViewModel(repository, authRepository, connectivity, locationRepository)

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
    return BrowseViewModel(repository, authRepository, connectivity, locationRepository)
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

  // ---------- list or map ----------

  @Test
  fun listIsShownByDefault() =
      assertEquals(
          BrowseView.LIST,
          BrowseViewModel(repository, authRepository, connectivity, locationRepository)
              .uiState
              .value
              .view,
      )

  @Test
  fun showMapThenShowListSwitchesTheViewAndKeepsTheFilter() {
    val vm = filterViewModel()
    vm.setQuery("drill")

    vm.showMap()
    assertEquals(BrowseView.MAP, vm.uiState.value.view)
    assertEquals(listOf(drill), vm.uiState.value.visibleListings)

    vm.showList()
    assertEquals(BrowseView.LIST, vm.uiState.value.view)
    assertEquals(listOf(drill), vm.uiState.value.visibleListings)
  }

  @Test
  fun refreshKeepsTheMapView() {
    val vm = filterViewModel()
    vm.showMap()

    vm.refresh()

    assertEquals(BrowseView.MAP, vm.uiState.value.view)
  }

  // ---------- pins ----------

  private val ecublens = MapPoint(46.52, 6.57)
  private val renens = MapPoint(46.54, 6.59)

  @Test
  fun listingsAtTheSamePointShareOnePinAndListingsWithoutAPointHaveNone() {
    val tentHere = tent.copy(pickupPoint = ecublens)
    val drillThere = drill.copy(pickupPoint = renens)
    val bikeHere = listing("bike").copy(pickupPoint = ecublens)
    repository.listings = listOf(tentHere, projector, drillThere, bikeHere)

    val pins =
        BrowseViewModel(repository, authRepository, connectivity, locationRepository)
            .uiState
            .value
            .pins

    assertEquals(
        listOf(
            BrowsePin(ecublens, listOf(tentHere, bikeHere)),
            BrowsePin(renens, listOf(drillThere)),
        ),
        pins,
    )
  }

  @Test
  fun pinsFollowTheSearchAndCategories() {
    val tentHere = tent.copy(pickupPoint = ecublens)
    val projectorHere = projector.copy(pickupPoint = ecublens)
    val drillThere = drill.copy(pickupPoint = renens)
    repository.listings = listOf(tentHere, projectorHere, drillThere)
    val vm = BrowseViewModel(repository, authRepository, connectivity, locationRepository)

    vm.setQuery("drill")
    assertEquals(listOf(BrowsePin(renens, listOf(drillThere))), vm.uiState.value.pins)

    vm.setQuery("")
    vm.toggleCategory(ListingCategory.ELECTRONICS)
    assertEquals(listOf(BrowsePin(ecublens, listOf(projectorHere))), vm.uiState.value.pins)
  }

  @Test
  fun noPinsWhenNoListingHasAPoint() =
      assertEquals(emptyList<BrowsePin>(), filterViewModel().uiState.value.pins)

  // ---------- selected pin ----------

  private val tentHere = tent.copy(pickupPoint = ecublens)
  private val projectorHere = projector.copy(pickupPoint = ecublens)
  private val drillThere = drill.copy(pickupPoint = renens)

  /** A ViewModel whose tent and projector share the Ecublens pin, and the drill is in Renens. */
  private fun pinViewModel(): BrowseViewModel {
    repository.listings = listOf(tentHere, projectorHere, drillThere)
    return BrowseViewModel(repository, authRepository, connectivity, locationRepository)
  }

  @Test
  fun selectingAPinGivesItsListingsAndClearingTheSelectionRemovesThem() {
    val vm = pinViewModel()
    assertNull(vm.uiState.value.selectedPin)

    vm.selectPin(ecublens)
    assertEquals(BrowsePin(ecublens, listOf(tentHere, projectorHere)), vm.uiState.value.selectedPin)

    vm.clearSelection()
    assertNull(vm.uiState.value.selectedPoint)
    assertNull(vm.uiState.value.selectedPin)
  }

  @Test
  fun filterKeepingSomeListingsOfTheSelectedPinKeepsTheSelection() {
    val vm = pinViewModel()
    vm.selectPin(ecublens)

    vm.toggleCategory(ListingCategory.ELECTRONICS)

    assertEquals(BrowsePin(ecublens, listOf(projectorHere)), vm.uiState.value.selectedPin)
  }

  @Test
  fun filterHidingTheSelectedPinDropsTheSelectionForGood() {
    val vm = pinViewModel()
    vm.selectPin(ecublens)

    vm.setQuery("drill")
    assertNull(vm.uiState.value.selectedPoint)
    assertNull(vm.uiState.value.selectedPin)

    vm.setQuery("")
    assertNull(vm.uiState.value.selectedPin)
  }

  @Test
  fun reloadWithoutTheSelectedPinDropsTheSelectionForGood() {
    val vm = pinViewModel()
    vm.selectPin(ecublens)

    // Both Ecublens listings were unpublished meanwhile.
    repository.listings = listOf(drillThere)
    vm.refresh()
    assertNull(vm.uiState.value.selectedPoint)

    repository.listings = listOf(tentHere, projectorHere, drillThere)
    vm.refresh()
    assertNull(vm.uiState.value.selectedPin)
  }

  @Test
  fun selectingAPointWithoutAPinSelectsNothing() {
    val vm = pinViewModel()

    vm.selectPin(MapPoint(46.60, 6.70))

    assertNull(vm.uiState.value.selectedPoint)
    assertNull(vm.uiState.value.selectedPin)
  }

  @Test
  fun showingTheListClearsTheSelection() {
    val vm = pinViewModel()
    vm.showMap()
    vm.selectPin(ecublens)

    vm.showList()
    vm.showMap()

    assertNull(vm.uiState.value.selectedPoint)
    assertNull(vm.uiState.value.selectedPin)
  }

  // ---------- distance ----------

  private val phone = MapPoint(46.52, 6.57)

  // Meters per degree of latitude on the sphere distanceMeters uses.
  private val metersPerDegree = 6_371_000.0 * Math.PI / 180

  /** A point [meters] north of [phone], so its distance from it is [meters]. */
  private fun north(meters: Double) = MapPoint(phone.latitude + meters / metersPerDegree, 6.57)

  private fun at(id: String, meters: Double) = listing(id).copy(pickupPoint = north(meters))

  private fun distanceViewModel(vararg ls: Listing, position: MapPoint? = phone): BrowseViewModel {
    repository.listings = ls.toList()
    locationRepository.position = position
    return BrowseViewModel(repository, authRepository, connectivity, locationRepository)
  }

  private fun BrowseViewModel.ids() = uiState.value.listings.map { it.id }

  @Test
  fun listingsAreSortedByDistanceWithTheirTexts() {
    val vm = distanceViewModel(at("far", 3300.0), at("near", 350.0), at("mid", 1200.0))

    assertEquals(listOf("near", "mid", "far"), vm.ids())
    assertEquals(
        mapOf("near" to "350 m", "mid" to "1.2 km", "far" to "3.3 km"),
        vm.uiState.value.distanceTexts,
    )
  }

  @Test
  fun refusedPermissionKeepsTheOrderAndShowsNoDistance() {
    val vm = distanceViewModel(at("far", 3300.0), at("near", 350.0), position = null)

    assertEquals(listOf("far", "near"), vm.ids())
    assertTrue(vm.uiState.value.distanceTexts.isEmpty())
  }

  @Test
  fun listingWithoutAPointComesLastWithNoDistance() {
    val noPoint = listing("nowhere")
    val vm = distanceViewModel(noPoint, at("far", 3300.0), at("near", 350.0))

    assertEquals(listOf("near", "far", "nowhere"), vm.ids())
    assertEquals(setOf("near", "far"), vm.uiState.value.distanceTexts.keys)
  }

  @Test
  fun listingsWithoutADistanceKeepTheirOrderAmongThemselves() {
    val vm = distanceViewModel(listing("b"), at("near", 350.0), listing("a"))

    assertEquals(listOf("near", "b", "a"), vm.ids())
  }

  @Test
  fun distanceTextsRoundAtTheKilometerEdge() {
    val vm =
        distanceViewModel(at("m", 354.0), at("edge", 997.0), at("km", 1049.0), at("big", 12_360.0))

    assertEquals(
        mapOf("m" to "350 m", "edge" to "1.0 km", "km" to "1.0 km", "big" to "12.4 km"),
        vm.uiState.value.distanceTexts,
    )
  }

  @Test
  fun distanceTextsUseTheDeviceLocale() {
    Locale.setDefault(Locale.FRANCE)

    val vm = distanceViewModel(at("mid", 1200.0))

    assertEquals("1,2 km", vm.uiState.value.distanceTexts["mid"])
  }

  @Test
  fun distanceOrderSurvivesASearchWithEqualRelevance() {
    val vm = distanceViewModel(at("far", 3300.0), at("near", 350.0), at("mid", 1200.0))

    vm.setQuery("item")

    assertEquals(listOf("near", "mid", "far"), vm.uiState.value.visibleListings.map { it.id })
  }

  @Test
  fun aBetterMatchStillRanksBeforeANearerListing() {
    val far = at("far", 3300.0).copy(title = "Mountain bike")
    val near = at("near", 350.0).copy(title = "Tent", description = "Fits a bike")
    val vm = distanceViewModel(far, near)

    vm.setQuery("bike")

    assertEquals(listOf("far", "near"), vm.uiState.value.visibleListings.map { it.id })
  }

  @Test
  fun pinsFollowTheDistanceOrder() {
    val farPoint = north(3300.0)
    val nearPoint = north(350.0)
    val far = listing("far").copy(pickupPoint = farPoint)
    val near = listing("near").copy(pickupPoint = nearPoint)
    val near2 = listing("near2").copy(pickupPoint = nearPoint)
    val vm = distanceViewModel(far, near2, near)

    assertEquals(
        listOf(BrowsePin(nearPoint, listOf(near2, near)), BrowsePin(farPoint, listOf(far))),
        vm.uiState.value.pins,
    )
  }

  @Test
  fun selectedPinSurvivesReadingThePositionAgain() {
    val vm = distanceViewModel(at("far", 3300.0), at("near", 350.0))
    vm.selectPin(north(350.0))

    locationRepository.position = null
    vm.refreshLocation()

    assertEquals(north(350.0), vm.uiState.value.selectedPoint)
    assertEquals(listOf("far", "near"), vm.ids())
  }

  @Test
  fun positionIsReadAgainAfterRefreshLocation() {
    val vm = distanceViewModel(at("far", 3300.0), at("near", 350.0), position = null)
    assertEquals(listOf("far", "near"), vm.ids())

    locationRepository.position = phone
    vm.refreshLocation()

    assertEquals(listOf("near", "far"), vm.ids())
    assertEquals("350 m", vm.uiState.value.distanceTexts["near"])
  }

  @Test
  fun positionIsReadAgainAfterAReload() {
    val vm = distanceViewModel(at("far", 3300.0), at("near", 350.0), position = null)

    locationRepository.position = phone
    vm.refresh()

    assertEquals(listOf("near", "far"), vm.ids())
  }

  @Test
  fun readingTheSamePositionAgainKeepsTheListAndItsSelection() {
    val vm = distanceViewModel(at("far", 3300.0), at("near", 350.0), at("mid", 1200.0))
    vm.selectPin(north(1200.0))
    val before = vm.uiState.value
    val reads = locationRepository.reads

    vm.refreshLocation()

    assertEquals(reads + 1, locationRepository.reads)
    assertEquals(listOf("near", "mid", "far"), vm.ids())
    assertEquals(before, vm.uiState.value)
  }

  @Test
  fun creatingTheViewModelDoesNotReadThePositionByItself() {
    connectivity.setOnline(false)
    val reads = locationRepository.reads
    BrowseViewModel(repository, authRepository, connectivity, locationRepository)

    assertEquals(reads, locationRepository.reads)
  }

  @Test
  fun losingThePositionRemovesTheDistances() {
    val vm = distanceViewModel(at("far", 3300.0), at("near", 350.0))
    assertEquals(listOf("near", "far"), vm.ids())

    locationRepository.position = null
    vm.refreshLocation()

    assertEquals(listOf("far", "near"), vm.ids())
    assertTrue(vm.uiState.value.distanceTexts.isEmpty())
  }

  /** A [LocationRepository] whose every read waits for the test to answer it. */
  private class GatedLocationRepository : LocationRepository {
    val reads = mutableListOf<CompletableDeferred<MapPoint?>>()

    override suspend fun currentPosition(): MapPoint? =
        CompletableDeferred<MapPoint?>().also { reads += it }.await()
  }

  @Test
  fun theLatestReadWinsEvenWhenAnOlderOneEndsLast() {
    val gated = GatedLocationRepository()
    repository.listings = listOf(at("far", 3300.0), at("near", 350.0))
    val vm = BrowseViewModel(repository, authRepository, connectivity, gated)
    val before = gated.reads.size

    vm.refreshLocation() // older
    vm.refreshLocation() // newer
    // The newer read answers with the phone south of both listings' far end, the older one late
    // with a position next to "far"; the older one was cancelled, so it must not apply.
    gated.reads[before + 1].complete(phone)
    gated.reads[before].complete(north(3300.0))

    assertEquals(listOf("near", "far"), vm.ids())
  }

  @Test
  fun factoryCreatesBrowseViewModel() {
    val vm =
        BrowseViewModel.Factory(repository, authRepository, connectivity, locationRepository)
            .create(BrowseViewModel::class.java)
    assertEquals(listings, vm.uiState.value.listings)
  }

  @Test(expected = IllegalArgumentException::class)
  fun factoryRejectsOtherViewModels() {
    BrowseViewModel.Factory(repository, authRepository, connectivity, locationRepository)
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
