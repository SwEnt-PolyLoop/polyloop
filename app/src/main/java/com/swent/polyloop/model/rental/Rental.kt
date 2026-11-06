// Made with Claude.

package com.swent.polyloop.model.rental

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * A request to borrow a listing for some days, and everything that happens to it afterwards. Stored
 * in Firestore at `rentals/{id}` (see "Firestore schema" in docs/ARCHITECTURE.md).
 *
 * The listing title, both names and the public areas are copied when the rental is created, so My
 * rentals and the chat list can show a rental without reading the listing or the profiles.
 */
data class Rental(
    val id: String,
    val listingId: String,
    val listingTitle: String,
    val listingPhotoUrl: String = "", // the listing's first photo, copied from the listing
    val ownerId: String,
    val ownerName:
        String, // "" when the owner's profile has no name; the screen shows a placeholder
    val borrowerId: String,
    val borrowerName: String,
    val startDate: LocalDate,
    val endDate: LocalDate, // the return date; both days are part of the rental
    val status: RentalStatus,
    val pricePerDay: Int, // in PP
    val totalPrice: Int, // in PP: pricePerDay times the number of days
    val pickupArea: String, // public, copied from the listing
    val dropOffArea: String, // public, copied from the listing
    val pickupAddress: String = "", // exact, set when the rental is accepted
    val returnAddress: String = "", // exact, set when the rental is accepted
    val deposit: Int = 0, // in PP: the accepted amount, 0 until then
    val offer: DepositOffer? = null, // the offer waiting for an answer, if any
    val createdAt: Instant,
    val lastMessage: String = "",
    // Starts at createdAt: the chat list sorts by this field, and Firestore leaves out of a sorted
    // query every document that lacks it.
    val lastMessageAt: Instant = createdAt,
) {
  /** The two users who take part in the rental; the chat list queries by this field. */
  val participants: List<String>
    get() = listOf(ownerId, borrowerId)

  /**
   * The name of the other person in the rental, as seen by the user [uid] (the borrower's name for
   * the owner, the owner's name for anyone else). Can be "": the screen then shows a placeholder.
   */
  fun otherPersonName(uid: String): String = if (uid == ownerId) borrowerName else ownerName

  companion object {
    /**
     * The price of renting at [pricePerDay] from [startDate] to [endDate], both days included.
     * Requires [endDate] not to be before [startDate].
     */
    fun totalPrice(pricePerDay: Int, startDate: LocalDate, endDate: LocalDate): Int {
      require(!endDate.isBefore(startDate)) { "endDate $endDate is before startDate $startDate" }
      return pricePerDay * (ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1)
    }
  }
}

/**
 * A deposit offer waiting for an answer. Only Cloud Functions write it.
 *
 * @property amount the deposit offered, in PP.
 * @property authorId the user who made it; the other participant answers it.
 * @property isCounter true for the borrower's counteroffer, which cannot be countered.
 * @property previousAmount on a counteroffer, the amount the lender offered; null otherwise.
 * @property expiresAt when it stops being valid.
 */
data class DepositOffer(
    val amount: Int,
    val authorId: String,
    val isCounter: Boolean,
    val previousAmount: Int? = null,
    val expiresAt: Instant,
)

/** Where a rental stands; see "Rental statuses" in docs/ARCHITECTURE.md. */
enum class RentalStatus {
  REQUESTED,
  ACCEPTED,
  ACTIVE,
  RETURNED,
  CLOSED,
  DECLINED,
  CANCELLED,
  OVERDUE,
  LOST,
  DISPUTED,
  FROZEN,
}
