// Made with Claude.

package com.swent.polyloop.ui.createlisting

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swent.polyloop.R
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.resources.C
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CreateListingPhotosTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  private var addPhotoClicks = 0
  private val removedIndexes = mutableListOf<Int>()

  private fun photos(count: Int) = List(count) { "" }

  /** Shows the photos card as the screen will: its flags come from [CreateListingUiState]. */
  private fun setContent(uiState: CreateListingUiState) {
    composeTestRule.setContent {
      PhotosCard(
          photos = uiState.photos,
          canAddPhoto = uiState.canAddPhoto,
          missingPhotos = uiState.missingPhotos,
          onAddPhoto = { addPhotoClicks++ },
          onRemovePhoto = { removedIndexes += it },
      )
    }
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true)

  /** A node in the photo row, scrolled into view first. */
  private fun tile(tag: String) = node(tag).performScrollTo()

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

    tile(C.Tag.create_listing_add_photo).performClick()

    assertEquals(1, addPhotoClicks)
  }

  @Test
  fun removeButtonCallsOnRemovePhotoWithItsIndex() {
    setContent(CreateListingUiState(photos = photos(3)))

    tile(C.Tag.create_listing_remove_photo_ + 1).performClick()
    tile(C.Tag.create_listing_remove_photo_ + 2).performClick()

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

    node(C.Tag.create_listing_photos_hint).assertDoesNotExist()
  }

  @Test
  fun addPhotoTileIsHiddenAtMaxPhotos() {
    setContent(CreateListingUiState(photos = photos(Listing.MAX_PHOTOS)))

    tile(C.Tag.create_listing_photo_ + (Listing.MAX_PHOTOS - 1)).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.create_listing_add_photo).assertDoesNotExist()
  }
}
