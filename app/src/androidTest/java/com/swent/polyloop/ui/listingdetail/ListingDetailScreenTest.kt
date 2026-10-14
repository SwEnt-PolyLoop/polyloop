// Made with Claude.

package com.swent.polyloop.ui.listingdetail

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
import com.swent.polyloop.model.listing.ListingRepository
import com.swent.polyloop.model.listing.ListingStatus
import com.swent.polyloop.resources.C
import com.swent.polyloop.utils.FakePhotoServerRule
import com.swent.polyloop.utils.FakePhotos
import com.swent.polyloop.utils.waitUntilCenterIs
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListingDetailScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  // Also keeps the example.com photos below off the network: they just fail to load.
  @get:Rule val fakePhotoServer = FakePhotoServerRule()

  /** The theme's grey, read while composing so it matches what the photos draw. */
  private var grey = Color.Unspecified

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

  private val today = LocalDate.of(2026, 10, 1)

  private fun oct(day: Int) = LocalDate.of(2026, 10, day)

  private var requestClicks = 0
  private var lenderClicks = 0
  private val confirmedDates = mutableListOf<Pair<LocalDate, LocalDate>>()

  private fun setContent(listing: Listing = this.listing) {
    composeTestRule.setContent {
      grey = MaterialTheme.colorScheme.surfaceVariant
      ListingDetailContent(
          uiState = ListingDetailUiState(listing = listing),
          onRequestDates = { requestClicks++ },
          onConfirmDates = { start, end -> confirmedDates += start to end },
          onDismissDatePicker = {},
          onLenderClick = { lenderClicks++ },
          onRetry = {},
          today = today,
      )
    }
  }

  private fun setContent(uiState: ListingDetailUiState) {
    composeTestRule.setContent {
      ListingDetailContent(
          uiState = uiState,
          onRequestDates = {},
          onConfirmDates = { start, end -> confirmedDates += start to end },
          onDismissDatePicker = {},
          onLenderClick = {},
          onRetry = {},
          today = today,
      )
    }
  }

  private fun tapDays(vararg dates: LocalDate) = dates.forEach {
    composeTestRule.onNodeWithTag(C.Tag.datePickerDay(it)).performClick()
  }

  private fun waitForTag(tag: String) {
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
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

  private fun photoPage(index: Int) =
      composeTestRule.onNodeWithTag(C.Tag.listing_detail_photo_ + index, useUnmergedTree = true)

  private fun swipeToNextPhoto() =
      composeTestRule.onNodeWithTag(C.Tag.listing_detail_photos).performTouchInput { swipeLeft() }

  private fun assertBadge(current: Int, total: Int) =
      assertSectionText(
          C.Tag.listing_detail_photo_badge,
          context.getString(R.string.listing_detail_photo_count, current, total),
      )

  @Test
  fun firstPageShowsTheFirstPhoto() {
    setContent(listing.copy(photoUrls = listOf(FakePhotos.RED, FakePhotos.BLUE)))

    composeTestRule.waitUntilCenterIs(Color.Red) { photoPage(0) }
  }

  @Test
  fun swipingShowsTheNextPhoto() {
    setContent(listing.copy(photoUrls = listOf(FakePhotos.RED, FakePhotos.BLUE)))

    swipeToNextPhoto()

    composeTestRule.waitUntilCenterIs(Color.Blue) { photoPage(1) }
  }

  @Test
  fun eachPageSaysWhichPhotoItShows() {
    setContent(listing.copy(photoUrls = listOf(FakePhotos.RED, FakePhotos.BLUE)))

    photoPage(0).assertContentDescriptionEquals("Photo 1 of 2")
    swipeToNextPhoto()
    photoPage(1).assertContentDescriptionEquals("Photo 2 of 2")
  }

  @Test
  fun cannotSwipePastTheLastPhoto() {
    setContent(listing.copy(photoUrls = listOf(FakePhotos.RED)))

    swipeToNextPhoto()

    assertBadge(current = 1, total = 1)
    composeTestRule.waitUntilCenterIs(Color.Red) { photoPage(0) }
    photoPage(1).assertDoesNotExist()
  }

  @Test
  fun allTenPhotosCanBeReached() {
    val photos = List(9) { FakePhotos.RED } + FakePhotos.BLUE
    setContent(listing.copy(photoUrls = photos))

    repeat(9) { swipeToNextPhoto() }

    assertBadge(current = 10, total = 10)
    composeTestRule.waitUntilCenterIs(Color.Blue) { photoPage(9) }
  }

  @Test
  fun aBrokenPhotoDoesNotStopTheNextOneLoading() {
    setContent(listing.copy(photoUrls = listOf(FakePhotos.BROKEN, FakePhotos.RED)))

    composeTestRule.waitUntilCenterIs(grey) { photoPage(0) }
    swipeToNextPhoto()
    composeTestRule.waitUntilCenterIs(Color.Red) { photoPage(1) }
  }

  @Test
  fun noPhotosShowsOneGreyPageWithoutDescription() {
    setContent(listing.copy(photoUrls = emptyList()))

    composeTestRule.waitUntilCenterIs(grey) { photoPage(0) }
    photoPage(0).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
    photoPage(1).assertDoesNotExist()
  }

  @Test
  fun loadingStateShowsProgressIndicator() {
    setContent(uiState = ListingDetailUiState(isLoading = true))

    composeTestRule.onNodeWithTag(C.Tag.listing_detail_loading).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.listing_detail_title).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.listing_detail_request_button).assertDoesNotExist()
  }

  @Test
  fun errorStateShowsMessageAndRetry() {
    setContent(uiState = ListingDetailUiState(errorMsg = "Network down"))

    composeTestRule.onNodeWithTag(C.Tag.listing_detail_error).assertTextEquals("Network down")
    composeTestRule
        .onNodeWithTag(C.Tag.listing_detail_retry_button)
        .assertIsDisplayed()
        .assertTextEquals(context.getString(R.string.listing_detail_retry))
  }

  @Test
  fun blankErrorShowsGenericMessage() {
    setContent(uiState = ListingDetailUiState(errorMsg = ""))

    val fallback =
        InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getString(R.string.listing_detail_error_fallback)
    composeTestRule.onNodeWithTag(C.Tag.listing_detail_error).assertTextEquals(fallback)
  }

  @Test
  fun notFoundStateShowsMessage() {
    setContent(uiState = ListingDetailUiState(listingNotFound = true))

    composeTestRule
        .onNodeWithTag(C.Tag.listing_detail_not_found)
        .assertIsDisplayed()
        .assertTextEquals(context.getString(R.string.listing_detail_not_found))
    composeTestRule.onNodeWithTag(C.Tag.listing_detail_request_button).assertDoesNotExist()
  }

  @Test
  fun loadingTakesPriorityOverError() {
    setContent(uiState = ListingDetailUiState(isLoading = true, errorMsg = "x"))

    composeTestRule.onNodeWithTag(C.Tag.listing_detail_loading).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.listing_detail_error).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.listing_detail_retry_button).assertDoesNotExist()
  }

  @Test
  fun screenTagIsPresentInEveryState() {
    var uiState by mutableStateOf(ListingDetailUiState(isLoading = true))
    composeTestRule.setContent {
      ListingDetailContent(
          uiState = uiState,
          onRequestDates = {},
          onConfirmDates = { _, _ -> },
          onDismissDatePicker = {},
          onLenderClick = {},
          onRetry = {},
          today = today,
      )
    }

    listOf(
            ListingDetailUiState(isLoading = true),
            ListingDetailUiState(errorMsg = "Network down"),
            ListingDetailUiState(listingNotFound = true),
            ListingDetailUiState(listing = listing),
        )
        .forEach { state ->
          uiState = state
          composeTestRule.onNodeWithTag(C.Tag.listing_detail_screen).assertIsDisplayed()
        }
  }

  @Test
  fun datePickerIsHiddenByDefault() {
    setContent()

    composeTestRule.onNodeWithTag(C.Tag.date_picker_sheet).assertDoesNotExist()
  }

  @Test
  fun datePickerShowsListingWindowAndPrice() {
    setContent(uiState = ListingDetailUiState(listing = listing, isDatePickerOpen = true))

    composeTestRule.onNodeWithTag(C.Tag.date_picker_sheet).assertIsDisplayed()
    val (from, to) =
        formatRangeEnds(
            listing.availableFrom,
            listing.availableTo,
            context.resources.configuration.locales[0],
        )
    composeTestRule
        .onNodeWithTag(C.Tag.date_picker_available)
        .assertTextEquals(context.getString(R.string.date_picker_available, from, to))

    tapDays(oct(15), oct(17))

    composeTestRule
        .onNodeWithTag(C.Tag.date_picker_summary_total)
        .assertTextEquals(context.getString(R.string.listing_card_price, 3 * listing.pricePerDay))
    composeTestRule
        .onNodeWithTag(C.Tag.date_picker_balance)
        .assertTextEquals(context.getString(R.string.listing_card_price, 120))
  }

  @Test
  fun sendRequestDisabledWhenTotalExceedsPlaceholderBalance() {
    setContent(
        uiState =
            ListingDetailUiState(listing = listing.copy(pricePerDay = 50), isDatePickerOpen = true)
    )

    tapDays(oct(15), oct(17))

    composeTestRule
        .onNodeWithTag(C.Tag.date_picker_confirm_button)
        .performScrollTo()
        .assertIsNotEnabled()
    composeTestRule.onNodeWithTag(C.Tag.date_picker_balance_error).assertExists()
  }

  @Test
  fun sendRequestCallsOnConfirmDates() {
    setContent(uiState = ListingDetailUiState(listing = listing, isDatePickerOpen = true))

    tapDays(oct(15), oct(17))
    composeTestRule.onNodeWithTag(C.Tag.date_picker_confirm_button).performScrollTo().performClick()

    assertEquals(listOf(oct(15) to oct(17)), confirmedDates)
  }

  @Test
  fun listingDetailScreenShowsErrorThenListingAfterRetry() {
    // Fails the first load, then returns the listing, so Retry must reach the ViewModel.
    var calls = 0
    val repository =
        object : ListingRepository {
          override suspend fun getListingsByOwner(ownerId: String): Result<List<Listing>> =
              Result.success(emptyList())

          override suspend fun getAllListings(): Result<List<Listing>> = Result.success(emptyList())

          override suspend fun getListing(id: String): Result<Listing?> =
              if (calls++ == 0) Result.failure(IllegalStateException("Network down"))
              else Result.success(listing.takeIf { it.id == id })
        }
    val viewModel = ListingDetailViewModel("tent", repository)
    var clickedOwnerId: String? = null
    composeTestRule.setContent {
      ListingDetailScreen(viewModel = viewModel, onLenderClick = { clickedOwnerId = it })
    }

    waitForTag(C.Tag.listing_detail_error)
    composeTestRule.onNodeWithTag(C.Tag.listing_detail_error).assertTextEquals("Network down")
    composeTestRule.onNodeWithTag(C.Tag.listing_detail_retry_button).performClick()

    waitForTag(C.Tag.listing_detail_title)
    composeTestRule.onNodeWithTag(C.Tag.listing_detail_lender_card).performScrollTo().performClick()
    assertEquals("owner", clickedOwnerId)
  }
}
