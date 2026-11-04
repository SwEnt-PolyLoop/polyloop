// Made with Claude.

package com.swent.polyloop.ui.rentaldetail

import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.auth.FakeAuthRepository
import com.swent.polyloop.model.connectivity.FakeConnectivityRepository
import com.swent.polyloop.model.profile.FakeProfileRepository
import com.swent.polyloop.model.rental.FakeRentalRepository
import com.swent.polyloop.model.rental.Rental
import com.swent.polyloop.model.rental.RentalStatus
import com.swent.polyloop.ui.listingdetail.FakeListingRepository
import java.time.Instant
import java.time.LocalDate
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

/** The Accepted state of Rental detail. */
@OptIn(ExperimentalCoroutinesApi::class)
class RentalDetailViewModelAcceptedTest {

  private val now = Instant.parse("2026-11-04T10:00:00Z")

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

  private val accepted =
      request.copy(
          status = RentalStatus.ACCEPTED,
          deposit = 60,
          pickupAddress = "Route Cantonale 1",
          returnAddress = "Rue de Lausanne 10",
      )

  private lateinit var rentals: FakeRentalRepository

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun viewModel(rental: Rental): RentalDetailViewModel {
    rentals = FakeRentalRepository(rentals = listOf(rental), clock = { now })
    return RentalDetailViewModel(
        rental.id,
        rentals,
        FakeListingRepository(),
        FakeProfileRepository(),
        FakeAuthRepository(signedInUser = AuthUser("borrower", "l@epfl.ch", "Loca")),
        FakeConnectivityRepository(),
    )
  }

  @Test
  fun anAcceptedRentalGivesItsDepositAndAddresses() {
    val state = viewModel(accepted).uiState.value

    assertTrue(state.isSupported)
    assertEquals(RentalStatus.ACCEPTED, state.status)
    assertEquals(60, state.deposit)
    assertEquals("Route Cantonale 1", state.pickupAddress)
    assertEquals("Rue de Lausanne 10", state.returnAddress)
    assertNull(state.offer)
  }

  @Test
  fun aRequestHasNoDepositOrAddressesYet() {
    val state = viewModel(request).uiState.value

    assertEquals(0, state.deposit)
    assertEquals("", state.pickupAddress)
  }

  @Test
  fun anOfferAcceptedWhileTheScreenIsOpenShowsTheAcceptedState() = runTest {
    val vm = viewModel(request)
    rentals.makeOffer("r1", 180, "Route Cantonale 1", "Rue de Lausanne 10").getOrThrow()

    rentals.acceptOffer("r1").getOrThrow()

    val state = vm.uiState.value
    assertTrue(state.isSupported)
    assertEquals(RentalStatus.ACCEPTED, state.status)
    assertEquals(180, state.deposit)
    assertEquals("Route Cantonale 1", state.pickupAddress)
    assertNull(state.offer)
  }

  @Test
  fun aLaterStatusIsStillUnsupported() {
    val state = viewModel(accepted.copy(status = RentalStatus.ACTIVE)).uiState.value

    assertFalse(state.isSupported)
    assertEquals("Camping tent", state.itemTitle)
  }
}
