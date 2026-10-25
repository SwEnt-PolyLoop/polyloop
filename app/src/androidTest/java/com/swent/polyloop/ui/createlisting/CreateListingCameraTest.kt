// Made with Claude.

package com.swent.polyloop.ui.createlisting

import android.Manifest
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.toSize
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.swent.polyloop.R
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.resources.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The camera layer over the form. No photo is taken here: the preview needs a real camera, which
 * the CI emulator may not have, so only the controls are checked.
 */
@RunWith(AndroidJUnit4::class)
class CreateListingCameraTest {

  @get:Rule(order = 0) val permissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

  @get:Rule(order = 1) val composeTestRule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  private val viewModel = CreateListingViewModel()

  private fun setContent() {
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
          onPickDates = {},
          onConfirmDates = { _, _ -> },
          onDismissDatePicker = {},
          onAddPhoto = {},
          onRemovePhoto = {},
          onPhotoTaken = viewModel::onPhotoTaken,
          onCloseCamera = viewModel::closeCamera,
          onPublish = {},
      )
    }
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag)

  @Test
  fun cameraIsHiddenWhileClosed() {
    setContent()

    node(C.Tag.create_listing_camera).assertDoesNotExist()
  }

  @Test
  fun openCameraShowsTheCameraTheShutterAndTheCloseButton() {
    viewModel.openCamera()
    setContent()

    node(C.Tag.create_listing_camera).assertIsDisplayed()
    node(C.Tag.create_listing_camera_shutter).assertIsDisplayed()
    node(C.Tag.create_listing_camera_close).assertIsDisplayed()
  }

  @Test
  fun cameraShowsHowManyPhotosWereTaken() {
    viewModel.onPhotoTaken("a")
    viewModel.onPhotoTaken("b")
    viewModel.openCamera()
    setContent()

    node(C.Tag.create_listing_camera_count)
        .assertTextEquals(context.getString(R.string.create_listing_camera_count, 2, 10))
  }

  @Test
  fun closeButtonClosesTheCameraAndKeepsThePhotos() {
    viewModel.onPhotoTaken("a")
    viewModel.openCamera()
    setContent()

    node(C.Tag.create_listing_camera_close).performClick()

    composeTestRule.waitForIdle()
    assertFalse(viewModel.uiState.value.isCameraOpen)
    assertTrue(viewModel.uiState.value.photos == listOf("a"))
    node(C.Tag.create_listing_camera).assertDoesNotExist()
  }

  /** The screen rectangle of the node with [tag], whichever window it is in. */
  private fun screenRect(tag: String): Rect {
    val node = node(tag).fetchSemanticsNode()
    return Rect(node.positionOnScreen, node.size.toSize())
  }

  @Test
  fun cameraCoversThePublishBarAndTheForm() {
    viewModel.openCamera()
    setContent()

    // The camera is a separate window: the form behind it is still composed, but it lies entirely
    // under the camera, so the Publish button cannot be reached.
    val camera = screenRect(C.Tag.create_listing_camera)
    val publish = screenRect(C.Tag.create_listing_publish_button)
    val form = screenRect(C.Tag.create_listing_screen)
    listOf(publish, form).forEach { covered ->
      assertTrue(camera.left <= covered.left && camera.top <= covered.top)
      assertTrue(camera.right >= covered.right && camera.bottom >= covered.bottom)
    }
  }

  @Test
  fun backClosesTheCameraAndKeepsThePhotos() {
    viewModel.onPhotoTaken("a")
    viewModel.onPhotoTaken("b")
    viewModel.openCamera()
    setContent()
    node(C.Tag.create_listing_camera).assertIsDisplayed()

    Espresso.pressBack()

    composeTestRule.waitForIdle()
    assertFalse(viewModel.uiState.value.isCameraOpen)
    assertEquals(listOf("a", "b"), viewModel.uiState.value.photos)
    node(C.Tag.create_listing_camera).assertDoesNotExist()
  }

  @Test
  fun cameraClosesByItselfAtMaxPhotos() {
    repeat(Listing.MAX_PHOTOS) { viewModel.onPhotoTaken("photo$it") }
    viewModel.openCamera()
    setContent()

    composeTestRule.waitForIdle()
    assertFalse(viewModel.uiState.value.isCameraOpen)
    node(C.Tag.create_listing_camera).assertDoesNotExist()
  }
}
