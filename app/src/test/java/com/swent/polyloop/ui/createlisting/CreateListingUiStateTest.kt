// Made with Claude.

package com.swent.polyloop.ui.createlisting

import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingCategory
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateListingUiStateTest {

  private val complete =
      CreateListingUiState(
          title = "Camping tent, 2 people",
          description = "Light 2-person tent, used three times.",
          category = ListingCategory.SPORTS_OUTDOOR,
          pricePerDay = "15",
          itemValue = "180",
          availableFrom = LocalDate.of(2026, 10, 6),
          availableTo = LocalDate.of(2026, 10, 20),
          pickupAddress = "Route Cantonale 1, Ecublens",
          dropOffAddress = "Route Cantonale 1, Ecublens",
          photos = photos(Listing.MIN_PHOTOS),
      )

  private fun photos(count: Int) = List(count) { "content://photo$it" }

  @Test
  fun emptyStateFailsEveryCheck() {
    val state = CreateListingUiState()

    assertTrue(state.isTitleBlank)
    assertTrue(state.isDescriptionBlank)
    assertTrue(state.isCategoryMissing)
    assertTrue(state.isPickupAddressBlank)
    assertTrue(state.isDropOffAddressBlank)
    assertEquals(Listing.MIN_PHOTOS, state.missingPhotos)
    assertTrue(state.canAddPhoto)
    assertFalse(state.isPricePerDayValid)
    assertFalse(state.isItemValueValid)
    assertFalse(state.isAvailabilityValid)
    assertFalse(state.canPublish)
  }

  @Test
  fun completeStatePassesEveryCheck() {
    assertFalse(complete.isTitleBlank)
    assertFalse(complete.isDescriptionBlank)
    assertFalse(complete.isCategoryMissing)
    assertFalse(complete.isPickupAddressBlank)
    assertFalse(complete.isDropOffAddressBlank)
    assertEquals(0, complete.missingPhotos)
    assertTrue(complete.isPricePerDayValid)
    assertTrue(complete.isItemValueValid)
    assertTrue(complete.isAvailabilityValid)
    assertTrue(complete.canPublish)
  }

  @Test
  fun whitespaceOnlyTextIsBlank() {
    val state =
        complete.copy(title = "  ", description = "\n", pickupAddress = " ", dropOffAddress = "\t")

    assertTrue(state.isTitleBlank)
    assertTrue(state.isDescriptionBlank)
    assertTrue(state.isPickupAddressBlank)
    assertTrue(state.isDropOffAddressBlank)
    assertFalse(state.canPublish)
  }

  @Test
  fun eachRequiredFieldBlocksPublish() {
    listOf(
            complete.copy(title = ""),
            complete.copy(description = ""),
            complete.copy(category = null),
            complete.copy(pickupAddress = ""),
            complete.copy(dropOffAddress = ""),
        )
        .forEach { assertFalse(it.toString(), it.canPublish) }
  }

  @Test
  fun missingPhotosCountsDownToZeroAtMinPhotos() {
    assertEquals(Listing.MIN_PHOTOS, complete.copy(photos = emptyList()).missingPhotos)
    assertEquals(1, complete.copy(photos = photos(Listing.MIN_PHOTOS - 1)).missingPhotos)
    assertEquals(0, complete.copy(photos = photos(Listing.MIN_PHOTOS)).missingPhotos)
    assertEquals(0, complete.copy(photos = photos(Listing.MAX_PHOTOS)).missingPhotos)
  }

  @Test
  fun tooFewPhotosBlockPublish() {
    assertFalse(complete.copy(photos = photos(Listing.MIN_PHOTOS - 1)).canPublish)
  }

  @Test
  fun hasValidPhotoCountFromMinToMaxPhotos() {
    assertFalse(complete.copy(photos = photos(Listing.MIN_PHOTOS - 1)).hasValidPhotoCount)
    assertTrue(complete.copy(photos = photos(Listing.MIN_PHOTOS)).hasValidPhotoCount)
    assertTrue(complete.copy(photos = photos(Listing.MAX_PHOTOS)).hasValidPhotoCount)
    assertFalse(complete.copy(photos = photos(Listing.MAX_PHOTOS + 1)).hasValidPhotoCount)
  }

  @Test
  fun canAddPhotoUntilMaxPhotos() {
    assertTrue(complete.copy(photos = photos(Listing.MAX_PHOTOS - 1)).canAddPhoto)
    assertFalse(complete.copy(photos = photos(Listing.MAX_PHOTOS)).canAddPhoto)
  }

  @Test
  fun canPublishWithMaxPhotosButNotMore() {
    assertTrue(complete.copy(photos = photos(Listing.MAX_PHOTOS)).canPublish)
    assertFalse(complete.copy(photos = photos(Listing.MAX_PHOTOS + 1)).canPublish)
  }

  @Test
  fun wholeNumbersAboveZeroAreValidAmounts() {
    listOf("1", "15", "007", Listing.MAX_AMOUNT.toString()).forEach {
      assertTrue(it, complete.copy(pricePerDay = it).isPricePerDayValid)
      assertTrue(it, complete.copy(itemValue = it).isItemValueValid)
    }
  }

  @Test
  fun otherAmountsAreInvalid() {
    val tooBig = (Int.MAX_VALUE.toLong() + 1).toString()
    val aboveMax = (Listing.MAX_AMOUNT + 1).toString()
    val arabicFive = "٥" // a digit for Char.isDigit(), but not 0-9
    listOf("", "0", "000", "-3", "+5", "12.5", "1 000", " 5", "abc", arabicFive, aboveMax, tooBig)
        .forEach {
          val price = complete.copy(pricePerDay = it)
          val value = complete.copy(itemValue = it)
          assertFalse(it, price.isPricePerDayValid)
          assertFalse(it, price.canPublish)
          assertFalse(it, value.isItemValueValid)
          assertFalse(it, value.canPublish)
        }
  }

  @Test
  fun sameStartAndEndDateIsInvalid() {
    val day = LocalDate.of(2026, 10, 6)
    val state = complete.copy(availableFrom = day, availableTo = day)

    assertFalse(state.isAvailabilityValid)
    assertFalse(state.canPublish)
  }

  @Test
  fun endOneDayAfterStartIsValid() {
    val state =
        complete.copy(
            availableFrom = LocalDate.of(2026, 10, 6),
            availableTo = LocalDate.of(2026, 10, 7),
        )

    assertTrue(state.isAvailabilityValid)
    assertTrue(state.canPublish)
  }

  @Test
  fun endBeforeStartIsInvalid() {
    val state =
        complete.copy(
            availableFrom = LocalDate.of(2026, 10, 20),
            availableTo = LocalDate.of(2026, 10, 6),
        )

    assertFalse(state.isAvailabilityValid)
    assertFalse(state.canPublish)
  }

  @Test
  fun missingDateIsInvalid() {
    assertFalse(complete.copy(availableFrom = null).isAvailabilityValid)
    assertFalse(complete.copy(availableTo = null).isAvailabilityValid)
  }

  @Test
  fun cannotPublishWhilePublishing() {
    assertFalse(complete.copy(isPublishing = true).canPublish)
  }
}
