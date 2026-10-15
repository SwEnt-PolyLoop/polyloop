// Made with Claude.

package com.swent.polyloop.ui.createlisting

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
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
import org.junit.Assert.assertTrue
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

  private var publishClicks = 0

  private fun photos(count: Int) = List(count) { "" }

  private fun setContent(uiState: CreateListingUiState) {
    composeTestRule.setContent {
      CreateListingContent(
          uiState = uiState,
          onTitleChange = {},
          onDescriptionChange = {},
          onCategoryChange = {},
          onPricePerDayChange = {},
          onItemValueChange = {},
          onPickupAddressChange = {},
          onDropOffAddressChange = {},
          onPickDates = {},
          onConfirmDates = { _, _ -> },
          onDismissDatePicker = {},
          onAddPhoto = {},
          onRemovePhoto = {},
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
          onConfirmDates = { _, _ -> },
          onDismissDatePicker = {},
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
    assertErrorText(C.Tag.create_listing_price_error, R.string.create_listing_amount_invalid)
    assertErrorText(C.Tag.create_listing_item_value_error, R.string.create_listing_amount_invalid)
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
  fun sameStartAndEndDateShowsTheirErrorRightAway() {
    setContent(
        CreateListingUiState(
            availableFrom = LocalDate.of(2026, 10, 6),
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
  fun publishCallsOnPublishEvenWithIncompleteForm() {
    setContent(CreateListingUiState())

    composeTestRule.onNodeWithTag(C.Tag.create_listing_publish_button).performClick()

    assertEquals(1, publishClicks)
  }

  @Test
  fun publishButtonStaysVisibleAfterScrollingToBottom() {
    setContent(filled)

    composeTestRule.onNodeWithTag(C.Tag.create_listing_drop_off_field).performScrollTo()

    composeTestRule.onNodeWithTag(C.Tag.create_listing_publish_button).assertIsDisplayed()
  }

  @Test
  fun screenSendsTypingAndPublishToTheViewModel() {
    val viewModel = CreateListingViewModel()
    composeTestRule.setContent { CreateListingScreen(viewModel = viewModel) }

    composeTestRule.onNodeWithTag(C.Tag.create_listing_title_field).performTextReplacement("Tent")
    composeTestRule.onNodeWithTag(C.Tag.create_listing_publish_button).performClick()

    assertEquals("Tent", viewModel.uiState.value.title)
    assertTrue(viewModel.uiState.value.hasAttemptedPublish)
  }

  @Test
  fun tappingAvailabilityOpensThePickerAndConfirmingFillsTheField() {
    val today = LocalDate.of(2026, 10, 1)
    val from = today.plusDays(2)
    val to = today.plusDays(5)
    val viewModel = CreateListingViewModel()
    composeTestRule.setContent {
      val uiState by viewModel.uiState.collectAsState()
      CreateListingContent(
          uiState = uiState,
          onTitleChange = {},
          onDescriptionChange = {},
          onCategoryChange = {},
          onPricePerDayChange = {},
          onItemValueChange = {},
          onPickupAddressChange = {},
          onDropOffAddressChange = {},
          onPickDates = viewModel::openDatePicker,
          onConfirmDates = viewModel::selectDates,
          onDismissDatePicker = viewModel::dismissDatePicker,
          onAddPhoto = {},
          onRemovePhoto = {},
          onPublish = {},
          today = today,
      )
    }

    composeTestRule
        .onNodeWithTag(C.Tag.create_listing_availability_field)
        .performScrollTo()
        .performClick()
    composeTestRule.onNodeWithTag(C.Tag.date_picker_sheet).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.datePickerDay(from)).performClick()
    composeTestRule.onNodeWithTag(C.Tag.datePickerDay(to)).performClick()
    composeTestRule.onNodeWithTag(C.Tag.date_picker_confirm_button).performScrollTo().performClick()

    composeTestRule.onNodeWithTag(C.Tag.date_picker_sheet).assertDoesNotExist()
    val (start, end) = formatRangeEnds(from, to, context.resources.configuration.locales[0])
    composeTestRule
        .onNodeWithTag(C.Tag.create_listing_availability_field)
        .performScrollTo()
        .assertTextContains(context.getString(R.string.listing_detail_date_range, start, end))
  }
}
