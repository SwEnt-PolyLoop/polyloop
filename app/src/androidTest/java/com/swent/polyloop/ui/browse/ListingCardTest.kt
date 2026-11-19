// Made with Claude.

package com.swent.polyloop.ui.browse

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
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
import com.swent.polyloop.utils.isCloseTo
import com.swent.polyloop.utils.waitUntilCenterIs
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    composeTestRule.setContent { ListingCard(listing = listing, distanceMeters = 600.0) }

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
        .assertTextEquals("600 m · Ecublens")
  }

  @Test
  fun blankPickupAreaShowsOnlyDistance() {
    composeTestRule.setContent {
      ListingCard(listing = listing.copy(pickupArea = ""), distanceMeters = 600.0)
    }

    composeTestRule
        .onNodeWithTag(C.Tag.listing_card_location, useUnmergedTree = true)
        .assertTextEquals("600 m")
  }

  @Test
  fun kilometersFollowTheLanguageWithoutRecreatingTheCard() {
    var locale by mutableStateOf(Locale.FRANCE)
    composeTestRule.setContent {
      val configuration = Configuration(LocalConfiguration.current).apply { setLocale(locale) }
      CompositionLocalProvider(LocalConfiguration provides configuration) {
        ListingCard(listing = listing, distanceMeters = 1200.0)
      }
    }
    val location =
        composeTestRule.onNodeWithTag(C.Tag.listing_card_location, useUnmergedTree = true)
    location.assertTextEquals("1,2 km · Ecublens")

    composeTestRule.runOnIdle { locale = Locale.US }

    location.assertTextEquals("1.2 km · Ecublens")
  }

  @Test
  fun withoutADistanceTheLocationShowsOnlyThePickupArea() {
    composeTestRule.setContent { ListingCard(listing = listing, distanceMeters = null) }

    composeTestRule
        .onNodeWithTag(C.Tag.listing_card_location, useUnmergedTree = true)
        .assertTextEquals("Ecublens")
  }

  @Test
  fun withoutADistanceAndAreaNoLocationLineShows() {
    composeTestRule.setContent {
      ListingCard(listing = listing.copy(pickupArea = ""), distanceMeters = null)
    }

    composeTestRule
        .onNodeWithTag(C.Tag.listing_card_location, useUnmergedTree = true)
        .assertDoesNotExist()
  }

  @Test
  fun singleLineCardKeepsItsHeightWithoutADistanceAndArea() {
    // Cards under the map must all have the same height, or the map jumps while swiping.
    composeTestRule.setContent {
      Column {
        ListingCard(
            listing = listing.copy(id = "full"),
            distanceMeters = 350.0,
            singleLineText = true,
        )
        ListingCard(
            listing = listing.copy(id = "empty", pickupArea = ""),
            distanceMeters = null,
            singleLineText = true,
        )
      }
    }

    val heights =
        listOf("full", "empty").map {
          composeTestRule.onNodeWithTag(C.Tag.listing_card_ + it).fetchSemanticsNode().size.height
        }
    assertEquals(heights[0], heights[1])
  }

  @Test
  fun clickingCardCallsOnClick() {
    var clicks = 0
    composeTestRule.setContent {
      ListingCard(listing = listing, distanceMeters = 600.0, onClick = { clicks++ })
    }

    composeTestRule.onNodeWithTag(C.Tag.listing_card_ + listing.id).performClick()

    assertEquals(1, clicks)
  }

  /** Shows the card for [listing] with these [photoUrls], inside the app theme. */
  private fun setCardWithPhotos(photoUrls: List<String>) {
    composeTestRule.setContent {
      PolyLoopTheme {
        grey = MaterialTheme.colorScheme.surfaceVariant
        ListingCard(listing = listing.copy(photoUrls = photoUrls), distanceMeters = 600.0)
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
    assertFalse(photo().colorAt(0, 0).isCloseTo(Color.Red))
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
