// Made with Claude.

package com.swent.polyloop.model.listing

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListingDocumentTest {

  private val photoUrls = List(3) { "https://example.com/photo$it.jpg" }

  private val validData: Map<String, Any?> =
      mapOf(
          ListingFields.OWNER_ID to "user1",
          ListingFields.TITLE to "Camping tent, 2 people",
          ListingFields.DESCRIPTION to "Light 2-person tent, used three times.",
          ListingFields.CATEGORY to "SPORTS_OUTDOOR",
          ListingFields.PHOTO_URLS to photoUrls,
          ListingFields.PRICE_PER_DAY to 15L,
          ListingFields.ITEM_VALUE to 180L,
          ListingFields.AVAILABLE_FROM to "2026-10-06",
          ListingFields.AVAILABLE_TO to "2026-10-20",
          ListingFields.STATUS to "PUBLISHED",
          ListingFields.PICKUP_AREA to "Ecublens",
          ListingFields.DROP_OFF_AREA to "Ecublens",
      )

  private val expected =
      Listing(
          id = "listing1",
          ownerId = "user1",
          title = "Camping tent, 2 people",
          description = "Light 2-person tent, used three times.",
          category = ListingCategory.SPORTS_OUTDOOR,
          photoUrls = photoUrls,
          pricePerDay = 15,
          itemValue = 180,
          availableFrom = LocalDate.of(2026, 10, 6),
          availableTo = LocalDate.of(2026, 10, 20),
          status = ListingStatus.PUBLISHED,
          pickupArea = "Ecublens",
          dropOffArea = "Ecublens",
      )

  private fun fromDocument(data: Map<String, Any?>) = listingFromDocument("listing1", data)

  @Test fun validDocumentIsMapped() = assertEquals(expected, fromDocument(validData))

  @Test
  fun documentMissingARequiredFieldIsRejected() {
    val required =
        listOf(
            ListingFields.OWNER_ID,
            ListingFields.TITLE,
            ListingFields.DESCRIPTION,
            ListingFields.CATEGORY,
            ListingFields.PHOTO_URLS,
            ListingFields.PRICE_PER_DAY,
            ListingFields.ITEM_VALUE,
            ListingFields.AVAILABLE_FROM,
            ListingFields.AVAILABLE_TO,
            ListingFields.STATUS,
        )
    for (field in required) assertNull("Missing $field", fromDocument(validData - field))
  }

  @Test
  fun unknownCategoryIsRejected() =
      assertNull(fromDocument(validData + (ListingFields.CATEGORY to "CARS")))

  @Test
  fun unknownStatusIsRejected() =
      assertNull(fromDocument(validData + (ListingFields.STATUS to "ARCHIVED")))

  @Test
  fun badDateFormatIsRejected() =
      assertNull(fromDocument(validData + (ListingFields.AVAILABLE_FROM to "06/10/2026")))

  @Test
  fun impossibleDateIsRejected() =
      assertNull(fromDocument(validData + (ListingFields.AVAILABLE_TO to "2026-02-30")))

  @Test
  fun missingAreasDefaultToEmpty() =
      assertEquals(
          expected.copy(pickupArea = "", dropOffArea = ""),
          fromDocument(validData - listOf(ListingFields.PICKUP_AREA, ListingFields.DROP_OFF_AREA)),
      )

  @Test
  fun decimalPriceIsRejected() =
      assertNull(fromDocument(validData + (ListingFields.PRICE_PER_DAY to 12.5)))

  @Test
  fun priceStoredAsTextIsRejected() =
      assertNull(fromDocument(validData + (ListingFields.PRICE_PER_DAY to "15")))

  @Test
  fun nonTextPhotoUrlIsRejected() =
      assertNull(fromDocument(validData + (ListingFields.PHOTO_URLS to listOf("a.jpg", 1L))))

  @Test
  fun priceTooLargeForIntIsRejected() =
      assertNull(fromDocument(validData + (ListingFields.PRICE_PER_DAY to 3_000_000_000L)))
}
