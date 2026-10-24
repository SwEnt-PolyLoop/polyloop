// Made with Claude.

package com.swent.polyloop.model.rental

import com.google.firebase.firestore.FieldValue
import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.firestore.dateOrNull
import com.swent.polyloop.model.firestore.enumOrNull
import com.swent.polyloop.model.firestore.instantOrNull
import com.swent.polyloop.model.firestore.intOrNull
import com.swent.polyloop.model.listing.Listing
import java.time.LocalDate

/** Firestore names for rentals (see "Firestore schema" in docs/ARCHITECTURE.md). */
internal object RentalFields {
  const val COLLECTION = "rentals"
  const val LISTING_ID = "listingId"
  const val LISTING_TITLE = "listingTitle"
  const val LISTING_PHOTO_URL = "listingPhotoUrl"
  const val OWNER_ID = "ownerId"
  const val OWNER_NAME = "ownerName"
  const val BORROWER_ID = "borrowerId"
  const val BORROWER_NAME = "borrowerName"
  const val PARTICIPANTS = "participants"
  const val START_DATE = "startDate"
  const val END_DATE = "endDate"
  const val STATUS = "status"
  const val PRICE_PER_DAY = "pricePerDay"
  const val TOTAL_PRICE = "totalPrice"
  const val PICKUP_AREA = "pickupArea"
  const val DROP_OFF_AREA = "dropOffArea"
  const val PICKUP_ADDRESS = "pickupAddress"
  const val RETURN_ADDRESS = "returnAddress"
  const val CREATED_AT = "createdAt"
  const val LAST_MESSAGE = "lastMessage"
  const val LAST_MESSAGE_AT = "lastMessageAt"
}

/**
 * The document of a new request from [borrower] to rent [listing] from [startDate] to [endDate].
 * `createdAt` and `lastMessageAt` are the same server timestamp, so every rental shows in the chat
 * list, which sorts by `lastMessageAt`. A blank [ownerName] is stored as "". Requires [endDate] not
 * to be before [startDate].
 */
internal fun rentalRequestDocument(
    listing: Listing,
    ownerName: String,
    borrower: AuthUser,
    startDate: LocalDate,
    endDate: LocalDate,
): Map<String, Any> {
  val now = FieldValue.serverTimestamp()
  return mapOf(
      RentalFields.LISTING_ID to listing.id,
      RentalFields.LISTING_TITLE to listing.title,
      RentalFields.LISTING_PHOTO_URL to listing.photoUrls.firstOrNull().orEmpty(),
      RentalFields.OWNER_ID to listing.ownerId,
      RentalFields.OWNER_NAME to ownerName.trim(),
      RentalFields.BORROWER_ID to borrower.uid,
      RentalFields.BORROWER_NAME to borrower.name,
      RentalFields.PARTICIPANTS to listOf(listing.ownerId, borrower.uid),
      RentalFields.START_DATE to startDate.toString(),
      RentalFields.END_DATE to endDate.toString(),
      RentalFields.STATUS to RentalStatus.REQUESTED.name,
      RentalFields.PRICE_PER_DAY to listing.pricePerDay,
      RentalFields.TOTAL_PRICE to Rental.totalPrice(listing.pricePerDay, startDate, endDate),
      RentalFields.PICKUP_AREA to listing.pickupArea,
      RentalFields.DROP_OFF_AREA to listing.dropOffArea,
      RentalFields.PICKUP_ADDRESS to "",
      RentalFields.RETURN_ADDRESS to "",
      RentalFields.CREATED_AT to now,
      RentalFields.LAST_MESSAGE to "",
      RentalFields.LAST_MESSAGE_AT to now,
  )
}

/**
 * Builds a [Rental] from a Firestore document, or returns null if a field it cannot do without
 * (ids, dates, status, prices, `createdAt`) is missing or has the wrong type. Copied names, title,
 * photo, areas, addresses and the last message read as "" when missing, and `lastMessageAt` as
 * `createdAt`.
 */
internal fun rentalFromDocument(id: String, data: Map<String, Any?>): Rental? {
  val createdAt = instantOrNull(data[RentalFields.CREATED_AT]) ?: return null
  return Rental(
      id = id,
      listingId = data[RentalFields.LISTING_ID] as? String ?: return null,
      listingTitle = data.stringOrEmpty(RentalFields.LISTING_TITLE),
      listingPhotoUrl = data.stringOrEmpty(RentalFields.LISTING_PHOTO_URL),
      ownerId = data[RentalFields.OWNER_ID] as? String ?: return null,
      ownerName = data.stringOrEmpty(RentalFields.OWNER_NAME),
      borrowerId = data[RentalFields.BORROWER_ID] as? String ?: return null,
      borrowerName = data.stringOrEmpty(RentalFields.BORROWER_NAME),
      startDate = dateOrNull(data[RentalFields.START_DATE]) ?: return null,
      endDate = dateOrNull(data[RentalFields.END_DATE]) ?: return null,
      status = enumOrNull<RentalStatus>(data[RentalFields.STATUS]) ?: return null,
      pricePerDay = intOrNull(data[RentalFields.PRICE_PER_DAY]) ?: return null,
      totalPrice = intOrNull(data[RentalFields.TOTAL_PRICE]) ?: return null,
      pickupArea = data.stringOrEmpty(RentalFields.PICKUP_AREA),
      dropOffArea = data.stringOrEmpty(RentalFields.DROP_OFF_AREA),
      pickupAddress = data.stringOrEmpty(RentalFields.PICKUP_ADDRESS),
      returnAddress = data.stringOrEmpty(RentalFields.RETURN_ADDRESS),
      createdAt = createdAt,
      lastMessage = data.stringOrEmpty(RentalFields.LAST_MESSAGE),
      lastMessageAt = instantOrNull(data[RentalFields.LAST_MESSAGE_AT]) ?: createdAt,
  )
}

private fun Map<String, Any?>.stringOrEmpty(field: String): String = this[field] as? String ?: ""
