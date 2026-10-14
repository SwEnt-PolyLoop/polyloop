// Made with Claude.

package com.swent.polyloop.ui.createlisting

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swent.polyloop.R
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.listingdetail.formatRangeEnds
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CreateListingFieldsTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  private val titleLabel = context.getString(R.string.create_listing_title_label)

  private val availableFrom = LocalDate.of(2026, 10, 6)
  private val availableTo = LocalDate.of(2026, 10, 20)

  private var chosenCategory: ListingCategory? = null
  private var pickDatesClicks = 0
  private var publishClicks = 0

  private fun setLabelledField(errorRes: Int?, helperText: String? = null) {
    composeTestRule.setContent {
      LabelledField(
          label = titleLabel,
          errorRes = errorRes,
          errorTag = C.Tag.create_listing_title_error,
          helperText = helperText,
      ) { isError ->
        FormTextField(
            value = "",
            onValueChange = {},
            isError = isError,
            tag = C.Tag.create_listing_title_field,
            label = titleLabel,
        )
      }
    }
  }

  private fun setAvailabilityField(from: LocalDate?, to: LocalDate?) {
    composeTestRule.setContent {
      AvailabilityField(
          from = from,
          to = to,
          isError = false,
          label = "Available",
          onClick = { pickDatesClicks++ },
      )
    }
  }

  @Test
  fun labelledFieldShowsItsErrorOnlyWhenGiven() {
    setLabelledField(errorRes = R.string.create_listing_title_required)

    composeTestRule
        .onNodeWithTag(C.Tag.create_listing_title_error, useUnmergedTree = true)
        .assertIsDisplayed()
        .assertTextEquals(context.getString(R.string.create_listing_title_required))
  }

  @Test
  fun labelledFieldHidesItsErrorWithoutOne() {
    setLabelledField(errorRes = null)

    composeTestRule.onNodeWithTag(C.Tag.create_listing_title_field).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(C.Tag.create_listing_title_error, useUnmergedTree = true)
        .assertDoesNotExist()
  }

  @Test
  fun titleFieldHasItsLabelAsContentDescription() {
    setLabelledField(errorRes = null)

    composeTestRule.onNodeWithContentDescription(titleLabel).assertIsDisplayed()
  }

  @Test
  fun labelledFieldShowsItsHelperText() {
    setLabelledField(errorRes = null, helperText = "Shown to borrowers")

    composeTestRule.onNodeWithText("Shown to borrowers").assertIsDisplayed()
  }

  @Test
  fun formTextFieldShowsPlaceholderAndSuffix() {
    composeTestRule.setContent {
      FormTextField(
          value = "",
          onValueChange = {},
          isError = false,
          tag = C.Tag.create_listing_title_field,
          label = titleLabel,
          placeholder = "e.g. 5",
          suffix = "PP",
      )
    }

    composeTestRule
        .onNodeWithTag(C.Tag.create_listing_title_field)
        .assertTextContains("e.g. 5")
        .assertTextContains("PP")
  }

  @Test
  fun multiLineFormTextFieldShowsItsValue() {
    composeTestRule.setContent {
      FormTextField(
          value = "A two-person tent",
          onValueChange = {},
          isError = false,
          tag = C.Tag.create_listing_title_field,
          label = titleLabel,
          minLines = 3,
      )
    }

    composeTestRule
        .onNodeWithTag(C.Tag.create_listing_title_field)
        .assertTextContains("A two-person tent")
  }

  @Test
  fun categoryDropdownShowsChosenCategory() {
    composeTestRule.setContent {
      CategoryDropdown(
          category = ListingCategory.MUSIC,
          isError = false,
          label = "Category",
          onCategoryChange = {},
      )
    }

    composeTestRule
        .onNodeWithTag(C.Tag.create_listing_category_field)
        .assertTextContains(ListingCategory.MUSIC.label)
  }

  @Test
  fun choosingCategoryCallsOnCategoryChange() {
    composeTestRule.setContent {
      CategoryDropdown(
          category = null,
          isError = false,
          label = "Category",
          onCategoryChange = { chosenCategory = it },
      )
    }

    composeTestRule.onNodeWithTag(C.Tag.create_listing_category_field).performClick()
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
  fun availabilityShowsFormattedRange() {
    setAvailabilityField(availableFrom, availableTo)

    val (from, to) =
        formatRangeEnds(availableFrom, availableTo, context.resources.configuration.locales[0])
    composeTestRule
        .onNodeWithTag(C.Tag.create_listing_availability_field)
        .assertTextContains(context.getString(R.string.listing_detail_date_range, from, to))
  }

  @Test
  fun availabilityShowsPlaceholderWhenEmpty() {
    setAvailabilityField(null, null)

    composeTestRule
        .onNodeWithTag(C.Tag.create_listing_availability_field)
        .assertTextContains(context.getString(R.string.create_listing_availability_placeholder))
  }

  @Test
  fun tappingAvailabilityCallsOnPickDates() {
    setAvailabilityField(null, null)

    composeTestRule.onNodeWithTag(C.Tag.create_listing_availability_field).performClick()

    assertEquals(1, pickDatesClicks)
  }

  @Test
  fun scrollingFromAvailabilityDoesNotCallOnPickDates() {
    composeTestRule.setContent {
      Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        AvailabilityField(
            from = null,
            to = null,
            isError = false,
            label = "Available",
            onClick = { pickDatesClicks++ },
        )
        Spacer(modifier = Modifier.height(2000.dp))
      }
    }

    composeTestRule.onNodeWithTag(C.Tag.create_listing_availability_field).performTouchInput {
      swipeUp()
    }

    assertEquals(0, pickDatesClicks)
  }

  @Test
  fun publishIsDisabledWhilePublishing() {
    composeTestRule.setContent { PublishBar(isPublishing = true, onPublish = {}) }

    composeTestRule.onNodeWithTag(C.Tag.create_listing_publish_button).assertIsNotEnabled()
    composeTestRule
        .onNodeWithTag(C.Tag.create_listing_publishing, useUnmergedTree = true)
        .assertIsDisplayed()
  }

  @Test
  fun publishCallsOnPublishWhenNotPublishing() {
    composeTestRule.setContent { PublishBar(isPublishing = false, onPublish = { publishClicks++ }) }

    composeTestRule
        .onNodeWithTag(C.Tag.create_listing_publishing, useUnmergedTree = true)
        .assertDoesNotExist()
    composeTestRule
        .onNodeWithTag(C.Tag.create_listing_publish_button)
        .assertIsEnabled()
        .performClick()

    assertEquals(1, publishClicks)
  }
}
