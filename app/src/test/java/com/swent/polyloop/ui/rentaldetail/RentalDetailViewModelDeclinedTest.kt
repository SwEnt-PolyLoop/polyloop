// Made with Claude.

package com.swent.polyloop.ui.rentaldetail

import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.auth.FakeAuthRepository
import com.swent.polyloop.model.connectivity.FakeConnectivityRepository
import com.swent.polyloop.model.profile.FakeProfileRepository
import com.swent.polyloop.model.profile.UserProfile
import com.swent.polyloop.model.rental.DepositOffer
import com.swent.polyloop.model.rental.FakeRentalRepository
import com.swent.polyloop.model.rental.InMemoryServerClockStore
import com.swent.polyloop.model.rental.Rental
import com.swent.polyloop.model.rental.RentalStatus
import com.swent.polyloop.model.rental.ServerClock
import com.swent.polyloop.ui.listingdetail.FakeListingRepository
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The Declined state of Rental detail, which never reads the server's day. */
@OptIn(ExperimentalCoroutinesApi::class)
class RentalDetailViewModelDeclinedTest {

  private val now = Instant.parse("2026-11-04T10:00:00Z")

  private val request =
      Rental(
          id = "r1",
          listingId = "l1",
          listingTitle = "Ski helmet",
          ownerId = "owner",
          ownerName = "Mara",
          borrowerId = "borrower",
          borrowerName = "Loca",
          startDate = LocalDate.of(2026, 12, 1),
          endDate = LocalDate.of(2026, 12, 8),
          status = RentalStatus.REQUESTED,
          pricePerDay = 15,
          totalPrice = 120,
          pickupArea = "Ecublens",
          dropOffArea = "Renens",
          createdAt = now,
      )

  private val declined = request.copy(status = RentalStatus.DECLINED)

  // The lender's offer, still open at [now].
  private val offer =
      DepositOffer(
          amount = 60,
          authorId = "owner",
          isCounter = false,
          expiresAt = Instant.parse("2026-11-11T10:00:00Z"),
      )

  private val profiles =
      listOf(
          UserProfile("owner", "Mara Rossi", "m@epfl.ch", averageRating = 4.6, reviewCount = 5),
          UserProfile("borrower", "Loca Meier", "l@epfl.ch", averageRating = 3.0, reviewCount = 2),
      )

  private lateinit var rentals: FakeRentalRepository

  // Also runs the server clock's reads, so they finish before each assertion.
  private val testDispatcher = UnconfinedTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  /**
   * The ViewModel for [rental] as [uid], with [others] also stored (e.g. another request for the
   * same listing).
   */
  private fun viewModel(
      rental: Rental,
      uid: String = "borrower",
      others: List<Rental> = emptyList(),
      connectivity: FakeConnectivityRepository = FakeConnectivityRepository(),
      listings: FakeListingRepository = FakeListingRepository(),
  ): RentalDetailViewModel {
    rentals = FakeRentalRepository(rentals = listOf(rental) + others, clock = { now })
    rentals.serverNow =
        LocalDate.of(2026, 11, 30).atTime(12, 0).atZone(ZoneId.of("Europe/Zurich")).toInstant()
    return RentalDetailViewModel(
        rental.id,
        rentals,
        listings,
        FakeProfileRepository(profiles),
        FakeAuthRepository(signedInUser = AuthUser(uid, "$uid@epfl.ch", uid)),
        connectivity,
        ServerClock(
            rentals,
            InMemoryServerClockStore(),
            elapsedRealtime = { 0L },
            bootCount = { 0 },
            ioDispatcher = testDispatcher,
        ),
    )
  }

