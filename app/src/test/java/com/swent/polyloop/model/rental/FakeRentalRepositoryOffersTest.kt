// Made with Claude.

package com.swent.polyloop.model.rental

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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The deposit negotiation of [FakeRentalRepository], which follows the Cloud Functions' rules. */
class FakeRentalRepositoryOffersTest {

  private var now = Instant.parse("2026-11-04T10:00:00Z")

  private fun rental(
      id: String = "r1",
      listingId: String = "listing-1",
      start: LocalDate = LocalDate.of(2026, 12, 10),
      end: LocalDate = LocalDate.of(2026, 12, 12),
      status: RentalStatus = RentalStatus.REQUESTED,
  ) =
      Rental(
          id = id,
          listingId = listingId,
          listingTitle = "Camping tent",
          ownerId = "owner",
          ownerName = "Vida",
          borrowerId = "borrower",
          borrowerName = "Loca",
          startDate = start,
          endDate = end,
          status = status,
          pricePerDay = 5,
          totalPrice = 15,
          pickupArea = "Ecublens",
          dropOffArea = "Renens",
          createdAt = now,
      )

  private fun repository(vararg rentals: Rental) =
      FakeRentalRepository(rentals = rentals.toList(), clock = { now })

  private suspend fun FakeRentalRepository.stored(id: String = "r1"): Rental =
      getRental(id).getOrThrow()!!

  private suspend fun FakeRentalRepository.offered(deposit: Int = 180) = apply {
    makeOffer("r1", deposit, " Route Cantonale 1 ", "Rue de Lausanne 10").getOrThrow()
  }

  @Test
  fun makeOfferStoresTheOfferAndKeepsTheAddressesOutOfTheRental() = runTest {
    val repository = repository(rental()).offered()

    val stored = repository.stored()
    assertEquals(
        DepositOffer(
            amount = 180,
            authorId = "owner",
            isCounter = false,
            expiresAt = Instant.parse("2026-11-11T10:00:00Z"),
        ),
        stored.offer,
    )
    assertEquals(RentalStatus.REQUESTED, stored.status)
    assertEquals("", stored.pickupAddress)
  }

  @Test
  fun anOfferExpiresOnTheFirstDayWhenThatComesBeforeOneWeek() = runTest {
    val soon = rental(start = LocalDate.of(2026, 11, 8), end = LocalDate.of(2026, 11, 9))
    val repository = repository(soon).offered()

    assertEquals(Instant.parse("2026-11-08T00:00:00Z"), repository.stored().offer?.expiresAt)
  }

  @Test
  fun makeOfferRefusesWrongInputOrStateAndStoresNothing() = runTest {
    val repository = repository(rental())

    assertTrue(repository.makeOffer("r1", 0, "A", "B").isFailure)
    assertTrue(repository.makeOffer("r1", 100_000, "A", "B").isFailure)
    assertTrue(repository.makeOffer("r1", 10, " ", "B").isFailure)
    assertTrue(repository.makeOffer("missing", 10, "A", "B").isFailure)
    assertNull(repository.stored().offer)

    repository.offered()
    assertTrue(repository.makeOffer("r1", 10, "A", "B").isFailure)
    assertEquals(180, repository.stored().offer?.amount)
  }

  @Test
  fun makeOfferRefusesARentalWhoseFirstDayHasCome() = runTest {
    val started = rental(start = LocalDate.of(2026, 11, 4), end = LocalDate.of(2026, 11, 5))
    val repository = repository(started)

    assertTrue(repository.makeOffer("r1", 10, "A", "B").isFailure)
  }

  @Test
  fun acceptOfferMakesTheRentalAcceptedWithTheDepositAndAddresses() = runTest {
    val repository = repository(rental()).offered()

    assertTrue(repository.acceptOffer("r1").isSuccess)

    val stored = repository.stored()
    assertEquals(RentalStatus.ACCEPTED, stored.status)
    assertEquals(180, stored.deposit)
    assertNull(stored.offer)
    assertEquals("Route Cantonale 1", stored.pickupAddress)
    assertEquals("Rue de Lausanne 10", stored.returnAddress)
  }

