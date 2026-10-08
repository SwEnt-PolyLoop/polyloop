// Made with Claude.

package com.swent.polyloop.ui.browse

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.model.listing.ListingStatus
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.theme.PolyLoopTheme
import com.swent.polyloop.utils.FakePhotoServerRule
import com.swent.polyloop.utils.FakePhotos
import com.swent.polyloop.utils.colorAt
import com.swent.polyloop.utils.waitUntilCenterIs
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListingCardTest {

  @get:Rule val composeTestRule = createComposeRule()

  @get:Rule val fakePhotoServer = FakePhotoServerRule()

  /** The theme's grey, read while composing so it matches what the photo draws. */
  private var grey = Color.Unspecified

  private val listing =
      Listing(
          id = "tent",
          ownerId = "owner",
          title = "Camping tent, 2 people",
          description = "A tent",
          category = ListingCategory.SPORTS_OUTDOOR,
          photoUrls = emptyList(),
          pricePerDay = 15,
          itemValue = 200,
          availableFrom = LocalDate.of(2026, 10, 1),
          availableTo = LocalDate.of(2026, 12, 31),
          status = ListingStatus.PUBLISHED,
          pickupArea = "Ecublens",
      )

  @Test
  fun displaysTitlePriceAndLocation() {
    composeTestRule.setContent { ListingCard(listing = listing, distanceText = "0.6 km") }

    composeTestRule.onNodeWithTag(C.Tag.listing_card_ + listing.id).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(C.Tag.listing_card_title, useUnmergedTree = true)
        .assertIsDisplayed()
        .assertTextEquals("Camping tent, 2 people")
    composeTestRule
        .onNodeWithTag(C.Tag.listing_card_price, useUnmergedTree = true)
        .assertIsDisplayed()
        .assertTextEquals("15 PP / day")
    composeTestRule
        .onNodeWithTag(C.Tag.listing_card_location, useUnmergedTree = true)
        .assertIsDisplayed()
        .assertTextEquals("0.6 km · Ecublens")
  }

  @Test
  fun blankPickupAreaShowsOnlyDistance() {
    composeTestRule.setContent {
      ListingCard(listing = listing.copy(pickupArea = ""), distanceText = "0.6 km")
    }

    composeTestRule
        .onNodeWithTag(C.Tag.listing_card_location, useUnmergedTree = true)
        .assertTextEquals("0.6 km")
  }

  @Test
  fun clickingCardCallsOnClick() {
    var clicks = 0
    composeTestRule.setContent {
      ListingCard(listing = listing, distanceText = "0.6 km", onClick = { clicks++ })
    }

    composeTestRule.onNodeWithTag(C.Tag.listing_card_ + listing.id).performClick()

    assertEquals(1, clicks)
  }

  /** Shows the card for [listing] with these [photoUrls], inside the app theme. */
  private fun setCardWithPhotos(photoUrls: List<String>) {
    composeTestRule.setContent {
      PolyLoopTheme {
        grey = MaterialTheme.colorScheme.surfaceVariant
        ListingCard(listing = listing.copy(photoUrls = photoUrls), distanceText = "0.6 km")
      }
    }
  }

  // The card merges its children for TalkBack, so its parts are found in the unmerged tree.
  private fun photo() =
      composeTestRule.onNodeWithTag(C.Tag.listing_card_photo, useUnmergedTree = true)

  @Test
  fun showsTheFirstPhotoWhenThereAreSeveral() {
    setCardWithPhotos(listOf(FakePhotos.RED, FakePhotos.BLUE))

    composeTestRule.waitUntilCenterIs(Color.Red, ::photo)
  }

  @Test
  fun showsOnlyTheFirstPhotoEvenWhenItCannotLoad() {
    setCardWithPhotos(listOf(FakePhotos.BROKEN, FakePhotos.RED))

    composeTestRule.waitUntilCenterIs(grey, ::photo)
  }

  @Test
  fun noPhotosShowsGrey() {
    setCardWithPhotos(emptyList())

    composeTestRule.waitUntilCenterIs(grey, ::photo)
  }

  @Test
  fun photoIsAn80dpSquare() {
    setCardWithPhotos(listOf(FakePhotos.LANDSCAPE))
    composeTestRule.waitUntilCenterIs(Color.Red, ::photo)

    photo().assertWidthIsEqualTo(80.dp).assertHeightIsEqualTo(80.dp)
  }

  @Test
  fun photoHasRoundedCorners() {
    setCardWithPhotos(listOf(FakePhotos.RED))
    composeTestRule.waitUntilCenterIs(Color.Red, ::photo)

    // The very corner is cut off by the rounding, so the card shows through there.
    assertNotEquals(Color.Red.toArgb(), photo().colorAt(0, 0))
  }

  @Test
  fun photoIsLeftOfTheTitle() {
    setCardWithPhotos(listOf(FakePhotos.RED))

    val photoRight = photo().getUnclippedBoundsInRoot().right
    val titleLeft =
        composeTestRule
            .onNodeWithTag(C.Tag.listing_card_title, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
            .left
    assertTrue("photo ends at $photoRight, title starts at $titleLeft", photoRight <= titleLeft)
  }

  @Test
  fun photoHasNoContentDescriptionSoTheTitleIsNotReadTwice() {
    setCardWithPhotos(listOf(FakePhotos.RED))

    photo().assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
  }
}
