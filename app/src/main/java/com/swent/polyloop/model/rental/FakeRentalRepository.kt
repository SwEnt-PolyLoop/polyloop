// Made with Claude.

package com.swent.polyloop.model.rental

import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.listing.BookedRange
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.conflictsWithBuffer
import java.time.Instant
import java.time.LocalDate
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
 * see a loading state. [clock] gives the creation time of new rentals.
 */
class FakeRentalRepository(
    rentals: List<Rental> = emptyList(),
    var failure: Throwable? = null,
    var gate: CompletableDeferred<Unit>? = null,
    private val clock: () -> Instant = { Instant.now() },
) : RentalRepository {

  private val stored = MutableStateFlow(rentals)

  /** The rentals currently stored, e.g. to check what a ViewModel requested. */
  val rentals: List<Rental>
    get() = stored.value

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

  /**
   * Same rules as the Cloud Function: only a requested rental can be accepted, with a deposit above
   * 0; the other requests of its listing whose dates overlap (buffer day included) are declined.
   */
  override suspend fun acceptRental(
      rentalId: String,
      deposit: Int,
      pickupAddress: String,
      returnAddress: String,
  ): Result<Unit> {
    gate?.await()
    failure?.let {
      return Result.failure(it)
    }
    val rental =
        stored.value.find { it.id == rentalId }
            ?: return Result.failure(IllegalArgumentException("No rental $rentalId"))
    if (rental.status != RentalStatus.REQUESTED) {
      return Result.failure(IllegalStateException("Rental $rentalId is ${rental.status}"))
    }
    if (deposit <= 0) {
      return Result.failure(IllegalArgumentException("The deposit must be above 0"))
    }
    val dates = BookedRange(rental.startDate, rental.endDate)
    stored.update { rentals ->
      rentals.map { other ->
        when {
          other.id == rentalId ->
              other.copy(
                  status = RentalStatus.ACCEPTED,
                  deposit = deposit,
                  pickupAddress = pickupAddress,
                  returnAddress = returnAddress,
              )
          other.listingId == rental.listingId &&
              other.status == RentalStatus.REQUESTED &&
              conflictsWithBuffer(dates, BookedRange(other.startDate, other.endDate)) ->
              other.copy(status = RentalStatus.DECLINED)
          else -> other
        }
      }
    }
    return Result.success(Unit)
  }

  override suspend fun declineRental(rentalId: String): Result<Unit> {
    gate?.await()
    failure?.let {
      return Result.failure(it)
    }
    val rental =
        stored.value.find { it.id == rentalId }
            ?: return Result.failure(IllegalArgumentException("No rental $rentalId"))
    if (rental.status != RentalStatus.REQUESTED) {
      return Result.failure(IllegalStateException("Rental $rentalId is ${rental.status}"))
    }
    stored.update { rentals ->
      rentals.map { if (it.id == rentalId) it.copy(status = RentalStatus.DECLINED) else it }
    }
    return Result.success(Unit)
  }

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
}
