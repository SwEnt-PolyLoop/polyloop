// Made with Claude.

package com.swent.polyloop.model.listing

import com.swent.polyloop.model.location.MapPoint
import java.time.LocalDate

data class Listing(
    val id: String,
    val ownerId: String,
    val title: String,
    val description: String,
    val category: ListingCategory,
    val photoUrls: List<String>,
    val pricePerDay: Int, // in PP
    val itemValue: Int, // in PP, shown as the suggested deposit
    val availableFrom: LocalDate,
    val availableTo: LocalDate,
    val status: ListingStatus,
    val pickupArea: String = "", // public, set by a Cloud Function when the listing is saved
    val dropOffArea: String = "", // public, set by a Cloud Function when the listing is saved
    // Public and rounded to about 1 km by a Cloud Function. Null until the address is geocoded, and
    // for good if it is blank, not found or keeps failing to geocode, so null does not always mean
    // "in progress". Right after an address edit, it is still the old address's point until the
    // Cloud Function has run.
    val pickupPoint: MapPoint? = null,
    // Accepted rentals, written only by Cloud Functions; the buffer days are not included
    val bookedRanges: List<BookedRange> = emptyList(),
) {
  fun canPublish(addresses: ListingAddresses): Boolean =
      title.isNotBlank() &&
          description.isNotBlank() &&
          photoUrls.size in MIN_PHOTOS..MAX_PHOTOS &&
          pricePerDay in 1..MAX_AMOUNT &&
          itemValue in 1..MAX_AMOUNT &&
          availableFrom.isBefore(availableTo) &&
          addresses.pickup.isNotBlank() &&
          addresses.dropOff.isNotBlank()

  companion object {
    const val MIN_PHOTOS = 3
    const val MAX_PHOTOS = 10

    /**
     * Highest price per day and item value, in PP, so totals over a year-long window fit in an Int.
     */
    const val MAX_AMOUNT = 99_999
  }
}

enum class ListingStatus {
  DRAFT,
  PUBLISHED,
}

enum class ListingCategory(val label: String) {
  ELECTRONICS("Electronics"),
  SPORTS_OUTDOOR("Sports & outdoor"),
  BOOKS_COURSE_MATERIAL("Books & course material"),
  TOOLS_DIY("Tools & DIY"),
  KITCHEN_HOME("Kitchen & home"),
  MUSIC("Music"),
  OTHER("Other"),
}

/** The days taken by one accepted rental, from [start] to [end], both days included. */
data class BookedRange(val start: LocalDate, val end: LocalDate)

/** Every day of [ranges], both ends included: the days the picker shows as booked. */
fun bookedDays(ranges: List<BookedRange>): Set<LocalDate> =
    ranges
        .flatMap { range ->
          generateSequence(range.start) { it.plusDays(1) }.takeWhile { it <= range.end }.toList()
        }
        .toSet()

/**
 * The days right before and right after each booked stretch, unless they are booked too. The day
 * before could only end a range with no free day after it, so it is blocked like the day after.
 * Shared by the date picker and the rule that declines overlapping requests.
 */
fun bufferDays(disabledDates: Set<LocalDate>): Set<LocalDate> =
    disabledDates
        .flatMap { listOf(it.minusDays(1), it.plusDays(1)) }
        .filterNot { it in disabledDates }
        .toSet()

/**
 * True if the stays [a] and [b] cannot both be booked: a day of [a] is a day of [b] or one of its
 * buffer days. Two stays with one free day between them do not conflict; two stays that touch
 * through a buffer day do. Same rule as the Cloud Function `acceptRental`
 * (functions/src/rentalRules.ts).
 */
fun conflictsWithBuffer(a: BookedRange, b: BookedRange): Boolean {
  val stayB = bookedDays(listOf(b))
  val blockedByB = stayB + bufferDays(stayB)
  return bookedDays(listOf(a)).any { it in blockedByB }
}

/** Exact addresses, kept out of the public listing document. */
data class ListingAddresses(val pickup: String, val dropOff: String)
