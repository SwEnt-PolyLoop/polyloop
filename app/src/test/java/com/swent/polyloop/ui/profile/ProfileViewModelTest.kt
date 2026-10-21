// Made with Claude.

package com.swent.polyloop.ui.profile

import androidx.lifecycle.ViewModel
import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.auth.FakeAuthRepository
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.model.listing.ListingStatus
import com.swent.polyloop.model.profile.FakeProfileRepository
import com.swent.polyloop.model.profile.UserProfile
import com.swent.polyloop.model.rental.FakeRentalRepository
import com.swent.polyloop.ui.listingdetail.FakeListingRepository
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

  private lateinit var profiles: FakeProfileRepository
  private lateinit var listings: FakeListingRepository
  private lateinit var rentals: FakeRentalRepository
  private lateinit var auth: FakeAuthRepository

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    profiles = FakeProfileRepository(listOf(me, jane))
    listings = FakeListingRepository(listings = listOf(myTent, myDraft, janesBike))
    rentals = FakeRentalRepository()
    auth =
        FakeAuthRepository(
            signedInUser = AuthUser(uid = "me", email = "john@epfl.ch", name = "John")
        )
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun viewModel(userId: String? = null) =
      ProfileViewModel(userId, profiles, listings, rentals, auth)

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
  fun otherProfileShowsTheirProfileWithoutListings() {
    val state = viewModel(userId = "jane").uiState.value

    assertFalse(state.isOwnProfile)
    assertEquals(jane, state.profile)
    assertTrue(state.listings.isEmpty())
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

    assertEquals("No connection", state.listingsError)
    assertFalse(state.listingsLoading)
    assertNull(state.errorMsg)
    assertEquals(me, state.profile)
  }

  @Test
  fun retryListingsLoadsTheListingsAgain() {
    listings.failure = IllegalStateException("No connection")
    val vm = viewModel()

    listings.failure = null
    vm.retryListings()

    assertNull(vm.uiState.value.listingsError)
    assertEquals(2, vm.uiState.value.listings.size)
  }

  @Test
  fun rentalsErrorShowsAListingsError() {
    rentals.failure = IllegalStateException("No connection")

    val state = viewModel().uiState.value

    assertEquals("No connection", state.listingsError)
    assertEquals(me, state.profile)
  }

  @Test
  fun otherProfileNeverLoadsListings() {
    val vm = viewModel(userId = "jane")

    vm.retryListings()

    assertFalse(vm.uiState.value.listingsLoading)
    assertTrue(vm.uiState.value.listings.isEmpty())
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
        ProfileViewModel.Factory("jane", profiles, listings, rentals, auth)
            .create(ProfileViewModel::class.java)

    assertEquals(jane, created.uiState.value.profile)
  }

  @Test
  fun factoryRejectsOtherViewModels() {
    class Other : ViewModel()

    assertThrows(IllegalArgumentException::class.java) {
      ProfileViewModel.Factory(null, profiles, listings, rentals, auth).create(Other::class.java)
    }
  }
}
