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
import com.swent.polyloop.model.rental.RentalRepository
import com.swent.polyloop.model.rental.RentalStatus
import com.swent.polyloop.model.rental.ServerClock
import com.swent.polyloop.ui.listingdetail.FakeListingRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The Disputed state of Rental detail. */
@OptIn(ExperimentalCoroutinesApi::class)
class RentalDetailViewModelDisputedTest {

  private val now = Instant.parse("2026-11-04T10:00:00Z")

  private val disputed =
      Rental(
          id = "r1",
          listingId = "l1",
          listingTitle = "Camping tent",
          ownerId = "owner",
          ownerName = "Mara",
          borrowerId = "borrower",
          borrowerName = "Loca",
          startDate = LocalDate.of(2026, 10, 1),
          endDate = LocalDate.of(2026, 10, 8),
          status = RentalStatus.DISPUTED,
          pricePerDay = 15,
          totalPrice = 120,
          pickupArea = "Ecublens",
          dropOffArea = "Renens",
          pickupAddress = "Route Cantonale 1",
          returnAddress = "Rue de Lausanne 10",
          deposit = 60,
          lateFees = 34,
          createdAt = now,
      )

  private val profiles =
      FakeProfileRepository(
          listOf(
              UserProfile("owner", "Mara Rossi", "mara@epfl.ch", "", 4.5, 12),
              UserProfile("borrower", "Loca Meier", "loca@epfl.ch", "", 3.0, 2),
          )
      )

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun viewModel(
      signedInUid: String,
      rentals: RentalRepository = FakeRentalRepository(rentals = listOf(disputed), clock = { now }),
  ) =
      RentalDetailViewModel(
          "r1",
          rentals,
          FakeListingRepository(),
          profiles,
          FakeAuthRepository(signedInUser = AuthUser(signedInUid, "$signedInUid@epfl.ch", "")),
          FakeConnectivityRepository(),
          ServerClock(rentals, InMemoryServerClockStore(), elapsedRealtime = { 0L }, bootCount = { 0 }),
      )

  @Test
  fun aDisputedRentalAsLenderShowsItsDetailsAndTheBorrower() {
    val state = viewModel("owner").uiState.value

    assertTrue(state.isSupported)
    assertTrue(state.isLender)
    assertEquals(RentalStatus.DISPUTED, state.status)
    assertEquals(LocalDate.of(2026, 10, 1), state.startDate)
    assertEquals(LocalDate.of(2026, 10, 8), state.endDate)
    assertEquals(120, state.rentalPrice)
    assertEquals(60, state.deposit)
    assertEquals(34, state.lateFees)
    assertEquals(OtherPerson("borrower", "Loca Meier", 3.0, 2), state.otherPerson)
  }

  @Test
  fun aDisputedRentalAsBorrowerShowsItsDetailsAndTheOwner() {
    val state = viewModel("borrower").uiState.value

    assertTrue(state.isSupported)
    assertFalse(state.isLender)
    assertEquals(RentalStatus.DISPUTED, state.status)
    assertEquals(LocalDate.of(2026, 10, 1), state.startDate)
    assertEquals(LocalDate.of(2026, 10, 8), state.endDate)
    assertEquals(120, state.rentalPrice)
    assertEquals(60, state.deposit)
    assertEquals(34, state.lateFees)
    assertEquals(OtherPerson("owner", "Mara Rossi", 4.5, 12), state.otherPerson)
  }

  @Test
  fun missingLateFeesGiveZero() {
    val rentals = FakeRentalRepository(listOf(disputed.copy(lateFees = 0)), clock = { now })

    assertEquals(0, viewModel("borrower", rentals).uiState.value.lateFees)
  }

  @Test
  fun aLiveChangeFromAcceptedToDisputedKeepsTheScreenSupported() {
    val accepted = disputed.copy(status = RentalStatus.ACCEPTED, lateFees = 0)
    val rentals = LiveRentalRepository(accepted)
    val vm = viewModel("borrower", rentals)
    assertEquals(RentalStatus.ACCEPTED, vm.uiState.value.status)

    rentals.live.value = disputed

    val state = vm.uiState.value
    assertTrue(state.isSupported)
    assertEquals(RentalStatus.DISPUTED, state.status)
    assertEquals(34, state.lateFees)
    assertEquals("Camping tent", state.itemTitle)
  }

  /**
   * A [RentalRepository] whose rental the test changes through [live]: the fake has no call that
   * moves a rental to Disputed, as only a Cloud Function will.
   */
  private class LiveRentalRepository(first: Rental) :
      RentalRepository by FakeRentalRepository(listOf(first)) {
    val live = MutableStateFlow<Rental?>(first)

    override suspend fun getRental(id: String): Result<Rental?> = Result.success(live.value)

    override fun observeRental(id: String): Flow<Rental?> = live
  }
}
