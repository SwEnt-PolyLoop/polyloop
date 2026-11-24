// Made with Claude.

package com.swent.polyloop.ui.rentaldetail

import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.auth.FakeAuthRepository
import com.swent.polyloop.model.connectivity.FakeConnectivityRepository
import com.swent.polyloop.model.profile.FakeProfileRepository
import com.swent.polyloop.model.profile.UserProfile
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

/** The Cancelled state of Rental detail, which never reads the server's day. */
@OptIn(ExperimentalCoroutinesApi::class)
class RentalDetailViewModelCancelledTest {

  private val now = Instant.parse("2026-11-04T10:00:00Z")
  private val cancelledAt = Instant.parse("2026-09-30T08:15:00Z")

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

  private val accepted =
      request.copy(
          status = RentalStatus.ACCEPTED,
          deposit = 60,
          pickupAddress = "Route Cantonale 1",
          returnAddress = "Rue de Lausanne 10",
      )

  private val cancelled = accepted.copy(status = RentalStatus.CANCELLED, cancelledAt = cancelledAt)

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

  // The day before pickup, so an accepted rental can still be cancelled.
  private val serverDay = LocalDate.of(2026, 11, 30)

  /** The ViewModel for [rental] as [uid], with the server on [serverDay]. */
  private fun viewModel(
      rental: Rental,
      uid: String = "borrower",
      connectivity: FakeConnectivityRepository = FakeConnectivityRepository(),
      listings: FakeListingRepository = FakeListingRepository(),
  ): RentalDetailViewModel {
    rentals = FakeRentalRepository(rentals = listOf(rental), clock = { now })
    rentals.serverNow = serverDay.atTime(12, 0).atZone(ZoneId.of("Europe/Zurich")).toInstant()
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

  /** Asserts what both roles see of [cancelled], apart from the other person. */
  private fun assertCancelled(state: RentalDetailUiState) {
    assertTrue(state.isSupported)
    assertFalse(state.isLoading)
    assertNull(state.errorMsg)
    assertEquals(RentalStatus.CANCELLED, state.status)
    assertEquals("Ski helmet", state.itemTitle)
    assertEquals(LocalDate.of(2026, 12, 1), state.startDate)
    assertEquals(LocalDate.of(2026, 12, 8), state.endDate)
    assertEquals(120, state.rentalPrice)
    assertEquals(60, state.deposit)
    assertEquals(cancelledAt, state.cancelledAt)
  }

  @Test
  fun aCancelledRentalIsSupportedForTheBorrower() {
    val state = viewModel(cancelled).uiState.value

    assertCancelled(state)
    assertFalse(state.isLender)
    assertEquals(
        OtherPerson(uid = "owner", name = "Mara Rossi", averageRating = 4.6, reviewCount = 5),
        state.otherPerson,
    )
  }

  @Test
  fun aCancelledRentalIsSupportedForTheLender() {
    val state = viewModel(cancelled, uid = "owner").uiState.value

    assertCancelled(state)
    assertTrue(state.isLender)
    assertEquals(
        OtherPerson(uid = "borrower", name = "Loca Meier", averageRating = 3.0, reviewCount = 2),
        state.otherPerson,
    )
  }

  @Test
  fun aRequestCancelledBeforeAnyOfferHasNoDeposit() {
    val state =
        viewModel(request.copy(status = RentalStatus.CANCELLED, cancelledAt = cancelledAt))
            .uiState
            .value

    assertTrue(state.isSupported)
    assertEquals(RentalStatus.CANCELLED, state.status)
    assertEquals(0, state.deposit)
  }

  @Test
  fun aCancelledRentalWithoutACancelDateIsSupportedWithNoDate() {
    val state = viewModel(cancelled.copy(cancelledAt = null)).uiState.value

    assertTrue(state.isSupported)
    assertEquals(RentalStatus.CANCELLED, state.status)
    assertNull(state.cancelledAt)
  }

  @Test
  fun aCancelledRentalNeverReadsTheServersDayAndCannotBeCancelled() {
    val vm = viewModel(cancelled)

    vm.onScreenResumed()

    val state = vm.uiState.value
    assertEquals(0, rentals.serverNowCalls)
    assertNull(state.serverToday)
    assertFalse(state.isReadingServerToday)
    assertFalse(state.canCancel)
  }

  @Test
  fun aRequestCancelledWhileTheScreenIsOpenShowsAsCancelledWithItsDate() = runTest {
    val vm = viewModel(request)

    rentals.cancelRental(request.id)

    val state = vm.uiState.value
    assertTrue(state.isSupported)
    assertEquals(RentalStatus.CANCELLED, state.status)
    // The fake stamps its clock, as the Cloud Function does.
    assertEquals(now, state.cancelledAt)
    assertEquals(0, rentals.serverNowCalls)
  }

  @Test
  fun anAcceptedRentalCancelledWhileTheScreenIsOpenKeepsItsDepositAndReadsNoNewDay() = runTest {
    val vm = viewModel(accepted)
    assertEquals(1, rentals.serverNowCalls)

    rentals.cancelRental(accepted.id)

    val state = vm.uiState.value
    assertTrue(state.isSupported)
    assertEquals(RentalStatus.CANCELLED, state.status)
    assertEquals(now, state.cancelledAt)
    assertEquals(60, state.deposit)
    assertEquals(1, rentals.serverNowCalls)
  }

  @Test
  fun goingOfflineThenOnlineOnACancelledRentalReadsNothingAgain() {
    val connectivity = FakeConnectivityRepository()
    // A failed listing read would be retried on reconnection for a request; not here.
    val listings = FakeListingRepository(failure = IOException("offline"))
    val vm = viewModel(cancelled, connectivity = connectivity, listings = listings)
    assertEquals(1, listings.getListingCalls)

    connectivity.setOnline(false)
    connectivity.setOnline(true)

    assertEquals(RentalStatus.CANCELLED, vm.uiState.value.status)
    assertEquals(0, rentals.serverNowCalls)
    assertEquals(1, listings.getListingCalls)
  }
}
