// Made with Claude.

package com.swent.polyloop.ui.listingdetail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swent.polyloop.R
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.model.listing.ListingStatus
import com.swent.polyloop.resources.C
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListingDetailScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  private val listing =
      Listing(
          id = "tent",
          ownerId = "owner",
          title = "Camping tent, 2 people",
          description = "Light 2-person tent, used three times.",
          category = ListingCategory.SPORTS_OUTDOOR,
          photoUrls = List(5) { "https://example.com/photo$it.jpg" },
          pricePerDay = 15,
          itemValue = 180,
          availableFrom = LocalDate.of(2026, 10, 6),
          availableTo = LocalDate.of(2026, 10, 20),
          status = ListingStatus.PUBLISHED,
          pickupArea = "Ecublens",
      )

  private var requestClicks = 0
  private var lenderClicks = 0

  private fun setContent(listing: Listing = this.listing) {
    composeTestRule.setContent {
      ListingDetailScreen(
          listing = listing,
          onRequestDates = { requestClicks++ },
          onLenderClick = { lenderClicks++ },
      )
    }
  }

  private fun assertSectionText(tag: String, text: String) {
    composeTestRule
        .onNodeWithTag(tag, useUnmergedTree = true)
        .performScrollTo()
        .assertIsDisplayed()
        .assertTextEquals(text)
  }

  @Test
  fun displaysTitlePriceAndDescription() {
    setContent()

    composeTestRule.onNodeWithTag(C.Tag.listing_detail_screen).assertIsDisplayed()
    assertSectionText(C.Tag.listing_detail_title, "Camping tent, 2 people")
    assertSectionText(
        C.Tag.listing_detail_price,
        context.getString(R.string.listing_card_price, listing.pricePerDay) +
            " " +
            context.getString(R.string.listing_card_per_day),
    )
    assertSectionText(C.Tag.listing_detail_description, "Light 2-person tent, used three times.")
  }

  @Test
  fun badgeShowsFirstPhotoOfTotal() {
    setContent()

    assertSectionText(
        C.Tag.listing_detail_photo_badge,
        context.getString(R.string.listing_detail_photo_count, 1, listing.photoUrls.size),
    )
  }

  @Test
  fun swipingPhotosUpdatesBadge() {
    setContent()

    composeTestRule.onNodeWithTag(C.Tag.listing_detail_photos).performTouchInput { swipeLeft() }

    assertSectionText(
        C.Tag.listing_detail_photo_badge,
        context.getString(R.string.listing_detail_photo_count, 2, listing.photoUrls.size),
    )
  }

  @Test
  fun noPhotosHidesBadge() {
    setContent(listing.copy(photoUrls = emptyList()))

    composeTestRule.onNodeWithTag(C.Tag.listing_detail_photos).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.listing_detail_photo_badge).assertDoesNotExist()
  }

  @Test
  fun displaysLenderPlaceholderAndRating() {
    setContent()

    assertSectionText(
        C.Tag.listing_detail_lender_name,
        context.getString(R.string.listing_detail_lender_placeholder),
    )
    assertSectionText(
        C.Tag.listing_detail_lender_rating,
        context.resources.getQuantityString(
            R.plurals.listing_detail_rating,
            FAKE_LENDER_REVIEW_COUNT,
            FAKE_LENDER_RATING,
            FAKE_LENDER_REVIEW_COUNT,
        ),
    )
  }

  @Test
  fun displaysDepositAvailabilityAndNote() {
    setContent()

    val (from, to) =
        formatRangeEnds(
            listing.availableFrom,
            listing.availableTo,
            context.resources.configuration.locales[0],
        )
    assertSectionText(
        C.Tag.listing_detail_deposit,
        context.getString(R.string.listing_card_price, listing.itemValue),
    )
    assertSectionText(
        C.Tag.listing_detail_availability,
        context.getString(R.string.listing_detail_date_range, from, to),
    )
    assertSectionText(
        C.Tag.listing_detail_deposit_note,
        context.getString(R.string.listing_detail_deposit_note),
    )
  }

  @Test
  fun displaysPickupAreaCaption() {
    setContent()

    composeTestRule
        .onNodeWithTag(C.Tag.listing_detail_pickup_area)
        .performScrollTo()
        .assertIsDisplayed()
    assertSectionText(
        C.Tag.listing_detail_pickup_caption,
        context.getString(R.string.listing_detail_pickup_caption),
    )
  }

  @Test
  fun requestButtonIsPinnedAndCallsOnRequestDates() {
    setContent()

    composeTestRule
        .onNodeWithTag(C.Tag.listing_detail_request_button)
        .assertIsDisplayed()
        .assertTextEquals(context.getString(R.string.listing_detail_request_dates))
        .performClick()

    assertEquals(1, requestClicks)
    assertEquals(0, lenderClicks)
  }

  @Test
  fun clickingLenderCardCallsOnLenderClick() {
    setContent()

    composeTestRule.onNodeWithTag(C.Tag.listing_detail_lender_card).performScrollTo().performClick()

    assertEquals(1, lenderClicks)
    assertEquals(0, requestClicks)
  }

  @Test
  fun requestButtonStaysPinnedAfterScrollingToBottom() {
    setContent()

    composeTestRule.onNodeWithTag(C.Tag.listing_detail_pickup_area).performScrollTo()

    composeTestRule.onNodeWithTag(C.Tag.listing_detail_request_button).assertIsDisplayed()
  }

  @Test
  fun lenderAvatarShowsFirstLetterOfName() {
    setContent()

    val name = context.getString(R.string.listing_detail_lender_placeholder)
    assertSectionText(C.Tag.listing_detail_lender_avatar, name.first().uppercase())
  }
}
