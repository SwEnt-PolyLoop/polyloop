// Made with Claude.

package com.swent.polyloop.model.rental

import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.listing.BookedRange
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.conflictsWithBuffer
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [RentalRepository] for tests and previews; the app itself never uses it. It lives in
 * `main` so that unit tests, Compose tests and `@Preview`s can all share it.
 *
 * Holds the rentals in memory: every change is emitted to the observers. When [failure] is set,
 * every call fails with it; an observer throws it when it subscribes or at the next change, not as
 * soon as it is set. When [gate] is set, the suspending calls wait for it to complete, so tests can
 * see a loading state. [clock] gives the creation time of new rentals and the current time of the
 * negotiation; the server's time, which [serverNow] returns, is set separately.
 *
 * The negotiation follows the rules of the Cloud Functions, except for what the fake cannot know:
 * - it does not know who calls it, so it does not check the caller's role;
 * - it has no listings, so it does not check that the dates are still free (neither when an offer
 *   is made nor when it is accepted) and does not refuse a listing that is gone.
 *
 * A refusal is an [IllegalStateException].
 */
class FakeRentalRepository(
    rentals: List<Rental> = emptyList(),
    var failure: Throwable? = null,
    var gate: CompletableDeferred<Unit>? = null,
    private val clock: () -> Instant = { Instant.now() },
) : RentalRepository {

  private val stored = MutableStateFlow(rentals)

  /** The addresses of each offer, out of the rental until it is accepted, as on the server. */
  private val offeredAddresses = mutableMapOf<String, Pair<String, String>>()

  /** The rentals currently stored, e.g. to check what a ViewModel requested. */
  val rentals: List<Rental>
    get() = stored.value

  /** The time [serverNow] returns, apart from [clock] so that tests can set it on its own. */
  var serverNow: Instant = Instant.parse("2026-10-13T10:00:00Z")

  /** How many times [serverNow] was called, e.g. to check that a ViewModel did not ask. */
  var serverNowCalls: Int = 0
    private set

  override suspend fun requestRental(
      listing: Listing,
      ownerName: String,
      borrower: AuthUser,
      startDate: LocalDate,
      endDate: LocalDate,
  ): Result<Rental> {
    gate?.await()
    failure?.let {
      return Result.failure(it)
    }
    if (endDate.isBefore(startDate)) {
      return Result.failure(
          IllegalArgumentException("endDate $endDate is before startDate $startDate")
      )
    }
    val rental =
        Rental(
            id = newId(),
            listingId = listing.id,
            listingTitle = listing.title,
            listingPhotoUrl = listing.photoUrls.firstOrNull().orEmpty(),
            ownerId = listing.ownerId,
            ownerName = ownerName.trim(),
            borrowerId = borrower.uid,
            borrowerName = borrower.name,
            startDate = startDate,
            endDate = endDate,
            status = RentalStatus.REQUESTED,
            pricePerDay = listing.pricePerDay,
            totalPrice = Rental.totalPrice(listing.pricePerDay, startDate, endDate),
            pickupArea = listing.pickupArea,
            dropOffArea = listing.dropOffArea,
            createdAt = clock(),
        )
    stored.update { it + rental }
    return Result.success(rental)
  }

  override fun observeRentalsAsBorrower(uid: String): Flow<List<Rental>> = observe {
    it.borrowerId == uid
  }

  override fun observeRentalsAsLender(uid: String): Flow<List<Rental>> = observe {
    it.ownerId == uid
  }

  override suspend fun getRental(id: String): Result<Rental?> {
    gate?.await()
    return failure?.let { Result.failure(it) } ?: Result.success(stored.value.find { it.id == id })
  }

  override fun observeRental(id: String): Flow<Rental?> =
      stored
          .map { rentals ->
            failure?.let { throw it }
            rentals.find { it.id == id }
          }
          .distinctUntilChanged()

  override suspend fun makeOffer(
      rentalId: String,
      deposit: Int,
      pickupAddress: String,
      returnAddress: String,
  ): Result<Unit> =
      negotiate(rentalId) { rental ->
        check(rental.offer == null) { "An offer was already sent" }
        checkDeposit(deposit)
        check(pickupAddress.isNotBlank() && returnAddress.isNotBlank()) { "Addresses are required" }
        val now = clock()
        check(firstDay(rental).isAfter(now)) { "The rental's first day has come" }
        offeredAddresses[rentalId] = pickupAddress.trim() to returnAddress.trim()
        rental.copy(
            offer =
                DepositOffer(
                    amount = deposit,
                    authorId = rental.ownerId,
                    isCounter = false,
                    expiresAt = expiry(rental, now),
                )
        )
      }

  override suspend fun acceptOffer(rentalId: String): Result<Unit> =
      negotiate(rentalId) { rental ->
        val offer = liveOffer(rental)
        val addresses = offeredAddresses.remove(rentalId)
        val dates = BookedRange(rental.startDate, rental.endDate)
        stored.update { rentals ->
          rentals.map { other ->
            val overlaps =
                other.id != rentalId &&
                    other.listingId == rental.listingId &&
                    other.status == RentalStatus.REQUESTED &&
                    conflictsWithBuffer(dates, BookedRange(other.startDate, other.endDate))
            if (overlaps) {
              offeredAddresses.remove(other.id)
              other.copy(status = RentalStatus.DECLINED, offer = null)
            } else {
              other
            }
          }
        }
        rental.copy(
            status = RentalStatus.ACCEPTED,
            deposit = offer.amount,
            offer = null,
            pickupAddress = addresses?.first.orEmpty(),
            returnAddress = addresses?.second.orEmpty(),
        )
      }

  override suspend fun declineOffer(rentalId: String): Result<Unit> =
      negotiate(rentalId) { rental ->
        liveOffer(rental)
        offeredAddresses.remove(rentalId)
        rental.copy(status = RentalStatus.DECLINED, offer = null)
      }

  override suspend fun counterOffer(rentalId: String, deposit: Int): Result<Unit> =
      negotiate(rentalId) { rental ->
        val offer = liveOffer(rental)
        check(!offer.isCounter) { "A counteroffer cannot be countered" }
        checkDeposit(deposit)
        rental.copy(
            offer =
                DepositOffer(
                    amount = deposit,
                    authorId = rental.borrowerId,
                    isCounter = true,
                    previousAmount = offer.amount,
                    expiresAt = expiry(rental, clock()),
                )
        )
      }

  override suspend fun declineRequest(rentalId: String): Result<Unit> =
      negotiate(rentalId) { rental ->
        check(rental.offer == null) { "An offer is waiting for an answer" }
        rental.copy(status = RentalStatus.DECLINED)
      }

  override suspend fun serverNow(): Result<Instant> {
    serverNowCalls++
    gate?.await()
    return failure?.let { Result.failure(it) } ?: Result.success(serverNow)
  }

  /**
   * Runs one step of the negotiation on the REQUESTED rental [rentalId] and stores what [step]
   * returns. A failed `check` in [step] is reported as a failure, and nothing is stored.
   */
  private suspend fun negotiate(rentalId: String, step: (Rental) -> Rental): Result<Unit> {
    gate?.await()
    failure?.let {
      return Result.failure(it)
    }
    return runCatching {
      val rental = checkNotNull(stored.value.find { it.id == rentalId }) { "No rental $rentalId" }
      check(rental.status == RentalStatus.REQUESTED) { "Rental $rentalId is ${rental.status}" }
      val updated = step(rental)
      stored.update { rentals -> rentals.map { if (it.id == rentalId) updated else it } }
    }
  }

  private fun liveOffer(rental: Rental): DepositOffer {
    val offer = checkNotNull(rental.offer) { "No offer is waiting for an answer" }
    check(offer.expiresAt.isAfter(clock())) { "This offer has expired" }
    return offer
  }

  private fun checkDeposit(deposit: Int) {
    check(deposit in 1..Listing.MAX_AMOUNT) { "The deposit is out of range" }
  }

  private fun firstDay(rental: Rental): Instant =
      rental.startDate.atStartOfDay().toInstant(ZoneOffset.UTC)

  /** One week after [now], or the rental's first day if that comes sooner. */
  private fun expiry(rental: Rental, now: Instant): Instant =
      minOf(now.plus(OFFER_LIFETIME), firstDay(rental))

  /** "rental-N" with the smallest N not used yet, so it never clashes with the initial rentals. */
  private fun newId(): String {
    val used = stored.value.map { it.id }.toSet()
    return generateSequence(1) { it + 1 }.map { "rental-$it" }.first { it !in used }
  }

  private fun observe(keep: (Rental) -> Boolean): Flow<List<Rental>> =
      stored
          .map { rentals ->
            failure?.let { throw it }
            rentals.filter(keep).sortedByDescending { it.createdAt }
          }
          .distinctUntilChanged()

  private companion object {
    val OFFER_LIFETIME: Duration = Duration.ofDays(7)
  }
}
