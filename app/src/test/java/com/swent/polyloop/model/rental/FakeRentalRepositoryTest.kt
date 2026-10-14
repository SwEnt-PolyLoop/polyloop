// Made with Claude.

package com.swent.polyloop.model.rental

import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.model.listing.ListingStatus
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FakeRentalRepositoryTest {

  private val listing =
      Listing(
          id = "listing-1",
          ownerId = "owner",
          title = "Camping tent",
          description = "Light 2-person tent.",
          category = ListingCategory.SPORTS_OUTDOOR,
          photoUrls = List(3) { "https://example.com/$it.jpg" },
          pricePerDay = 5,
          itemValue = 120,
          availableFrom = LocalDate.of(2026, 10, 1),
          availableTo = LocalDate.of(2026, 12, 31),
          status = ListingStatus.PUBLISHED,
          pickupArea = "Ecublens",
          dropOffArea = "Renens",
      )

  private val borrower = AuthUser(uid = "borrower", email = "loca@epfl.ch", name = "Loca")
  private val start = LocalDate.of(2026, 10, 20)
  private val end = LocalDate.of(2026, 10, 22)

  // Each call returns one second later, so creation times are distinct and ordered.
  private var now = Instant.parse("2026-10-13T10:00:00Z")
  private val clock = { now.also { now = now.plusSeconds(1) } }

  private fun repository() = FakeRentalRepository(clock = clock)

  @Test
  fun requestRentalCreatesARequestedRentalWithCopiedFields() = runTest {
    val rental = repository().requestRental(listing, "Vida", borrower, start, end).getOrThrow()

    assertEquals("rental-1", rental.id)
    assertEquals(RentalStatus.REQUESTED, rental.status)
    assertEquals("listing-1", rental.listingId)
    assertEquals("Camping tent", rental.listingTitle)
    assertEquals("owner", rental.ownerId)
    assertEquals("Vida", rental.ownerName)
    assertEquals("borrower", rental.borrowerId)
    assertEquals("Loca", rental.borrowerName)
    assertEquals(5, rental.pricePerDay)
    assertEquals(15, rental.totalPrice)
    assertEquals("Ecublens", rental.pickupArea)
    assertEquals("Renens", rental.dropOffArea)
    assertEquals(rental.createdAt, rental.lastMessageAt)
  }

  @Test
  fun requestRentalStoresABlankOwnerNameAsEmpty() = runTest {
    val repository = repository()

    assertEquals(
        "",
        repository.requestRental(listing, "", borrower, start, end).getOrThrow().ownerName,
    )
    assertEquals(
        "",
        repository.requestRental(listing, " ", borrower, start, end).getOrThrow().ownerName,
    )
  }

  @Test
  fun requestRentalGivesEachRentalANewId() = runTest {
    val repository = repository()
    repository.requestRental(listing, "Vida", borrower, start, end)
    repository.requestRental(listing, "Vida", borrower, start, end)

    assertEquals(listOf("rental-1", "rental-2"), repository.rentals.map { it.id })
  }

  @Test
  fun observersSeeOnlyTheirSideNewestFirst() = runTest {
    val repository = repository()
    val first = repository.requestRental(listing, "Vida", borrower, start, end).getOrThrow()
    val second = repository.requestRental(listing, "Vida", borrower, start, end).getOrThrow()

    assertEquals(listOf(second, first), repository.observeRentalsAsBorrower("borrower").first())
    assertEquals(listOf(second, first), repository.observeRentalsAsLender("owner").first())
    assertTrue(repository.observeRentalsAsBorrower("owner").first().isEmpty())
    assertTrue(repository.observeRentalsAsLender("borrower").first().isEmpty())
  }

  @Test
  fun observersReceiveNewRequests() = runTest {
    val repository = repository()
    val emissions = async { repository.observeRentalsAsLender("owner").first { it.isNotEmpty() } }
    yield()

    val rental = repository.requestRental(listing, "Vida", borrower, start, end).getOrThrow()

    assertEquals(listOf(rental), emissions.await())
  }

  @Test
  fun getRentalFindsByIdOrReturnsNull() = runTest {
    val repository = repository()
    val rental = repository.requestRental(listing, "Vida", borrower, start, end).getOrThrow()

    assertEquals(rental, repository.getRental("rental-1").getOrThrow())
    assertNull(repository.getRental("missing").getOrThrow())
  }

  @Test
  fun everyCallFailsWithTheFailure() = runTest {
    val error = IllegalStateException("offline")
    val repository = repository().apply { failure = error }

    assertEquals(
        error,
        repository.requestRental(listing, "Vida", borrower, start, end).exceptionOrNull(),
    )
    assertEquals(error, repository.getRental("rental-1").exceptionOrNull())
    assertTrue(repository.rentals.isEmpty())
    try {
      repository.observeRentalsAsBorrower("borrower").first()
      fail("Expected the observer to throw")
    } catch (e: IllegalStateException) {
      assertEquals(error, e)
    }
  }

  @Test
  fun callsWaitForTheGate() = runTest {
    val gate = CompletableDeferred<Unit>()
    val repository = repository().apply { this.gate = gate }

    val request = async { repository.requestRental(listing, "Vida", borrower, start, end) }
    val read = async { repository.getRental("rental-1") }
    yield()
    assertFalse(request.isCompleted)
    assertFalse(read.isCompleted)

    gate.complete(Unit)
    assertTrue(request.await().isSuccess)
    assertTrue(read.await().isSuccess)
  }

  @Test
  fun startsWithTheGivenRentals() = runTest {
    val stored = repository().requestRental(listing, "Vida", borrower, start, end).getOrThrow()
    val repository = FakeRentalRepository(rentals = listOf(stored))

    assertEquals(listOf(stored), repository.rentals)
    assertEquals(stored, repository.getRental(stored.id).getOrThrow())
  }

  @Test
  fun newIdsNeverClashWithTheInitialRentals() = runTest {
    val stored = repository().requestRental(listing, "Vida", borrower, start, end).getOrThrow()
    val repository = FakeRentalRepository(rentals = listOf(stored))

    val created = repository.requestRental(listing, "Vida", borrower, start, end).getOrThrow()

    assertEquals("rental-2", created.id)
    assertEquals(listOf("rental-1", "rental-2"), repository.rentals.map { it.id })
  }

  @Test
  fun requestRentalFailsWhenTheDatesAreReversed() = runTest {
    val repository = repository()

    val result = repository.requestRental(listing, "Vida", borrower, end, start)

    assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    assertTrue(repository.rentals.isEmpty())
  }

  @Test
  fun requestRentalAcceptsASingleDay() = runTest {
    val rental = repository().requestRental(listing, "Vida", borrower, start, start).getOrThrow()

    assertEquals(5, rental.totalPrice)
  }
}
