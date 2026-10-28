// Made with Claude.

package com.swent.polyloop.model.rental

import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.listing.Listing
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

  override suspend fun acceptRental(
      id: String,
      pickupAddress: String,
      returnAddress: String,
  ): Result<Rental> =
      answer(id) { rental ->
        rental.copy(
            status = RentalStatus.ACCEPTED,
            pickupAddress = pickupAddress.trim(),
            returnAddress = returnAddress.trim(),
        )
      }

  override suspend fun declineRental(id: String): Result<Rental> =
      answer(id) { rental -> rental.copy(status = RentalStatus.DECLINED) }

  /**
   * Replaces the [RentalStatus.REQUESTED] rental [id] by [change] of it. Fails like the other calls
   * when [failure] is set, or when the rental is missing or no longer requested.
   */
  private suspend fun answer(id: String, change: (Rental) -> Rental): Result<Rental> {
    gate?.await()
    failure?.let {
      return Result.failure(it)
    }
    val rental =
        stored.value.find { it.id == id }
            ?: return Result.failure(NoSuchElementException("No rental $id"))
    if (rental.status != RentalStatus.REQUESTED) {
      return Result.failure(IllegalStateException("Rental $id is ${rental.status}, not REQUESTED"))
    }
    val answered = change(rental)
    stored.update { rentals -> rentals.map { if (it.id == id) answered else it } }
    return Result.success(answered)
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
