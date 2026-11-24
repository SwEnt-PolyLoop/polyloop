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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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

/** The Returned state of Rental detail, which never reads the server's day. */
@OptIn(ExperimentalCoroutinesApi::class)
class RentalDetailViewModelReturnedTest {

  private val now = Instant.parse("2026-10-09T10:00:00Z")
  private val returnedAt = Instant.parse("2026-10-08T16:40:00Z")

  private val active =
      Rental(
          id = "r1",
          listingId = "l1",
          listingTitle = "Ski helmet",
          ownerId = "owner",
          ownerName = "Mara",
          borrowerId = "borrower",
          borrowerName = "Loca",
          startDate = LocalDate.of(2026, 10, 1),
          endDate = LocalDate.of(2026, 10, 8),
          status = RentalStatus.ACTIVE,
          pricePerDay = 15,
          totalPrice = 120,
          pickupArea = "Ecublens",
          dropOffArea = "Renens",
          pickupAddress = "Route Cantonale 1",
          returnAddress = "Rue de Lausanne 10",
          deposit = 60,
          createdAt = Instant.parse("2026-09-20T10:00:00Z"),
      )

  private val returned =
      active.copy(status = RentalStatus.RETURNED, lateFees = 17, returnedAt = returnedAt)

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

  private val serverDay = LocalDate.of(2026, 10, 8)

  /** The ViewModel for [rental] as [uid], with the server on [serverDay]. */
  private fun viewModel(
      rental: Rental,
      uid: String = "borrower",
      connectivity: FakeConnectivityRepository = FakeConnectivityRepository(),
  ): RentalDetailViewModel {
    rentals = FakeRentalRepository(rentals = listOf(rental), clock = { now })
    rentals.serverNow = serverDay.atTime(12, 0).atZone(ZoneId.of("Europe/Zurich")).toInstant()
    return RentalDetailViewModel(
        rental.id,
        rentals,
        FakeListingRepository(),
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

  /** Asserts what both roles see of [returned], apart from the other person. */
  private fun assertReturned(state: RentalDetailUiState) {
    assertTrue(state.isSupported)
    assertFalse(state.isLoading)
    assertNull(state.errorMsg)
    assertEquals(RentalStatus.RETURNED, state.status)
    assertEquals("Ski helmet", state.itemTitle)
    assertEquals(LocalDate.of(2026, 10, 1), state.startDate)
    assertEquals(LocalDate.of(2026, 10, 8), state.endDate)
    assertEquals(120, state.rentalPrice)
    assertEquals(60, state.deposit)
    assertEquals(17, state.lateFees)
    assertEquals(returnedAt, state.returnedAt)
  }

  @Test
  fun aReturnedRentalIsSupportedForTheBorrower() {
    val state = viewModel(returned).uiState.value

    assertReturned(state)
    assertFalse(state.isLender)
    assertEquals(
        OtherPerson(uid = "owner", name = "Mara Rossi", averageRating = 4.6, reviewCount = 5),
        state.otherPerson,
    )
  }

  @Test
  fun aReturnedRentalIsSupportedForTheLender() {
    val state = viewModel(returned, uid = "owner").uiState.value

    assertReturned(state)
    assertTrue(state.isLender)
    assertEquals(
        OtherPerson(uid = "borrower", name = "Loca Meier", averageRating = 3.0, reviewCount = 2),
        state.otherPerson,
    )
  }

  @Test
  fun aReturnedRentalWithoutAReturnDateIsSupportedWithNoDate() {
    val state = viewModel(returned.copy(returnedAt = null)).uiState.value

    assertTrue(state.isSupported)
    assertEquals(RentalStatus.RETURNED, state.status)
    assertNull(state.returnedAt)
  }

  @Test
  fun lateFeesAreExposedAsStoredAtZeroAndAbove() {
    assertEquals(0, viewModel(returned.copy(lateFees = 0)).uiState.value.lateFees)
    assertEquals(17, viewModel(returned).uiState.value.lateFees)
  }

  @Test
  fun aReturnedRentalNeverReadsTheServersDayAndCannotBeCancelled() {
    val vm = viewModel(returned)

    vm.onScreenResumed()

    val state = vm.uiState.value
    assertEquals(0, rentals.serverNowCalls)
    assertNull(state.serverToday)
    assertFalse(state.isReadingServerToday)
    assertFalse(state.canCancel)
  }

  @Test
  fun anActiveRentalReturnedWhileTheScreenIsOpenShowsAsReturnedAndReadsNoNewDay() {
    val vm = viewModel(active)
    assertEquals(1, rentals.serverNowCalls)

    rentals.setStatus(active.id, RentalStatus.RETURNED, returnedAt = returnedAt)

    val state = vm.uiState.value
    assertTrue(state.isSupported)
    assertEquals(RentalStatus.RETURNED, state.status)
    assertEquals(returnedAt, state.returnedAt)
    vm.onScreenResumed()
    assertEquals(1, rentals.serverNowCalls)
  }

  @Test
  fun goingOfflineThenOnlineOnAReturnedRentalReadsNothingAgain() {
    val connectivity = FakeConnectivityRepository()
    val vm = viewModel(returned, connectivity = connectivity)

    connectivity.setOnline(false)
    connectivity.setOnline(true)

    assertEquals(RentalStatus.RETURNED, vm.uiState.value.status)
    assertEquals(0, rentals.serverNowCalls)
  }
}
