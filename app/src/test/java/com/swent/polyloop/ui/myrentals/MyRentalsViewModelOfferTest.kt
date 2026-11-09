// Made with Claude.

package com.swent.polyloop.ui.myrentals

import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.auth.FakeAuthRepository
import com.swent.polyloop.model.rental.DepositOffer
import com.swent.polyloop.model.rental.FakeRentalRepository
import com.swent.polyloop.model.rental.Rental
import com.swent.polyloop.model.rental.RentalStatus
import java.time.Instant
import java.time.LocalDate
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

/** The deposit offer on the rows of My rentals. */
@OptIn(ExperimentalCoroutinesApi::class)
class MyRentalsViewModelOfferTest {

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

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun viewModel(uid: String, rental: Rental) =
      MyRentalsViewModel(
          FakeRentalRepository(rentals = listOf(rental), clock = { now }),
          FakeAuthRepository(
              signedInUser = AuthUser(uid = uid, email = "$uid@epfl.ch", name = uid)
          ),
      )

  @Test
  fun aRequestWithoutAnOfferHasNone() {
    val row = viewModel("owner", request).uiState.value.lendingRequests.single()

    assertNull(row.offer)
    assertFalse(row.isOfferMine)
  }

  @Test
  fun theLendersOwnOfferIsMarkedAsMine() {
    val row = viewModel("owner", request.copy(offer = offer)).uiState.value.lendingRequests.single()

    assertEquals(offer, row.offer)
    assertTrue(row.isOfferMine)
  }

  @Test
  fun theBorrowerSeesTheOfferAsReceived() {
    val row =
        viewModel("borrower", request.copy(offer = offer)).uiState.value.borrowingRequests.single()

    assertEquals(offer, row.offer)
    assertFalse(row.isOfferMine)
  }
}
