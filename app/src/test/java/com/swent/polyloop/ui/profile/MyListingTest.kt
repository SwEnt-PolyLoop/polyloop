// Made with Claude.

package com.swent.polyloop.ui.profile

import com.swent.polyloop.model.rental.Rental
import com.swent.polyloop.model.rental.RentalStatus
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class MyListingTest {

  private fun rental(status: RentalStatus, listingId: String = "tent") =
      Rental(
          id = "$listingId-$status",
          listingId = listingId,
          listingTitle = "Tent",
          ownerId = "me",
          ownerName = "John",
          borrowerId = "jane",
          borrowerName = "Jane",
          startDate = LocalDate.of(2026, 10, 10),
          endDate = LocalDate.of(2026, 10, 12),
          status = status,
          pricePerDay = 10,
          totalPrice = 30,
          pickupArea = "Ecublens",
          dropOffArea = "Ecublens",
          createdAt = Instant.EPOCH,
      )

  private fun stateOf(vararg statuses: RentalStatus) =
      rentalStateOf("tent", statuses.map { rental(it) })

  @Test
  fun overdueWinsOverEveryOtherRental() {
    assertEquals(
        ListingRentalState.OVERDUE,
        stateOf(RentalStatus.REQUESTED, RentalStatus.OVERDUE, RentalStatus.ACCEPTED),
    )
  }

  @Test
  fun onLoanWinsOverLaterBookingsAndRequests() {
    assertEquals(
        ListingRentalState.ON_LOAN,
        stateOf(RentalStatus.ACCEPTED, RentalStatus.ACTIVE, RentalStatus.REQUESTED),
    )
  }

  @Test
  fun bookedWinsOverRequested() {
    assertEquals(
        ListingRentalState.BOOKED,
        stateOf(RentalStatus.REQUESTED, RentalStatus.ACCEPTED),
    )
  }

  @Test
  fun finishedOrRefusedRentalsLeaveTheListingAvailable() {
    assertEquals(
        ListingRentalState.AVAILABLE,
        stateOf(RentalStatus.CLOSED, RentalStatus.CANCELLED, RentalStatus.DECLINED),
    )
  }

  @Test
  fun rentalsOfOtherListingsAreIgnored() {
    val rentals = listOf(rental(RentalStatus.ACTIVE, listingId = "bike"))

    assertEquals(ListingRentalState.AVAILABLE, rentalStateOf("tent", rentals))
  }
}
