package com.swent.polyloop.model.listing

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
) {
  fun canPublish(addresses: ListingAddresses): Boolean =
      title.isNotBlank() &&
          description.isNotBlank() &&
          photoUrls.size in MIN_PHOTOS..MAX_PHOTOS &&
          pricePerDay > 0 &&
          itemValue > 0 &&
          !availableFrom.isAfter(availableTo) &&
          addresses.pickup.isNotBlank() &&
          addresses.dropOff.isNotBlank()

  companion object {
    const val MIN_PHOTOS = 3
    const val MAX_PHOTOS = 10
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

/** Exact addresses, kept out of the public listing document. */
data class ListingAddresses(val pickup: String, val dropOff: String)