  /** Asserts what both roles see of [declined], apart from the other person. */
  private fun assertDeclined(state: RentalDetailUiState) {
    assertTrue(state.isSupported)
    assertFalse(state.isLoading)
    assertNull(state.errorMsg)
    assertEquals(RentalStatus.DECLINED, state.status)
    assertEquals("Ski helmet", state.itemTitle)
    assertEquals(LocalDate.of(2026, 12, 1), state.startDate)
    assertEquals(LocalDate.of(2026, 12, 8), state.endDate)
    assertEquals(120, state.rentalPrice)
    // A declined rental never had its deposit held.
    assertEquals(0, state.deposit)
    assertNull(state.offer)
    assertFalse(state.isOfferMine)
  }

  /** Asserts that a request declined while the screen is open shows as Declined, as is. */
  private fun assertDeclinedLive(vm: RentalDetailViewModel) {
    val state = vm.uiState.value
    assertTrue(state.isSupported)
    assertEquals(RentalStatus.DECLINED, state.status)
    assertNull(state.offer)
    assertFalse(state.isOfferMine)
    assertEquals(0, rentals.serverNowCalls)
  }

  @Test
  fun aDeclinedRentalIsSupportedForTheBorrower() {
    val state = viewModel(declined).uiState.value

    assertDeclined(state)
    assertFalse(state.isLender)
    assertEquals(
        OtherPerson(uid = "owner", name = "Mara Rossi", averageRating = 4.6, reviewCount = 5),
        state.otherPerson,
    )
  }

  @Test
  fun aDeclinedRentalIsSupportedForTheLender() {
    val state = viewModel(declined, uid = "owner").uiState.value

    assertDeclined(state)
    assertTrue(state.isLender)
    assertEquals(
        OtherPerson(uid = "borrower", name = "Loca Meier", averageRating = 3.0, reviewCount = 2),
        state.otherPerson,
    )
  }

  @Test
  fun aDeclinedRentalNeverReadsTheServersDayAndCannotBeCancelledOrReviewed() {
    val vm = viewModel(declined, uid = "owner")

    vm.onScreenResumed()

    val state = vm.uiState.value
    assertEquals(0, rentals.serverNowCalls)
    assertNull(state.serverToday)
    assertFalse(state.isReadingServerToday)
    assertFalse(state.canCancel)
    assertFalse(state.canReviewRequest)
  }

  @Test
  fun aRequestDeclinedByTheLenderWhileTheScreenIsOpenShowsAsDeclined() = runTest {
    val vm = viewModel(request)

    rentals.declineRequest(request.id).getOrThrow()

    assertDeclinedLive(vm)
  }

  @Test
  fun anOfferDeclinedWhileTheScreenIsOpenShowsAsDeclinedWithoutTheOffer() = runTest {
    val vm = viewModel(request.copy(offer = offer), uid = "owner")
    assertEquals(offer, vm.uiState.value.offer)
    assertTrue(vm.uiState.value.isOfferMine)

    rentals.declineOffer(request.id).getOrThrow()

    assertDeclinedLive(vm)
  }

  @Test
  fun aRequestDeclinedBecauseAnOverlappingOneWasAcceptedShowsAsDeclined() = runTest {
    val overlapping = request.copy(id = "r2", borrowerId = "someone", offer = offer)
    val vm = viewModel(request, others = listOf(overlapping))

    rentals.acceptOffer(overlapping.id).getOrThrow()

    assertDeclinedLive(vm)
  }

  @Test
  fun goingOfflineThenOnlineOnADeclinedRentalReadsNothingAgain() {
    val connectivity = FakeConnectivityRepository()
    // A failed listing read would be retried on reconnection for a request; not here.
    val listings = FakeListingRepository(failure = IOException("offline"))
    val vm = viewModel(declined, connectivity = connectivity, listings = listings)
    assertEquals(1, listings.getListingCalls)

    connectivity.setOnline(false)
    connectivity.setOnline(true)

    assertEquals(RentalStatus.DECLINED, vm.uiState.value.status)
    assertEquals(0, rentals.serverNowCalls)
    assertEquals(1, listings.getListingCalls)
  }
}
