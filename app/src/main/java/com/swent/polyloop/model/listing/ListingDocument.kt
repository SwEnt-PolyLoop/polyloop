// Made with Claude.

package com.swent.polyloop.model.listing

import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Firestore names for listings, kept in one place so they are easy to change. Proposed in #32, not
 * agreed by the team yet.
 */
internal object ListingFields {
  const val COLLECTION = "listings"
  const val OWNER_ID = "ownerId"
  const val TITLE = "title"
  const val DESCRIPTION = "description"
  const val CATEGORY = "category"
  const val PHOTO_URLS = "photoUrls"
  const val PRICE_PER_DAY = "pricePerDay"
  const val ITEM_VALUE = "itemValue"
  const val AVAILABLE_FROM = "availableFrom"
  const val AVAILABLE_TO = "availableTo"
  const val STATUS = "status"
  const val PICKUP_AREA = "pickupArea"
  const val DROP_OFF_AREA = "dropOffArea"
}

/**
 * Builds a [Listing] from a Firestore document, or returns null if a required field is missing or
 * has the wrong type. The areas default to empty because a Cloud Function fills them in later.
 */
internal fun listingFromDocument(id: String, data: Map<String, Any?>): Listing? {
  return Listing(
      id = id,
      ownerId = data[ListingFields.OWNER_ID] as? String ?: return null,
      title = data[ListingFields.TITLE] as? String ?: return null,
      description = data[ListingFields.DESCRIPTION] as? String ?: return null,
      category = enumOrNull<ListingCategory>(data[ListingFields.CATEGORY]) ?: return null,
      photoUrls = stringListOrNull(data[ListingFields.PHOTO_URLS]) ?: return null,
      pricePerDay = intOrNull(data[ListingFields.PRICE_PER_DAY]) ?: return null,
      itemValue = intOrNull(data[ListingFields.ITEM_VALUE]) ?: return null,
      availableFrom = dateOrNull(data[ListingFields.AVAILABLE_FROM]) ?: return null,
      availableTo = dateOrNull(data[ListingFields.AVAILABLE_TO]) ?: return null,
      status = enumOrNull<ListingStatus>(data[ListingFields.STATUS]) ?: return null,
      pickupArea = data[ListingFields.PICKUP_AREA] as? String ?: "",
      dropOffArea = data[ListingFields.DROP_OFF_AREA] as? String ?: "",
  )
}

private inline fun <reified T : Enum<T>> enumOrNull(value: Any?): T? =
    enumValues<T>().firstOrNull { it.name == value }

/** Firestore returns whole numbers as Long; anything else (e.g. 12.5) is rejected. */
private fun intOrNull(value: Any?): Int? =
    (value as? Long)?.takeIf { it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() }?.toInt()

private fun stringListOrNull(value: Any?): List<String>? {
  val list = value as? List<*> ?: return null
  return list.filterIsInstance<String>().takeIf { it.size == list.size }
}

private fun dateOrNull(value: Any?): LocalDate? =
    try {
      (value as? String)?.let(LocalDate::parse)
    } catch (e: DateTimeParseException) {
      null
    }
