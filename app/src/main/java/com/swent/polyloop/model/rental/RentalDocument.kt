// Made with Claude.

package com.swent.polyloop.model.rental

import com.swent.polyloop.model.firestore.dateOrNull
import com.swent.polyloop.model.firestore.enumOrNull
import com.swent.polyloop.model.firestore.instantOrNull
import com.swent.polyloop.model.firestore.intOrNull

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
  const val DEPOSIT = "deposit"
  const val LATE_FEES = "lateFees"
  const val OFFER = "offer"
  const val OFFER_AMOUNT = "amount"
  const val OFFER_AUTHOR_ID = "authorId"
  const val OFFER_IS_COUNTER = "isCounter"
  const val OFFER_PREVIOUS_AMOUNT = "previousAmount"
  const val OFFER_EXPIRES_AT = "expiresAt"
  const val CREATED_AT = "createdAt"
  const val LAST_MESSAGE = "lastMessage"
  const val LAST_MESSAGE_AT = "lastMessageAt"
}

/**
 * Builds a [Rental] from a Firestore document, or returns null if a field it cannot do without
 * (ids, dates, status, prices, `createdAt`) is missing or has the wrong type. Copied names, title,
 * photo, areas, addresses and the last message read as "" when missing, `lastMessageAt` as
 * `createdAt`, `deposit` and `lateFees` as 0, and a missing or malformed `offer` as no offer.
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
      deposit = intOrNull(data[RentalFields.DEPOSIT]) ?: 0,
      // Only Cloud Functions will write lateFees, the scheduled job that charges late fees, once a
      // return is late, so a rental that was never late has no such field.
      lateFees = intOrNull(data[RentalFields.LATE_FEES]) ?: 0,
      offer = depositOfferFromDocument(data[RentalFields.OFFER]),
      createdAt = createdAt,
      lastMessage = data.stringOrEmpty(RentalFields.LAST_MESSAGE),
      lastMessageAt = instantOrNull(data[RentalFields.LAST_MESSAGE_AT]) ?: createdAt,
  )
}

/**
 * Builds a [DepositOffer] from the `offer` field, or returns null if it is missing or malformed.
 */
private fun depositOfferFromDocument(value: Any?): DepositOffer? {
  val offer = value as? Map<*, *> ?: return null
  return DepositOffer(
      amount = intOrNull(offer[RentalFields.OFFER_AMOUNT]) ?: return null,
      authorId = offer[RentalFields.OFFER_AUTHOR_ID] as? String ?: return null,
      isCounter = offer[RentalFields.OFFER_IS_COUNTER] as? Boolean ?: return null,
      previousAmount = intOrNull(offer[RentalFields.OFFER_PREVIOUS_AMOUNT]),
      expiresAt = instantOrNull(offer[RentalFields.OFFER_EXPIRES_AT]) ?: return null,
  )
}

private fun Map<String, Any?>.stringOrEmpty(field: String): String = this[field] as? String ?: ""
