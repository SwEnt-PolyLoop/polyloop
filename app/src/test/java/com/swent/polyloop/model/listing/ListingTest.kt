package com.swent.polyloop.model.listing

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListingTest {

  private fun photos(n: Int) = List(n) { "https://example.com/photo$it.jpg" }

  private val addresses =
      ListingAddresses(
          pickup = "Route Cantonale 1, Ecublens",
          dropOff = "Route Cantonale 1, Ecublens",
      )

  private val valid =
      Listing(
          id = "listing1",
          ownerId = "user1",
          title = "Camping tent, 2 people",
          description = "Light 2-person tent, used three times.",
          category = ListingCategory.SPORTS_OUTDOOR,
          photoUrls = photos(3),
          pricePerDay = 15,
          itemValue = 180,
          availableFrom = LocalDate.of(2026, 10, 6),
          availableTo = LocalDate.of(2026, 10, 20),
          status = ListingStatus.DRAFT,
          pickupArea = "Ecublens",
          dropOffArea = "Ecublens",
      )

  @Test fun validListingCanBePublished() = assertTrue(valid.canPublish(addresses))

  @Test
  fun tooFewPhotosCannotBePublished() =
      assertFalse(valid.copy(photoUrls = photos(2)).canPublish(addresses))

  @Test
  fun maxPhotosCanBePublished() =
      assertTrue(valid.copy(photoUrls = photos(10)).canPublish(addresses))

  @Test
  fun tooManyPhotosCannotBePublished() =
      assertFalse(valid.copy(photoUrls = photos(11)).canPublish(addresses))

  @Test
  fun blankTitleCannotBePublished() = assertFalse(valid.copy(title = "  ").canPublish(addresses))

  @Test
  fun emptyTitleCannotBePublished() = assertFalse(valid.copy(title = "").canPublish(addresses))

  @Test
  fun blankDescriptionCannotBePublished() =
      assertFalse(valid.copy(description = "").canPublish(addresses))

  @Test
  fun zeroPriceCannotBePublished() = assertFalse(valid.copy(pricePerDay = 0).canPublish(addresses))

  @Test
  fun zeroItemValueCannotBePublished() =
      assertFalse(valid.copy(itemValue = 0).canPublish(addresses))

  @Test
  fun endBeforeStartCannotBePublished() =
      assertFalse(valid.copy(availableTo = LocalDate.of(2026, 10, 5)).canPublish(addresses))

  @Test
  fun singleDayAvailabilityCanBePublished() =
      assertTrue(valid.copy(availableTo = valid.availableFrom).canPublish(addresses))

  @Test
  fun blankPickupAddressCannotBePublished() =
      assertFalse(valid.canPublish(addresses.copy(pickup = "")))

  @Test
  fun blankDropOffAddressCannotBePublished() =
      assertFalse(valid.canPublish(addresses.copy(dropOff = "")))

  @Test
  fun whitespaceDescriptionCannotBePublished() =
      assertFalse(valid.copy(description = "   ").canPublish(addresses))

  @Test
  fun whitespacePickupAddressCannotBePublished() =
      assertFalse(valid.canPublish(addresses.copy(pickup = "   ")))

  @Test
  fun whitespaceDropOffAddressCannotBePublished() =
      assertFalse(valid.canPublish(addresses.copy(dropOff = "   ")))

  @Test
  fun missingAreasDoNotBlockPublishing() =
      assertTrue(valid.copy(pickupArea = "", dropOffArea = "").canPublish(addresses))

  @Test
  fun negativePriceCannotBePublished() =
      assertFalse(valid.copy(pricePerDay = -5).canPublish(addresses))

  @Test
  fun negativeItemValueCannotBePublished() =
      assertFalse(valid.copy(itemValue = -1).canPublish(addresses))

  @Test
  fun noPhotosCannotBePublished() =
      assertFalse(valid.copy(photoUrls = emptyList()).canPublish(addresses))
}
