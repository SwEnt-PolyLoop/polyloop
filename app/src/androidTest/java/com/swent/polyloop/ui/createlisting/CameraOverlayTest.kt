// Made with Claude.

package com.swent.polyloop.ui.createlisting

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
class CameraOverlayTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  private var shutterClicks = 0
  private var closeClicks = 0

  private fun setContent(
      photoCount: Int = 0,
      canTakePhoto: Boolean = true,
      errorMessage: String? = null,
      maxPhotos: Int = Listing.MAX_PHOTOS,
  ) {
    composeTestRule.setContent {
      CameraOverlay(
          photoCount = photoCount,
          maxPhotos = maxPhotos,
          canTakePhoto = canTakePhoto,
          errorMessage = errorMessage,
          onShutter = { shutterClicks++ },
          onClose = { closeClicks++ },
      )
    }
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag)

  @Test
  fun showsTheShutterTheCloseButtonAndTheCount() {
    setContent()

    node(C.Tag.create_listing_camera).assertIsDisplayed()
    node(C.Tag.create_listing_camera_shutter).assertIsDisplayed()
    node(C.Tag.create_listing_camera_close).assertIsDisplayed()
    node(C.Tag.create_listing_camera_count).assertIsDisplayed()
  }

  @Test
  fun countShowsThePhotosTakenOutOfTheMaximum() {
    setContent(photoCount = 4)

    node(C.Tag.create_listing_camera_count)
        .assertTextEquals(context.getString(R.string.create_listing_camera_count, 4, 10))
  }

  @Test
  fun countShowsAnotherMaximumWhenGiven() {
    setContent(photoCount = 2, maxPhotos = 3)

    node(C.Tag.create_listing_camera_count)
        .assertTextEquals(context.getString(R.string.create_listing_camera_count, 2, 3))
  }

  @Test
  fun countIsHiddenForASinglePhoto() {
    setContent(maxPhotos = 1)

    node(C.Tag.create_listing_camera_count).assertDoesNotExist()
    node(C.Tag.create_listing_camera_shutter).assertIsDisplayed()
    node(C.Tag.create_listing_camera_close).assertIsDisplayed()
  }

  @Test
  fun shutterCallsOnShutter() {
    setContent()

    node(C.Tag.create_listing_camera_shutter).assertIsEnabled().performClick()

    assertEquals(1, shutterClicks)
  }

  @Test
  fun shutterIsDisabledWhenAPhotoCannotBeTaken() {
    setContent(canTakePhoto = false)

    node(C.Tag.create_listing_camera_shutter).assertIsNotEnabled().performClick()

    assertEquals(0, shutterClicks)
  }

  @Test
  fun closeButtonCallsOnClose() {
    setContent()

    node(C.Tag.create_listing_camera_close).performClick()

    assertEquals(1, closeClicks)
  }

  @Test
  fun errorMessageIsShownWhenGiven() {
    setContent(errorMessage = "No camera")

    node(C.Tag.create_listing_camera_error).assertIsDisplayed().assertTextEquals("No camera")
  }

  @Test
  fun errorMessageIsHiddenWhenNull() {
    setContent()

    node(C.Tag.create_listing_camera_error).assertDoesNotExist()
  }
}