  @Test
  fun acceptOfferDeclinesOverlappingRequestsOfTheSameListingOnly() = runTest {
    val touching =
        rental("touching", start = LocalDate.of(2026, 12, 13), end = LocalDate.of(2026, 12, 14))
    val free = rental("free", start = LocalDate.of(2026, 12, 14), end = LocalDate.of(2026, 12, 15))
    val elsewhere = rental("elsewhere", listingId = "listing-2")
    val repository = repository(rental(), touching, free, elsewhere).offered()

    repository.acceptOffer("r1").getOrThrow()

    assertEquals(RentalStatus.DECLINED, repository.stored("touching").status)
    assertEquals(RentalStatus.REQUESTED, repository.stored("free").status)
    assertEquals(RentalStatus.REQUESTED, repository.stored("elsewhere").status)
  }

  @Test
  fun declineOfferMakesTheRentalDeclinedAndRemovesTheOffer() = runTest {
    val repository = repository(rental()).offered()

    repository.declineOffer("r1").getOrThrow()

    assertEquals(RentalStatus.DECLINED, repository.stored().status)
    assertNull(repository.stored().offer)
  }

  @Test
  fun counterOfferReplacesTheOfferAndKeepsThePreviousAmount() = runTest {
    val repository = repository(rental()).offered()

    repository.counterOffer("r1", 120).getOrThrow()

    val offer = repository.stored().offer
    assertEquals(120, offer?.amount)
    assertEquals(180, offer?.previousAmount)
    assertEquals("borrower", offer?.authorId)
    assertEquals(true, offer?.isCounter)
  }

  @Test
  fun aCounterofferCannotBeCounteredButCanBeAcceptedAtItsAmount() = runTest {
    val repository = repository(rental()).offered()
    repository.counterOffer("r1", 120).getOrThrow()

    assertFalse(repository.counterOffer("r1", 150).isSuccess)
    repository.acceptOffer("r1").getOrThrow()

    assertEquals(120, repository.stored().deposit)
  }

  @Test
  fun answersAreRefusedWithoutAnOfferOrOnceItExpired() = runTest {
    val repository = repository(rental())
    assertTrue(repository.acceptOffer("r1").isFailure)
    assertTrue(repository.declineOffer("r1").isFailure)
    assertTrue(repository.counterOffer("r1", 10).isFailure)

    repository.offered()
    now = Instant.parse("2026-11-11T10:00:00Z")

    assertTrue(repository.acceptOffer("r1").isFailure)
    assertEquals(RentalStatus.REQUESTED, repository.stored().status)
  }

  @Test
  fun declineRequestWorksOnlyWithoutAnOffer() = runTest {
    val withOffer = repository(rental()).offered()
    assertTrue(withOffer.declineRequest("r1").isFailure)

    val noOffer = repository(rental())
    noOffer.declineRequest("r1").getOrThrow()
    assertEquals(RentalStatus.DECLINED, noOffer.stored().status)
  }

  @Test
  fun aRentalThatIsNotRequestedCannotBeNegotiated() = runTest {
    val repository = repository(rental(status = RentalStatus.ACCEPTED))

    assertTrue(repository.makeOffer("r1", 10, "A", "B").isFailure)
    assertTrue(repository.declineRequest("r1").isFailure)
  }

  @Test
  fun observeRentalEmitsChangesAndNullForAMissingRental() = runTest {
    val repository = repository(rental())
    assertNull(repository.observeRental("missing").first())
    assertNull(repository.observeRental("r1").first()?.offer)

    repository.offered()

    assertEquals(180, repository.observeRental("r1").first()?.offer?.amount)
  }

  @Test
  fun negotiationCallsFailWithTheFailureAndWaitForTheGate() = runTest {
    val error = IllegalStateException("down")
    val failing = repository(rental()).apply { failure = error }
    assertSame(error, failing.makeOffer("r1", 10, "A", "B").exceptionOrNull())

    val gate = CompletableDeferred<Unit>()
    val gated = repository(rental()).apply { this.gate = gate }
    val call = async { gated.makeOffer("r1", 10, "A", "B") }
    yield()
    assertFalse(call.isCompleted)
    gate.complete(Unit)
    assertTrue(call.await().isSuccess)
  }
}
