// Made with Claude.

package com.swent.polyloop.ui.reviewrequest

import androidx.lifecycle.ViewModel
import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.auth.FakeAuthRepository
import com.swent.polyloop.model.connectivity.FakeConnectivityRepository
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.model.listing.ListingStatus
import com.swent.polyloop.model.profile.FakeProfileRepository
import com.swent.polyloop.model.profile.UserProfile
import com.swent.polyloop.model.rental.FakeRentalRepository
import com.swent.polyloop.model.rental.Rental
import com.swent.polyloop.model.rental.RentalRepository
import com.swent.polyloop.model.rental.RentalStatus
import com.swent.polyloop.ui.listingdetail.FakeListingRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewRequestViewModelTest {

  private val now = Instant.parse("2026-11-04T10:00:00Z")

  private val request =
      Rental(
          id = "rental1",
          listingId = "listing1",
          listingTitle = "Camping tent",
          ownerId = "owner",
          ownerName = "Mara",
          borrowerId = "borrower",
          borrowerName = "Loca",
          startDate = LocalDate.of(2026, 12, 1),
          endDate = LocalDate.of(2026, 12, 3),
          status = RentalStatus.REQUESTED,
          pricePerDay = 15,
          totalPrice = 45,
          pickupArea = "Ecublens",
          dropOffArea = "Renens",
          createdAt = now,
      )

  private val listing =
      Listing(
          id = "listing1",
          ownerId = "owner",
          title = "Camping tent",
          description = "Light 2-person tent.",
          category = ListingCategory.SPORTS_OUTDOOR,
          photoUrls = List(3) { "https://example.com/photo$it.jpg" },
          pricePerDay = 15,
          itemValue = 180,
          availableFrom = LocalDate.of(2026, 11, 1),
          availableTo = LocalDate.of(2026, 12, 31),
          status = ListingStatus.PUBLISHED,
      )

  private val lender = AuthUser(uid = "owner", email = "mara@epfl.ch", name = "Mara")
  private val borrowerUser = AuthUser(uid = "borrower", email = "loca@epfl.ch", name = "Loca")
  private val borrowerProfile =
      UserProfile(
          uid = "borrower",
          name = "Loca Meier",
          email = "loca@epfl.ch",
          averageRating = 4.5,
          reviewCount = 12,
      )

  private lateinit var rentals: FakeRentalRepository
  private lateinit var listings: FakeListingRepository
  private lateinit var profiles: FakeProfileRepository
  private lateinit var auth: FakeAuthRepository
  private lateinit var connectivity: FakeConnectivityRepository

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    rentals = FakeRentalRepository(rentals = listOf(request), clock = { now })
    listings = FakeListingRepository(listings = listOf(listing))
    profiles = FakeProfileRepository(profiles = listOf(borrowerProfile))
    auth = FakeAuthRepository(signedInUser = lender)
    connectivity = FakeConnectivityRepository()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun viewModel(id: String = request.id) =
      ReviewRequestViewModel(id, rentals, listings, profiles, auth, connectivity)

  /** A ViewModel with a complete form. */
  private fun filled() =
      viewModel().apply {
        onPickupAddressChange("  Route Cantonale 1 ")
        onReturnAddressChange("Rue de Lausanne 10")
      }

  private suspend fun stored(): Rental = rentals.getRental(request.id).getOrThrow()!!

  // ---------- Loading ----------

  @Test
  fun showsLoadingThenTheRequestWithTheItemValueAsDeposit() {
    val gate = CompletableDeferred<Unit>()
    rentals.gate = gate
    val vm = viewModel()
    assertTrue(vm.uiState.value.isLoading)

    gate.complete(Unit)

    val state = vm.uiState.value
    assertFalse(state.isLoading)
    assertTrue(state.canAnswer)
    assertEquals("Camping tent", state.itemTitle)
    assertEquals(request.startDate, state.startDate)
    assertEquals(request.endDate, state.endDate)
    assertEquals(45, state.rentalPrice)
    assertEquals("180", state.deposit)
    assertEquals("", state.pickupAddress)
    assertEquals("borrower", state.borrower?.uid)
    assertEquals("Loca Meier", state.borrower?.name)
    assertEquals(4.5, state.borrower?.averageRating)
    assertEquals(12, state.borrower?.reviewCount)
  }

  @Test
  fun anUnreadableListingLeavesTheDepositEmpty() {
    listings.failure = IllegalStateException("down")

    val state = viewModel().uiState.value

    assertTrue(state.canAnswer)
    assertEquals("", state.deposit)
  }

  @Test
  fun anUnreadableProfileFallsBackToTheNameOnTheRental() {
    profiles.failure = IllegalStateException("down")

    val borrower = viewModel().uiState.value.borrower

    assertEquals("Loca", borrower?.name)
    assertNull(borrower?.averageRating)
    assertNull(borrower?.reviewCount)
  }

  @Test
  fun aMissingRentalIsNotFound() {
    val state = viewModel(id = "missing").uiState.value

    assertTrue(state.notFound)
    assertFalse(state.canAnswer)
  }

  @Test
  fun aReadErrorIsShownAndRetryLoadsAgain() {
    rentals.failure = IllegalStateException("down")
    val vm = viewModel()
    assertEquals("down", vm.uiState.value.errorMsg)

    rentals.failure = null
    vm.retry()

    assertNull(vm.uiState.value.errorMsg)
    assertTrue(vm.uiState.value.canAnswer)
  }

  @Test
  fun aFailureWithoutAMessageGivesABlankError() {
    rentals.failure = IllegalStateException()

    assertEquals("", viewModel().uiState.value.errorMsg)
  }

  @Test
  fun theBorrowerCannotAnswer() {
    auth = FakeAuthRepository(signedInUser = borrowerUser)

    val state = viewModel().uiState.value

    assertFalse(state.canAnswer)
    assertEquals("Camping tent", state.itemTitle)
  }

  @Test
  fun aRequestThatIsNotRequestedAnyMoreCannotBeAnswered() {
    rentals = FakeRentalRepository(listOf(request.copy(status = RentalStatus.DECLINED)))

    assertFalse(viewModel().uiState.value.canAnswer)
  }

  @Test
  fun aRequestWithAnOfferCannotBeAnsweredHereAgain() = runTest {
    rentals.makeOffer(request.id, 100, "A", "B").getOrThrow()

    assertFalse(viewModel().uiState.value.canAnswer)
  }

  // ---------- The form ----------

  @Test
  fun sendOfferIsDisabledUntilTheDepositAndBothAddressesAreValid() {
    val vm = viewModel()
    assertFalse(vm.uiState.value.canSendOffer)

    vm.onPickupAddressChange("Route Cantonale 1")
    assertFalse(vm.uiState.value.canSendOffer)

    vm.onReturnAddressChange("   ")
    assertFalse(vm.uiState.value.canSendOffer)

    vm.onReturnAddressChange("Rue de Lausanne 10")
    assertTrue(vm.uiState.value.canSendOffer)
  }

  @Test
  fun aDepositOutOfRangeOrNotWholeDisablesSendOffer() {
    val vm = filled()

    for (deposit in listOf("", "0", "-5", "12.5", "abc", "100000")) {
      vm.onDepositChange(deposit)
      assertFalse("deposit \"$deposit\"", vm.uiState.value.canSendOffer)
    }
    for (deposit in listOf("1", "99999")) {
      vm.onDepositChange(deposit)
      assertTrue("deposit \"$deposit\"", vm.uiState.value.canSendOffer)
    }
  }

  @Test
  fun bothActionsAreDisabledOfflineAndComeBackOnline() {
    val vm = filled()

    connectivity.setOnline(false)

    assertTrue(vm.uiState.value.isOffline)
    assertFalse(vm.uiState.value.canSendOffer)
    assertFalse(vm.uiState.value.canDecline)

    connectivity.setOnline(true)

    assertTrue(vm.uiState.value.canSendOffer)
    assertTrue(vm.uiState.value.canDecline)
  }

  // ---------- Answering ----------

  @Test
  fun sendOfferSendsTheOfferWithTrimmedAddresses() = runTest {
    val vm = filled()
    vm.onDepositChange("150")

    vm.sendOffer()

    assertEquals(ReviewOutcome.OFFER_SENT, vm.uiState.value.outcome)
    assertEquals(150, stored().offer?.amount)
    rentals.acceptOffer(request.id).getOrThrow()
    assertEquals("Route Cantonale 1", stored().pickupAddress)
    assertEquals("Rue de Lausanne 10", stored().returnAddress)
  }

  @Test
  fun sendOfferDoesNothingWhenTheFormIsNotValid() = runTest {
    val vm = viewModel()

    vm.sendOffer()

    assertNull(vm.uiState.value.outcome)
    assertNull(stored().offer)
  }

  @Test
  fun aFailedOfferShowsTheErrorAndKeepsTheForm() = runTest {
    val vm = filled()
    rentals.failure = IllegalStateException("refused")

    vm.sendOffer()

    val state = vm.uiState.value
    assertEquals("refused", state.actionError)
    assertNull(state.outcome)
    assertFalse(state.isSubmitting)
    assertEquals("Rue de Lausanne 10", state.returnAddress)
    assertEquals("180", state.deposit)

    rentals.failure = null
    vm.sendOffer()

    assertNull(vm.uiState.value.actionError)
    assertEquals(ReviewOutcome.OFFER_SENT, vm.uiState.value.outcome)
  }

  @Test
  fun aFailedAnswerWithoutAMessageGivesABlankError() {
    val vm = viewModel()
    rentals.failure = IllegalStateException()

    vm.decline()

    assertEquals("", vm.uiState.value.actionError)
  }

  @Test
  fun declineDeclinesTheRequest() = runTest {
    val vm = viewModel()

    vm.decline()

    assertEquals(ReviewOutcome.DECLINED, vm.uiState.value.outcome)
    assertEquals(RentalStatus.DECLINED, stored().status)
  }

  @Test
  fun declineDoesNothingOffline() = runTest {
    val vm = viewModel()
    connectivity.setOnline(false)

    vm.decline()

    assertNull(vm.uiState.value.outcome)
    assertEquals(RentalStatus.REQUESTED, stored().status)
  }

  @Test
  fun bothActionsAreDisabledWhileAnAnswerIsSentAndASecondPressDoesNothing() = runTest {
    val vm = filled()
    val gate = CompletableDeferred<Unit>()
    rentals.gate = gate

    vm.sendOffer()
    assertTrue(vm.uiState.value.isSubmitting)
    assertFalse(vm.uiState.value.canSendOffer)
    assertFalse(vm.uiState.value.canDecline)
    vm.decline()

    gate.complete(Unit)

    assertEquals(ReviewOutcome.OFFER_SENT, vm.uiState.value.outcome)
    assertNotNull(stored().offer)
    assertEquals(RentalStatus.REQUESTED, stored().status)
  }

  @Test
  fun nothingCanBeSentAgainOnceAnswered() {
    val vm = filled()

    vm.sendOffer()

    assertFalse(vm.uiState.value.canSendOffer)
    assertFalse(vm.uiState.value.canDecline)
  }

  // ---------- What the screen warns about ----------

  @Test
  fun givesTheSuggestedDeposit() {
    assertEquals(180, viewModel().uiState.value.suggestedDeposit)
  }

  @Test
  fun anUnreadableListingGivesNoSuggestedDeposit() {
    listings.failure = IllegalStateException("down")

    assertNull(viewModel().uiState.value.suggestedDeposit)
  }

  @Test
  fun countsOnlyOtherPendingRequestsOfTheListingWhoseDatesConflict() {
    val touching =
        request.copy(
            id = "touching",
            startDate = LocalDate.of(2026, 12, 4),
            endDate = LocalDate.of(2026, 12, 5),
        )
    val free =
        request.copy(
            id = "free",
            startDate = LocalDate.of(2026, 12, 5),
            endDate = LocalDate.of(2026, 12, 6),
        )
    val answered = touching.copy(id = "answered", status = RentalStatus.DECLINED)
    val elsewhere = touching.copy(id = "elsewhere", listingId = "listing2")
    rentals =
        FakeRentalRepository(listOf(request, touching, free, answered, elsewhere), clock = { now })

    assertEquals(1, viewModel().uiState.value.overlappingRequests)
  }

  @Test
  fun unreadableRentalsGiveNoWarningAndKeepTheForm() {
    val failing =
        object : RentalRepository by rentals {
          override fun observeRentalsAsLender(uid: String) = flow<List<Rental>> { error("down") }
        }
    val vm = ReviewRequestViewModel(request.id, failing, listings, profiles, auth, connectivity)

    assertEquals(0, vm.uiState.value.overlappingRequests)
    assertTrue(vm.uiState.value.canAnswer)
  }

  // ---------- Factory ----------

  private class OtherViewModel : ViewModel()

  private fun factory() =
      ReviewRequestViewModel.Factory(request.id, rentals, listings, profiles, auth, connectivity)

  @Test
  fun factoryCreatesTheViewModel() {
    val vm = factory().create(ReviewRequestViewModel::class.java)

    assertTrue(vm.uiState.value.canAnswer)
  }

  @Test(expected = IllegalArgumentException::class)
  fun factoryRefusesAnotherViewModel() {
    factory().create(OtherViewModel::class.java)
  }
}
