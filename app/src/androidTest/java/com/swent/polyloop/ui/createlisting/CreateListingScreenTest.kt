// Made with Claude.

package com.swent.polyloop.ui.createlisting

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swent.polyloop.R
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.listingdetail.formatRangeEnds
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CreateListingScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  private val filled =
      CreateListingUiState(
          title = "Camping tent, 2 people",
          description = "Light 2-person tent, used three times.",
          category = ListingCategory.SPORTS_OUTDOOR,
          pricePerDay = "15",
          itemValue = "180",
          availableFrom = LocalDate.of(2026, 10, 6),
          availableTo = LocalDate.of(2026, 10, 20),
          pickupAddress = "Route Cantonale 1, Ecublens",
          dropOffAddress = "Avenue Piccard 2, Lausanne",
          photos = photos(Listing.MIN_PHOTOS),
      )

  private var chosenCategory: ListingCategory? = null
  private var pickDatesClicks = 0
  private var addPhotoClicks = 0
  private val removedIndexes = mutableListOf<Int>()
  private var publishClicks = 0

  private fun photos(count: Int) = List(count) { "" }

  private fun setContent(uiState: CreateListingUiState) {
    composeTestRule.setContent {
      CreateListingContent(
          uiState = uiState,
          onTitleChange = {},
          onDescriptionChange = {},
          onCategoryChange = { chosenCategory = it },
          onPricePerDayChange = {},
          onItemValueChange = {},
          onPickupAddressChange = {},
          onDropOffAddressChange = {},
          onPickDates = { pickDatesClicks++ },
          onAddPhoto = { addPhotoClicks++ },
          onRemovePhoto = { removedIndexes += it },
          onPublish = { publishClicks++ },
      )
    }
  }

  private fun node(tag: String) =
      composeTestRule.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo()

  private fun assertErrorText(tag: String, res: Int) =
      node(tag).assertIsDisplayed().assertTextEquals(context.getString(res))

  private val hasError = SemanticsMatcher.keyIsDefined(SemanticsProperties.Error)

  /** Each required field's tag, its error's tag and the error shown when it is left empty. */
  private val requiredFields =
      listOf(
          Triple(
              C.Tag.create_listing_title_field,
              C.Tag.create_listing_title_error,
              R.string.create_listing_title_required,
          ),
          Triple(
              C.Tag.create_listing_category_field,
              C.Tag.create_listing_category_error,
              R.string.create_listing_category_required,
          ),
          Triple(
              C.Tag.create_listing_description_field,
              C.Tag.create_listing_description_error,
              R.string.create_listing_description_required,
          ),
          Triple(
              C.Tag.create_listing_price_field,
              C.Tag.create_listing_price_error,
              R.string.create_listing_price_required,
          ),
          Triple(
              C.Tag.create_listing_item_value_field,
              C.Tag.create_listing_item_value_error,
              R.string.create_listing_item_value_required,
          ),
          Triple(
              C.Tag.create_listing_availability_field,
              C.Tag.create_listing_availability_error,
              R.string.create_listing_availability_required,
          ),
          Triple(
              C.Tag.create_listing_pickup_field,
              C.Tag.create_listing_pickup_error,
              R.string.create_listing_pickup_required,
          ),
          Triple(
              C.Tag.create_listing_drop_off_field,
              C.Tag.create_listing_drop_off_error,
              R.string.create_listing_drop_off_required,
          ),
      )

  @Test
  fun textFieldsShowTheirValues() {
    setContent(filled)

    mapOf(
            C.Tag.create_listing_title_field to filled.title,
            C.Tag.create_listing_description_field to filled.description,
            C.Tag.create_listing_price_field to filled.pricePerDay,
            C.Tag.create_listing_item_value_field to filled.itemValue,
            C.Tag.create_listing_pickup_field to filled.pickupAddress,
            C.Tag.create_listing_drop_off_field to filled.dropOffAddress,
            C.Tag.create_listing_category_field to ListingCategory.SPORTS_OUTDOOR.label,
        )
        .forEach { (tag, value) ->
          composeTestRule.onNodeWithTag(tag).performScrollTo().assertTextContains(value)
        }
  }

  @Test
  fun typingInEachTextFieldCallsItsCallback() {
    // Held here and updated by each callback, like the ViewModel will.
    var state by mutableStateOf(CreateListingUiState())
    composeTestRule.setContent {
      CreateListingContent(
          uiState = state,
          onTitleChange = { state = state.copy(title = it) },
          onDescriptionChange = { state = state.copy(description = it) },
          onCategoryChange = {},
          onPricePerDayChange = { state = state.copy(pricePerDay = it) },
          onItemValueChange = { state = state.copy(itemValue = it) },
          onPickupAddressChange = { state = state.copy(pickupAddress = it) },
          onDropOffAddressChange = { state = state.copy(dropOffAddress = it) },
          onPickDates = {},
          onAddPhoto = {},
          onRemovePhoto = {},
          onPublish = {},
      )
    }
    val tags =
        listOf(
            C.Tag.create_listing_title_field,
            C.Tag.create_listing_description_field,
            C.Tag.create_listing_price_field,
            C.Tag.create_listing_item_value_field,
            C.Tag.create_listing_pickup_field,
            C.Tag.create_listing_drop_off_field,
        )

    tags.forEach {
      composeTestRule.onNodeWithTag(it).performScrollTo().performTextReplacement("42")
    }

    tags.forEach { composeTestRule.onNodeWithTag(it).performScrollTo().assertTextContains("42") }
    assertEquals(
        CreateListingUiState(
            title = "42",
            description = "42",
            pricePerDay = "42",
            itemValue = "42",
            pickupAddress = "42",
            dropOffAddress = "42",
        ),
        state,
    )
  }

  @Test
  fun choosingCategoryCallsOnCategoryChange() {
    setContent(CreateListingUiState())

    composeTestRule
        .onNodeWithTag(C.Tag.create_listing_category_field)
        .performScrollTo()
        .performClick()
    ListingCategory.entries.forEach {
      composeTestRule
          .onNodeWithTag(C.Tag.create_listing_category_option_ + it.name)
          .assertTextContains(it.label)
    }
    composeTestRule
        .onNodeWithTag(C.Tag.create_listing_category_option_ + ListingCategory.MUSIC.name)
        .performClick()

    assertEquals(ListingCategory.MUSIC, chosenCategory)
  }

  @Test
  fun emptyFieldErrorsAreHiddenBeforePublishAttempt() {
    setContent(CreateListingUiState())

    requiredFields.forEach { (_, errorTag, _) ->
      composeTestRule.onNodeWithTag(errorTag, useUnmergedTree = true).assertDoesNotExist()
    }
  }

  @Test
  fun emptyFieldErrorsAreShownAfterPublishAttempt() {
    setContent(CreateListingUiState(hasAttemptedPublish = true))

    requiredFields.forEach { (fieldTag, errorTag, res) ->
      composeTestRule.onNodeWithTag(fieldTag).performScrollTo().assert(hasError)
      assertErrorText(errorTag, res)
    }
  }

  @Test
  fun invalidAmountsShowTheirErrorRightAway() {
    setContent(CreateListingUiState(pricePerDay = "12.5", itemValue = "0"))

    composeTestRule.onNodeWithTag(C.Tag.create_listing_price_field).assert(hasError)
    assertErrorText(C.Tag.create_listing_price_error, R.string.create_listing_price_invalid)
    assertErrorText(
        C.Tag.create_listing_item_value_error,
        R.string.create_listing_item_value_invalid,
    )
  }

  @Test
  fun datesInWrongOrderShowTheirErrorRightAway() {
    setContent(
        CreateListingUiState(
            availableFrom = LocalDate.of(2026, 10, 20),
            availableTo = LocalDate.of(2026, 10, 6),
        )
    )

    assertErrorText(
        C.Tag.create_listing_availability_error,
        R.string.create_listing_availability_invalid,
    )
  }

  @Test
  fun completeFormShowsNoErrors() {
    setContent(filled.copy(hasAttemptedPublish = true))

    requiredFields.forEach { (_, errorTag, _) ->
      composeTestRule.onNodeWithTag(errorTag, useUnmergedTree = true).assertDoesNotExist()
    }
    composeTestRule
        .onNodeWithTag(C.Tag.create_listing_photos_hint, useUnmergedTree = true)
        .assertDoesNotExist()
  }

  @Test
  fun photoCounterShowsCountAndLimits() {
    setContent(CreateListingUiState(photos = photos(2)))

    node(C.Tag.create_listing_photos_counter)
        .assertTextEquals(
            context.getString(
                R.string.create_listing_photo_count,
                2,
                Listing.MAX_PHOTOS,
                Listing.MIN_PHOTOS,
            )
        )
  }

  @Test
  fun addPhotoTileCallsOnAddPhoto() {
    setContent(CreateListingUiState())

    node(C.Tag.create_listing_add_photo).performClick()

    assertEquals(1, addPhotoClicks)
  }

  @Test
  fun removeButtonCallsOnRemovePhotoWithItsIndex() {
    setContent(CreateListingUiState(photos = photos(3)))

    node(C.Tag.create_listing_remove_photo_ + 1).performClick()
    node(C.Tag.create_listing_remove_photo_ + 2).performClick()

    assertEquals(listOf(1, 2), removedIndexes)
  }

  @Test
  fun photoHintShowsHowManyPhotosAreMissing() {
    setContent(CreateListingUiState(photos = photos(1)))

    val missing = Listing.MIN_PHOTOS - 1
    node(C.Tag.create_listing_photos_hint)
        .assertTextEquals(
            context.resources.getQuantityString(
                R.plurals.create_listing_missing_photos,
                missing,
                missing,
            )
        )
  }

  @Test
  fun photoHintDisappearsAtMinPhotos() {
    setContent(CreateListingUiState(photos = photos(Listing.MIN_PHOTOS)))

    composeTestRule
        .onNodeWithTag(C.Tag.create_listing_photos_hint, useUnmergedTree = true)
        .assertDoesNotExist()
  }

  @Test
  fun addPhotoTileIsHiddenAtMaxPhotos() {
    setContent(CreateListingUiState(photos = photos(Listing.MAX_PHOTOS)))

    node(C.Tag.create_listing_photo_ + (Listing.MAX_PHOTOS - 1)).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.create_listing_add_photo).assertDoesNotExist()
  }

  @Test
  fun availabilityShowsFormattedRange() {
    setContent(filled)

    val (from, to) =
        formatRangeEnds(
            filled.availableFrom!!,
            filled.availableTo!!,
            context.resources.configuration.locales[0],
        )
    composeTestRule
        .onNodeWithTag(C.Tag.create_listing_availability_field)
        .performScrollTo()
        .assertTextContains(context.getString(R.string.listing_detail_date_range, from, to))
  }

  @Test
  fun availabilityShowsPlaceholderWhenEmpty() {
    setContent(CreateListingUiState())

    composeTestRule
        .onNodeWithTag(C.Tag.create_listing_availability_field)
        .performScrollTo()
        .assertTextContains(context.getString(R.string.create_listing_availability_placeholder))
  }

  @Test
  fun tappingAvailabilityCallsOnPickDates() {
    setContent(CreateListingUiState())

    composeTestRule
        .onNodeWithTag(C.Tag.create_listing_availability_field)
        .performScrollTo()
        .performClick()

    assertEquals(1, pickDatesClicks)
  }

  @Test
  fun publishCallsOnPublishEvenWithIncompleteForm() {
    setContent(CreateListingUiState())

    composeTestRule.onNodeWithTag(C.Tag.create_listing_publish_button).performClick()

    assertEquals(1, publishClicks)
  }

  @Test
  fun publishIsDisabledWhilePublishing() {
    setContent(filled.copy(isPublishing = true))

    composeTestRule.onNodeWithTag(C.Tag.create_listing_publish_button).assertIsNotEnabled()
    composeTestRule
        .onNodeWithTag(C.Tag.create_listing_publishing, useUnmergedTree = true)
        .assertIsDisplayed()
  }

  @Test
  fun publishButtonStaysVisibleAfterScrollingToBottom() {
    setContent(filled)

    composeTestRule.onNodeWithTag(C.Tag.create_listing_drop_off_field).performScrollTo()

    composeTestRule.onNodeWithTag(C.Tag.create_listing_publish_button).assertIsDisplayed()
  }
}
