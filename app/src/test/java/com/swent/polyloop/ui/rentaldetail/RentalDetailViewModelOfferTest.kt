// Made with Claude.

package com.swent.polyloop.ui.rentaldetail

import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.auth.FakeAuthRepository
import com.swent.polyloop.model.connectivity.FakeConnectivityRepository
import com.swent.polyloop.model.profile.FakeProfileRepository
import com.swent.polyloop.model.rental.DepositOffer
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
import kotlinx.coroutines.flow.flow
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

/** The deposit offer on Rental detail: read with the rental, then followed live. */
@OptIn(ExperimentalCoroutinesApi::class)
class RentalDetailViewModelOfferTest {

  private val now = Instant.parse("2026-11-04T10:00:00Z")
  private val offer = DepositOffer(180, "owner", false, null, Instant.parse("2026-11-11T10:00:00Z"))

  private val request =
      Rental(
          id = "r1",
          listingId = "l1",
          listingTitle = "Camping tent",
          ownerId = "owner",
          ownerName = "Mara",
          borrowerId = "borrower",
          borrowerName = "Loca",
          startDate = LocalDate.of(2026, 12, 10),
          endDate = LocalDate.of(2026, 12, 12),
          status = RentalStatus.REQUESTED,
          pricePerDay = 5,
          totalPrice = 15,
          pickupArea = "Ecublens",
          dropOffArea = "Renens",
          createdAt = now,
      )

  private val lender = AuthUser(uid = "owner", email = "m@epfl.ch", name = "Mara")
  private val borrower = AuthUser(uid = "borrower", email = "l@epfl.ch", name = "Loca")

  private lateinit var rentals: FakeRentalRepository

  // Also runs the server clock's reads, so they finish before each assertion.
  private val testDispatcher = UnconfinedTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    rentals = FakeRentalRepository(rentals = listOf(request), clock = { now })
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun viewModel(user: AuthUser = borrower, repository: RentalRepository = rentals) =
      RentalDetailViewModel(
          "r1",
          repository,
          FakeListingRepository(),
          FakeProfileRepository(),
          FakeAuthRepository(signedInUser = user),
          FakeConnectivityRepository(),
          ServerClock(
              repository,
              InMemoryServerClockStore(),
              elapsedRealtime = { 0L },
              bootCount = { 0 },
              ioDispatcher = testDispatcher,
          ),
      )

  @Test
  fun aRequestWithoutAnOfferHasNone() {
    assertNull(viewModel().uiState.value.offer)
  }

  @Test
  fun theBorrowerSeesTheLendersOfferAsReceived() {
    rentals = FakeRentalRepository(listOf(request.copy(offer = offer)), clock = { now })

    val state = viewModel().uiState.value

    assertEquals(offer, state.offer)
    assertFalse(state.isOfferMine)
  }

  @Test
  fun theLenderSeesTheirOfferAsTheirOwn() {
    rentals = FakeRentalRepository(listOf(request.copy(offer = offer)), clock = { now })

    assertTrue(viewModel(lender).uiState.value.isOfferMine)
  }

  @Test
  fun theOfferIsFollowedLive() = runTest {
    val vm = viewModel()

    rentals.makeOffer("r1", 180, "A", "B").getOrThrow()
    assertEquals(180, vm.uiState.value.offer?.amount)

    rentals.counterOffer("r1", 120).getOrThrow()
    assertEquals(120, vm.uiState.value.offer?.amount)
    assertTrue(vm.uiState.value.isOfferMine)
  }

  @Test
  fun aRentalThatLeavesRequestedShowsThePlaceholder() = runTest {
    val vm = viewModel()

    rentals.declineRequest("r1").getOrThrow()

    assertFalse(vm.uiState.value.isSupported)
    assertEquals("Camping tent", vm.uiState.value.itemTitle)
  }

  @Test
  fun aReadErrorWhileFollowingKeepsTheRentalButHidesItsOffer() {
    rentals = FakeRentalRepository(listOf(request.copy(offer = offer)), clock = { now })
    val failing =
        object : RentalRepository by rentals {
          override fun observeRental(id: String): Flow<Rental?> = flow { error("down") }
        }

    val state = viewModel(repository = failing).uiState.value

    assertEquals("Camping tent", state.itemTitle)
    assertNull(state.errorMsg)
    assertNull(state.offer)
    assertFalse(state.isOfferMine)
  }
}
