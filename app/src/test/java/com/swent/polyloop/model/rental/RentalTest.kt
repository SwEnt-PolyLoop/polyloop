// Made with Claude.

package com.swent.polyloop.model.rental

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class RentalTest {

  private val createdAt = Instant.parse("2026-10-13T10:00:00Z")

  private val rental =
      Rental(
          id = "rental-1",
          listingId = "listing-1",
          listingTitle = "Camping tent",
          ownerId = "owner",
          ownerName = "Vida",
          borrowerId = "borrower",
          borrowerName = "Loca",
          startDate = LocalDate.of(2026, 10, 20),
          endDate = LocalDate.of(2026, 10, 22),
          status = RentalStatus.REQUESTED,
          pricePerDay = 5,
          totalPrice = 15,
          pickupArea = "Ecublens",
          dropOffArea = "Renens",
          createdAt = createdAt,
      )

  @Test
  fun lastMessageAtStartsAtCreation() {
    assertEquals(createdAt, rental.lastMessageAt)
    assertEquals("", rental.lastMessage)
  }

  @Test
  fun exactAddressesAreEmptyUntilAccepted() {
    assertEquals("", rental.pickupAddress)
    assertEquals("", rental.returnAddress)
  }

  @Test
  fun participantsAreOwnerThenBorrower() {
    assertEquals(listOf("owner", "borrower"), rental.participants)
  }

  @Test
  fun otherPersonNameDependsOnWhoLooks() {
    assertEquals("Loca", rental.otherPersonName("owner"))
    assertEquals("Vida", rental.otherPersonName("borrower"))
  }

  @Test
  fun totalPriceCountsBothDays() {
    val start = LocalDate.of(2026, 10, 20)
    assertEquals(15, Rental.totalPrice(5, start, LocalDate.of(2026, 10, 22)))
    assertEquals(5, Rental.totalPrice(5, start, start))
  }

  @Test(expected = IllegalArgumentException::class)
  fun totalPriceRejectsReversedDates() {
    Rental.totalPrice(5, LocalDate.of(2026, 10, 22), LocalDate.of(2026, 10, 20))
  }

  @Test
  fun otherPersonNameCanBeEmpty() {
    assertEquals("", rental.copy(ownerName = "").otherPersonName("borrower"))
  }

  @Test
  fun totalPriceCrossesMonthEnd() {
    assertEquals(
        12,
        Rental.totalPrice(3, LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 2)),
    )
  }
}
