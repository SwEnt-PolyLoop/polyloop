// Made with Claude.

package com.swent.polyloop.ui.profile

import androidx.lifecycle.ViewModel
import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.auth.FakeAuthRepository
import com.swent.polyloop.model.connectivity.ConnectivityRepository
import com.swent.polyloop.model.connectivity.FakeConnectivityRepository
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.model.listing.ListingRepository
import com.swent.polyloop.model.listing.ListingStatus
import com.swent.polyloop.model.profile.FakeProfileRepository
import com.swent.polyloop.model.profile.UserProfile
import com.swent.polyloop.model.rental.FakeRentalRepository
import com.swent.polyloop.model.rental.Rental
import com.swent.polyloop.model.rental.RentalRepository
import com.swent.polyloop.ui.listingdetail.FakeListingRepository
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

  private val me = UserProfile(uid = "me", name = "John", email = "john@epfl.ch")
  private val jane =
      UserProfile(uid = "jane", name = "Jane", email = "jane@epfl.ch", averageRating = 4.5)

  private fun listing(
      id: String,
      ownerId: String,
      status: ListingStatus = ListingStatus.PUBLISHED,
  ) =
      Listing(
          id = id,
          ownerId = ownerId,
          title = "Item $id",
          description = "Description of $id",
          category = ListingCategory.OTHER,
          photoUrls = List(3) { "https://example.com/$id-$it.jpg" },
          pricePerDay = 10,
          itemValue = 100,
          availableFrom = LocalDate.of(2026, 10, 1),
          availableTo = LocalDate.of(2026, 10, 31),
          status = status,
      )

  private val myTent = listing("tent", ownerId = "me")
  private val myDraft = listing("draft", ownerId = "me", status = ListingStatus.DRAFT)
  private val janesBike = listing("bike", ownerId = "jane")
  private val janesDraft = listing("lamp", ownerId = "jane", status = ListingStatus.DRAFT)

  private lateinit var profiles: FakeProfileRepository
  private lateinit var listings: FakeListingRepository
  private lateinit var rentals: FakeRentalRepository
  private lateinit var auth: FakeAuthRepository
  private lateinit var connectivity: FakeConnectivityRepository

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    profiles = FakeProfileRepository(listOf(me, jane))
    listings = FakeListingRepository(listings = listOf(myTent, myDraft, janesBike, janesDraft))
    rentals = FakeRentalRepository()
    auth =
        FakeAuthRepository(
            signedInUser = AuthUser(uid = "me", email = "john@epfl.ch", name = "John")
        )
    connectivity = FakeConnectivityRepository()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun viewModel(userId: String? = null) =
      ProfileViewModel(userId, profiles, listings, rentals, auth, connectivity)

  /** [listings], calling [onRead] at each read of someone's listings, own or not. */
  private fun countingListings(onRead: () -> Unit) =
      object : ListingRepository by listings {
        override suspend fun getListingsByOwner(
            ownerId: String,
            fromServer: Boolean,
        ): Result<List<Listing>> {
          onRead()
          return listings.getListingsByOwner(ownerId, fromServer)
        }
      }

  /** A ViewModel for the profile of [userId], calling [onRead] at each read of the listings. */
  private fun countingViewModel(userId: String?, onRead: () -> Unit) =
      ProfileViewModel(userId, profiles, countingListings(onRead), rentals, auth, connectivity)

  /** Like a phone reporting it is online that cannot reach the server: only the cache answers. */
  private fun serverOutOfReach(cached: List<Listing>) =
      object : ListingRepository by listings {
        override suspend fun getListingsByOwner(
            ownerId: String,
            fromServer: Boolean,
        ): Result<List<Listing>> =
            if (fromServer) Result.failure(IllegalStateException("Unavailable"))
            else Result.success(cached)
      }

  /** A network status that arrives only when the test emits it. */
  private fun connectivityFrom(status: Flow<Boolean>) =
      object : ConnectivityRepository {
        override val isOnline: Flow<Boolean> = status
      }

  // ---------- own profile ----------

  @Test
  fun ownProfileShowsTheProfileAndAllMyListings() {
    val state = viewModel().uiState.value

    assertTrue(state.isOwnProfile)
    assertEquals(me, state.profile)
    assertEquals(
        listOf(
            MyListing(myTent, ListingRentalState.AVAILABLE),
            MyListing(myDraft, ListingRentalState.AVAILABLE),
        ),
        state.listings,
    )
    assertFalse(state.isLoading)
    assertFalse(state.listingsLoading)
    assertFalse(state.profileNotFound)
    assertNull(state.errorMsg)
  }

  @Test
  fun listingStateFollowsItsRentalsLive() = runTest {
    val vm = viewModel()

    rentals.requestRental(
        listing = myTent,
        ownerName = "John",
        borrower = AuthUser(uid = "jane", email = "jane@epfl.ch", name = "Jane"),
        startDate = LocalDate.of(2026, 10, 10),
        endDate = LocalDate.of(2026, 10, 12),
    )

    assertEquals(
        listOf(ListingRentalState.REQUESTED, ListingRentalState.AVAILABLE),
        vm.uiState.value.listings.map { it.rentalState },
    )
  }

  @Test
  fun myOwnIdIsTheOwnProfileToo() {
    // e.g. when tapping on myself on Listing detail
    assertTrue(viewModel(userId = "me").uiState.value.isOwnProfile)
  }

  @Test
  fun profileChangesShowWithoutReloading() = runTest {
    val vm = viewModel()

    profiles.updateProfile("me", "Johnny", "https://example.com/john.jpg")

    assertEquals(
        me.copy(name = "Johnny", photoUrl = "https://example.com/john.jpg"),
        vm.uiState.value.profile,
    )
  }

  @Test
  fun missingProfileIsNotFound() {
    profiles = FakeProfileRepository(listOf(jane))

    val state = viewModel().uiState.value

    assertNull(state.profile)
    assertTrue(state.profileNotFound)
    assertFalse(state.isLoading)
  }

  // ---------- someone else's profile ----------

  @Test
  fun otherProfileShowsOnlyTheirPublishedListingsWithoutWhereTheyStand() {
    val gate = CompletableDeferred<Unit>()
    listings.gate = gate
    val vm = viewModel(userId = "jane")
    assertTrue(vm.uiState.value.listingsLoading)

    gate.complete(Unit)

    val state = vm.uiState.value
    assertFalse(state.isOwnProfile)
    assertEquals(jane, state.profile)
    assertFalse(state.listingsLoading)
    // Her draft is left out, and her rentals are not read.
    assertEquals(listOf(MyListing(janesBike, rentalState = null)), state.listings)
  }

  @Test
  fun otherProfileNeverReadsRentals() {
    val reads = mutableListOf<String>()
    val recorded =
        object : RentalRepository by rentals {
          override fun observeRentalsAsLender(uid: String): Flow<List<Rental>> {
            reads += "as lender: $uid"
            return rentals.observeRentalsAsLender(uid)
          }

          override fun observeRentalsAsBorrower(uid: String): Flow<List<Rental>> {
            reads += "as borrower: $uid"
            return rentals.observeRentalsAsBorrower(uid)
          }

          override suspend fun getRental(id: String): Result<Rental?> {
            reads += "rental: $id"
            return rentals.getRental(id)
          }
        }
    val vm = ProfileViewModel("jane", profiles, listings, recorded, auth, connectivity)

    vm.onScreenResumed()
    vm.onScreenResumed()
    vm.refreshListings()

    assertEquals(emptyList<String>(), reads)
    assertEquals(listOf(MyListing(janesBike, rentalState = null)), vm.uiState.value.listings)
  }

  @Test
  fun otherProfileWithOnlyDraftsShowsNoListing() {
    listings.listings = listOf(janesDraft, myTent)

    val state = viewModel(userId = "jane").uiState.value

    assertTrue(state.listings.isEmpty())
    assertFalse(state.listingsLoading)
    assertFalse(state.listingsError)
  }

  @Test
  fun otherProfileListingsErrorKeepsTheProfileAndRetryLoadsThem() {
    listings.failure = IllegalStateException("No connection")
    val vm = viewModel(userId = "jane")
    assertTrue(vm.uiState.value.listingsError)
    assertEquals(jane, vm.uiState.value.profile)

    listings.failure = null
    vm.refreshListings()

    assertFalse(vm.uiState.value.listingsError)
    assertEquals(listOf(MyListing(janesBike, rentalState = null)), vm.uiState.value.listings)
  }

  @Test
  fun comingBackToSomeoneElsesProfileShowsTheirListingsAsTheyAreNow() {
    val vm = viewModel(userId = "jane")
    vm.onScreenResumed()

    // e.g. Jane unpublished her bike while it was open on Listing detail
    listings.listings = listOf(janesBike.copy(status = ListingStatus.DRAFT))
    vm.onScreenResumed()

    assertTrue(vm.uiState.value.listings.isEmpty())
  }

  @Test
  fun otherProfileRefreshThatFailsKeepsTheirListingsAsStale() {
    val vm = viewModel(userId = "jane")

    listings.failure = IllegalStateException("No connection")
    vm.refreshListings()

    assertFalse(vm.uiState.value.listingsError)
    assertTrue(vm.uiState.value.listingsStale)
    assertEquals(listOf(MyListing(janesBike, rentalState = null)), vm.uiState.value.listings)
  }

  // ---------- someone else's profile, connection ----------

  @Test
  fun otherProfileOfflineReadsNothingAndKeepsTheProfile() {
    connectivity.setOnline(false)
    var reads = 0

    val state = countingViewModel(userId = "jane") { reads++ }.uiState.value

    assertEquals(0, reads)
    assertTrue(state.listingsOffline)
    assertFalse(state.listingsLoading)
    assertTrue(state.listings.isEmpty())
    assertEquals(jane, state.profile)
  }

  @Test
  fun otherProfileLoadsTheListingsWhenTheConnectionComesBack() {
    connectivity.setOnline(false)
    val vm = viewModel(userId = "jane")

    connectivity.setOnline(true)

    assertFalse(vm.uiState.value.listingsOffline)
    assertEquals(listOf(MyListing(janesBike, rentalState = null)), vm.uiState.value.listings)
  }

  @Test
  fun otherProfileLoadsUntilTheFirstNetworkStatusArrives() {
    val status = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val vm = ProfileViewModel("jane", profiles, listings, rentals, auth, connectivityFrom(status))
    assertTrue(vm.uiState.value.listingsLoading)
    assertTrue(vm.uiState.value.listings.isEmpty())

    status.tryEmit(true)

    assertFalse(vm.uiState.value.listingsLoading)
    assertEquals(listOf(MyListing(janesBike, rentalState = null)), vm.uiState.value.listings)
  }

  @Test
  fun retryBeforeTheNetworkStatusArrivesClearsTheStaleNote() {
    val status = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val vm = ProfileViewModel("jane", profiles, listings, rentals, auth, connectivityFrom(status))
    status.tryEmit(true)
    listings.failure = IllegalStateException("No connection")
    vm.refreshListings()
    assertTrue(vm.uiState.value.listingsStale)

    vm.retry()

    assertTrue(vm.uiState.value.listingsLoading)
    assertFalse(vm.uiState.value.listingsStale)
  }

  @Test
  fun retryBeforeTheNetworkStatusArrivesClearsTheListingsError() {
    val status = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    listings.failure = IllegalStateException("No connection")
    val vm = ProfileViewModel("jane", profiles, listings, rentals, auth, connectivityFrom(status))
    status.tryEmit(true)
    assertTrue(vm.uiState.value.listingsError)

    vm.retry()

    assertTrue(vm.uiState.value.listingsLoading)
    assertFalse(vm.uiState.value.listingsError)
  }

  @Test
  fun otherProfileWithTheServerOutOfReachShowsTheErrorNotTheCache() {
    val vm =
        ProfileViewModel(
            "jane",
            profiles,
            serverOutOfReach(cached = emptyList()),
            rentals,
            auth,
            connectivity,
        )

    assertTrue(vm.uiState.value.listingsError)
    assertFalse(vm.uiState.value.listingsLoading)
    assertTrue(vm.uiState.value.listings.isEmpty())
  }

  @Test
  fun ownProfileWithTheServerOutOfReachShowsTheCache() {
    val vm =
        ProfileViewModel(
            null,
            profiles,
            serverOutOfReach(cached = listOf(myTent)),
            rentals,
            auth,
            connectivity,
        )

    assertFalse(vm.uiState.value.listingsError)
    assertEquals(
        listOf(MyListing(myTent, ListingRentalState.AVAILABLE)),
        vm.uiState.value.listings,
    )
  }

  @Test
  fun goingOfflineWhileTheListingsLoadStopsTheLoad() {
    val gate = CompletableDeferred<Unit>()
    listings.gate = gate
    val vm = viewModel(userId = "jane")
    assertTrue(vm.uiState.value.listingsLoading)

    connectivity.setOnline(false)
    gate.complete(Unit)

    assertTrue(vm.uiState.value.listingsOffline)
    assertFalse(vm.uiState.value.listingsLoading)
    assertTrue(vm.uiState.value.listings.isEmpty())
  }

  @Test
  fun goingOfflineClearsTheListingsError() {
    listings.failure = IllegalStateException("No connection")
    val vm = viewModel(userId = "jane")
    assertTrue(vm.uiState.value.listingsError)

    connectivity.setOnline(false)

    assertFalse(vm.uiState.value.listingsError)
    assertTrue(vm.uiState.value.listingsOffline)
  }

  @Test
  fun goingOfflineClearsTheStaleNote() {
    val vm = viewModel(userId = "jane")
    listings.failure = IllegalStateException("No connection")
    vm.refreshListings()
    assertTrue(vm.uiState.value.listingsStale)

    connectivity.setOnline(false)

    assertFalse(vm.uiState.value.listingsStale)
    assertTrue(vm.uiState.value.listingsOffline)
  }

  @Test
  fun refreshingWhileOfflineReadsNothing() {
    var reads = 0
    val vm = countingViewModel(userId = "jane") { reads++ }
    assertEquals(1, reads)

    connectivity.setOnline(false)
    vm.refreshListings()
    vm.onScreenResumed()
    vm.onScreenResumed()

    assertEquals(1, reads)
    assertTrue(vm.uiState.value.listingsOffline)
  }

  @Test
  fun retryOfflineThenReconnectingReadsTheListingsOnce() {
    connectivity.setOnline(false)
    var reads = 0
    val vm = countingViewModel(userId = "jane") { reads++ }

    vm.retry()
    assertTrue(vm.uiState.value.listingsOffline)
    connectivity.setOnline(true)

    // Not twice: retry stops following the connection before following it again.
    assertEquals(1, reads)
    assertFalse(vm.uiState.value.listingsOffline)
  }

  @Test
  fun ownProfileOfflineStillShowsMyListings() {
    connectivity.setOnline(false)

    val state = viewModel().uiState.value

    assertFalse(state.listingsOffline)
    assertEquals(2, state.listings.size)
  }

  @Test
  fun otherProfileIsShownEvenWhenNobodyIsSignedIn() {
    auth.signedInUser = null

    val state = viewModel(userId = "jane").uiState.value

    assertFalse(state.isOwnProfile)
    assertEquals(jane, state.profile)
  }

  // ---------- loading and errors ----------

  @Test
  fun profileShowsWhileTheListingsStillLoad() {
    val gate = CompletableDeferred<Unit>()
    listings.gate = gate

    val vm = viewModel()
    assertFalse(vm.uiState.value.isLoading)
    assertEquals(me, vm.uiState.value.profile)
    assertTrue(vm.uiState.value.listingsLoading)

    gate.complete(Unit)
    assertFalse(vm.uiState.value.listingsLoading)
    assertEquals(2, vm.uiState.value.listings.size)
  }

  @Test
  fun profileErrorShowsTheErrorAndRetryLoadsAgain() {
    profiles.failure = IllegalStateException("No connection")
    val vm = viewModel()
    assertEquals("No connection", vm.uiState.value.errorMsg)
    assertFalse(vm.uiState.value.isLoading)

    profiles.failure = null
    vm.retry()

    assertNull(vm.uiState.value.errorMsg)
    assertEquals(me, vm.uiState.value.profile)
  }

  @Test
  fun listingsErrorKeepsTheProfile() {
    listings.failure = IllegalStateException("No connection")

    val state = viewModel().uiState.value

    assertTrue(state.listingsError)
    assertFalse(state.listingsLoading)
    // Nothing was read before, so there are no old listings to show.
    assertFalse(state.listingsStale)
    assertNull(state.errorMsg)
    assertEquals(me, state.profile)
  }

  @Test
  fun refreshListingsShowsListingsChangedMeanwhile() {
    val vm = viewModel()

    listings.listings = listOf(myTent.copy(title = "Big tent"), janesBike)
    vm.refreshListings()

    assertEquals(
        listOf(MyListing(myTent.copy(title = "Big tent"), ListingRentalState.AVAILABLE)),
        vm.uiState.value.listings,
    )
  }

  @Test
  fun refreshListingsKeepsFollowingTheSameRentals() {
    var subscriptions = 0
    val counted =
        object : RentalRepository by rentals {
          override fun observeRentalsAsLender(uid: String): Flow<List<Rental>> =
              rentals.observeRentalsAsLender(uid).onStart { subscriptions++ }
        }
    val vm = ProfileViewModel(null, profiles, listings, counted, auth, connectivity)

    vm.refreshListings()

    assertEquals(1, subscriptions)
  }

  @Test
  fun refreshListingsKeepsTheShownListingsWithoutASpinner() {
    val vm = viewModel()

    listings.gate = CompletableDeferred()
    vm.refreshListings()

    assertFalse(vm.uiState.value.listingsLoading)
    assertEquals(2, vm.uiState.value.listings.size)
  }

  @Test
  fun refreshThatFailsKeepsTheListingsAsStaleWithTheirLivePills() = runTest {
    val vm = viewModel()

    listings.failure = IllegalStateException("No connection")
    vm.refreshListings()
    rentals.requestRental(
        listing = myTent,
        ownerName = "John",
        borrower = AuthUser(uid = "jane", email = "jane@epfl.ch", name = "Jane"),
        startDate = LocalDate.of(2026, 10, 10),
        endDate = LocalDate.of(2026, 10, 12),
    )

    assertFalse(vm.uiState.value.listingsError)
    assertTrue(vm.uiState.value.listingsStale)
    assertEquals(
        listOf(ListingRentalState.REQUESTED, ListingRentalState.AVAILABLE),
        vm.uiState.value.listings.map { it.rentalState },
    )
  }

  @Test
  fun refreshThatSucceedsAfterAFailureIsNoLongerStale() {
    val vm = viewModel()
    listings.failure = IllegalStateException("No connection")
    vm.refreshListings()
    assertTrue(vm.uiState.value.listingsStale)

    listings.failure = null
    vm.refreshListings()

    assertFalse(vm.uiState.value.listingsStale)
  }

  @Test
  fun retryWhileStaleLoadsTheListingsWithoutTheStaleNote() {
    val vm = viewModel()
    listings.failure = IllegalStateException("No connection")
    vm.refreshListings()
    assertTrue(vm.uiState.value.listingsStale)

    listings.failure = null
    listings.gate = CompletableDeferred()
    vm.retry()

    assertTrue(vm.uiState.value.listingsLoading)
    assertFalse(vm.uiState.value.listingsStale)
  }

  @Test
  fun refreshThatFailsWithNoListingShownIsStillStale() {
    listings.listings = listOf(janesBike)
    val vm = viewModel()

    listings.failure = IllegalStateException("No connection")
    vm.refreshListings()

    assertTrue(vm.uiState.value.listings.isEmpty())
    assertFalse(vm.uiState.value.listingsError)
    assertTrue(vm.uiState.value.listingsStale)
  }

  @Test
  fun rentalsFailingWhileStaleShowTheErrorWithoutTheStaleNote() {
    val rentalsFailure = CompletableDeferred<Nothing>()
    val failing =
        object : RentalRepository by rentals {
          override fun observeRentalsAsLender(uid: String): Flow<List<Rental>> = flow {
            emit(emptyList())
            rentalsFailure.await()
          }
        }
    val vm = ProfileViewModel(null, profiles, listings, failing, auth, connectivity)
    listings.failure = IllegalStateException("No connection")
    vm.refreshListings()
    assertTrue(vm.uiState.value.listingsStale)

    rentalsFailure.completeExceptionally(IllegalStateException("No connection"))

    assertTrue(vm.uiState.value.listingsError)
    assertFalse(vm.uiState.value.listingsStale)
  }

  @Test
  fun onlyComingBackToTheScreenReadsTheListingsAgain() {
    var reads = 0
    val vm = countingViewModel(userId = null) { reads++ }

    vm.onScreenResumed()
    assertEquals(1, reads)

    vm.onScreenResumed()
    assertEquals(2, reads)
  }

  @Test
  fun retryAfterAnErrorShowsTheSpinnerThenTheListings() {
    listings.failure = IllegalStateException("No connection")
    val vm = viewModel()

    listings.failure = null
    val gate = CompletableDeferred<Unit>()
    listings.gate = gate
    vm.refreshListings()
    assertTrue(vm.uiState.value.listingsLoading)
    assertFalse(vm.uiState.value.listingsError)

    gate.complete(Unit)
    assertFalse(vm.uiState.value.listingsLoading)
    assertEquals(2, vm.uiState.value.listings.size)
  }

  @Test
  fun refreshListingsWhileTheyLoadIsIgnored() {
    var reads = 0
    val gate = CompletableDeferred<Unit>()
    listings.gate = gate
    val vm = countingViewModel(userId = null) { reads++ }

    vm.refreshListings()
    gate.complete(Unit)

    assertEquals(1, reads)
  }

  @Test
  fun rentalsErrorShowsAListingsError() {
    rentals.failure = IllegalStateException("No connection")

    val state = viewModel().uiState.value

    assertTrue(state.listingsError)
    assertEquals(me, state.profile)
  }

  @Test
  fun errorWithoutMessageGivesABlankError() {
    profiles.failure = IllegalStateException()

    assertEquals("", viewModel().uiState.value.errorMsg)
  }

  @Test
  fun ownProfileWithNobodySignedInGivesABlankError() {
    auth.signedInUser = null

    val state = viewModel().uiState.value

    assertFalse(state.isOwnProfile)
    assertFalse(state.isLoading)
    assertEquals("", state.errorMsg)
  }

  // ---------- sign out ----------

  @Test
  fun signOutOnTheOwnProfileSignsOut() {
    val vm = viewModel()

    vm.signOut()

    assertEquals(listOf("signOut()"), auth.calls)
    assertTrue(vm.uiState.value.isSignedOut)
  }

  @Test
  fun refreshListingsAfterSigningOutReadsNothing() {
    var reads = 0
    val vm = countingViewModel(userId = null) { reads++ }

    vm.signOut()
    vm.refreshListings()

    assertEquals(1, reads)
  }

  @Test
  fun signingOutWhileTheListingsLoadStopsTheSpinner() {
    listings.gate = CompletableDeferred()
    val vm = viewModel()

    vm.signOut()

    assertFalse(vm.uiState.value.listingsLoading)
  }

  @Test
  fun signOutIsIgnoredOnSomeoneElsesProfile() {
    val vm = viewModel(userId = "jane")

    vm.signOut()

    assertTrue(auth.calls.isEmpty())
    assertFalse(vm.uiState.value.isSignedOut)
  }

  // ---------- factory ----------

  @Test
  fun factoryCreatesTheViewModel() {
    val created =
        ProfileViewModel.Factory("jane", profiles, listings, rentals, auth, connectivity)
            .create(ProfileViewModel::class.java)

    assertEquals(jane, created.uiState.value.profile)
  }

  @Test
  fun factoryRejectsOtherViewModels() {
    class Other : ViewModel()

    assertThrows(IllegalArgumentException::class.java) {
      ProfileViewModel.Factory(null, profiles, listings, rentals, auth, connectivity)
          .create(Other::class.java)
    }
  }
}
