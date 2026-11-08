// Made with Claude.

package com.swent.polyloop.ui.createlisting

import android.Manifest
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.ViewModelStore
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.swent.polyloop.R
import com.swent.polyloop.model.auth.FakeAuthRepository
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.resources.C
import java.io.File
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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

  private val viewModel = CreateListingViewModel(UnusedListingRepository(), FakeAuthRepository())

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
          onCaptureFailed = {},
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

  /** A fake capture that records the file and lets the test decide when and how it ends. */
  private class FakeCapture {
    var file: File? = null
    var onSaved: () -> Unit = {}
    var onError: () -> Unit = {}
    var captures = 0

    fun capture(file: File, onSaved: () -> Unit, onError: () -> Unit) {
      captures++
      file.writeText("jpeg")
      this.file = file
      this.onSaved = onSaved
      this.onError = onError
    }
  }

  private fun setCameraContent(
      fake: FakeCapture,
      photoCount: Int = 0,
      onPhotoTaken: (String) -> Unit = {},
      onClose: () -> Unit = {},
      maxPhotos: Int = Listing.MAX_PHOTOS,
      onCaptureFailed: (String) -> Unit = {},
      fileDispatcher: CoroutineDispatcher = Dispatchers.Main,
  ) {
    composeTestRule.setContent {
      PolyLoopCameraContent(
          photoCount = photoCount,
          maxPhotos = maxPhotos,
          onPhotoTaken = onPhotoTaken,
          onClose = onClose,
          preview = { _, modifier -> Box(modifier) },
          capture = fake::capture,
          onCaptureFailed = onCaptureFailed,
          fileDispatcher = fileDispatcher,
      )
    }
  }

  @Test
  fun shutterTakesAPhotoInTheListingPhotosFolder() {
    val fake = FakeCapture()
    val taken = mutableListOf<String>()
    setCameraContent(fake, onPhotoTaken = { taken.add(it) })

    node(C.Tag.create_listing_camera_shutter).performClick()
    composeTestRule.runOnIdle { fake.onSaved() }

    composeTestRule.waitForIdle()
    assertEquals(1, taken.size)
    assertEquals("file://${fake.file!!.absolutePath}", taken[0])
    assertEquals(LISTING_PHOTOS_DIR, fake.file!!.parentFile!!.name)
  }

  /** Takes one photo with the shutter into [vm]; returns its file, written inside `filesDir`. */
  private fun takePhotoInto(vm: CreateListingViewModel): File {
    val fake = FakeCapture()
    setCameraContent(fake, onPhotoTaken = vm::onPhotoTaken)
    node(C.Tag.create_listing_camera_shutter).performClick()
    composeTestRule.runOnIdle { fake.onSaved() }
    composeTestRule.waitForIdle()
    val file = fake.file!!
    assertEquals(File(context.filesDir, LISTING_PHOTOS_DIR), file.parentFile)
    assertEquals(1, vm.uiState.value.photos.size)
    assertTrue(file.exists())
    return file
  }

  @Test
  fun removingATakenPhotoDeletesItsFile() {
    val vm =
        storedCreateListingViewModel(
            ViewModelStore(),
            UnusedListingRepository(),
            FakeAuthRepository(),
        )
    val file = takePhotoInto(vm)

    vm.onRemovePhoto(0)

    assertFalse(file.exists())
    assertTrue(vm.uiState.value.photos.isEmpty())
  }

  @Test
  fun clearingTheViewModelWithoutPublishingDeletesTheTakenPhotoFile() {
    val store = ViewModelStore()
    val vm = storedCreateListingViewModel(store, UnusedListingRepository(), FakeAuthRepository())
    val file = takePhotoInto(vm)

    store.clear()

    assertFalse(file.exists())
  }

  @Test
  fun shutterIsDisabledWhileAPhotoIsBeingSaved() {
    val fake = FakeCapture()
    setCameraContent(fake)
    node(C.Tag.create_listing_camera_shutter).assertIsEnabled()

    node(C.Tag.create_listing_camera_shutter).performClick()

    node(C.Tag.create_listing_camera_shutter).assertIsNotEnabled()
    composeTestRule.runOnIdle { fake.onSaved() }
    node(C.Tag.create_listing_camera_shutter).assertIsEnabled()
  }

  @Test
  fun twoTapsBeforeTheNextRecompositionTakeOnlyOnePhoto() {
    val fake = FakeCapture()
    val taken = mutableListOf<String>()
    setCameraContent(fake, onPhotoTaken = { taken.add(it) })
    val shutterClick =
        node(C.Tag.create_listing_camera_shutter)
            .fetchSemanticsNode()
            .config[SemanticsActions.OnClick]
            .action!!

    // Both taps run in one UI-thread block, so the shutter cannot be disabled in between.
    composeTestRule.runOnUiThread {
      shutterClick()
      shutterClick()
    }
    composeTestRule.runOnIdle { fake.onSaved() }

    composeTestRule.waitForIdle()
    assertEquals(1, fake.captures)
    assertEquals(1, taken.size)
  }

  @Test
  fun failedCaptureShowsTheErrorAndKeepsTheCameraOpen() {
    val fake = FakeCapture()
    var closed = false
    val taken = mutableListOf<String>()
    val failed = mutableListOf<String>()
    setCameraContent(
        fake,
        onPhotoTaken = { taken.add(it) },
        onClose = { closed = true },
        onCaptureFailed = { failed.add(it) },
    )
    node(C.Tag.create_listing_camera_error).assertDoesNotExist()

    node(C.Tag.create_listing_camera_shutter).performClick()
    composeTestRule.waitForIdle()
    assertTrue(fake.file!!.exists())
    composeTestRule.runOnIdle { fake.onError() }

    composeTestRule.waitForIdle()
    // The screen only reports the failure; deleting the file is the caller's job.
    assertEquals(listOf(Uri.fromFile(fake.file).toString()), failed)
    assertTrue(fake.file!!.exists())
    fake.file!!.delete()
    node(C.Tag.create_listing_camera_error).assertIsDisplayed()
    node(C.Tag.create_listing_camera).assertIsDisplayed()
    node(C.Tag.create_listing_camera_shutter).assertIsEnabled()
    assertFalse(closed)
    assertTrue(taken.isEmpty())
  }

  @Test
  fun aSinglePhotoCameraClosesByItselfOnceThePhotoIsTaken() {
    val fake = FakeCapture()
    var photoCount by mutableIntStateOf(0)
    var closed = false
    composeTestRule.setContent {
      PolyLoopCameraContent(
          photoCount = photoCount,
          maxPhotos = 1,
          onPhotoTaken = { photoCount++ },
          onClose = { closed = true },
          preview = { _, modifier -> Box(modifier) },
          capture = fake::capture,
          onCaptureFailed = {},
          fileDispatcher = Dispatchers.Main,
      )
    }

    node(C.Tag.create_listing_camera_shutter).performClick()
    composeTestRule.runOnIdle {
      assertFalse(closed)
      fake.onSaved()
    }

    composeTestRule.runOnIdle { assertTrue(closed) }
  }

  @Test
  fun aSinglePhotoCameraOpenedWithItsPhotoCannotTakeAnother() {
    var closed = false
    setCameraContent(FakeCapture(), photoCount = 1, maxPhotos = 1, onClose = { closed = true })

    node(C.Tag.create_listing_camera_shutter).assertIsNotEnabled()
    composeTestRule.runOnIdle { assertTrue(closed) }
  }

  @Test
  fun openingTheCameraDeletesStalePhotosExceptTheOnesTheFormHolds() {
    val held = staleFile(context, "held-${System.nanoTime()}.jpg")
    val notHeld = staleFile(context, "not-held-${System.nanoTime()}.jpg")
    try {
      viewModel.onPhotoTaken(Uri.fromFile(held).toString())
      viewModel.openCamera()
      setContent()

      composeTestRule.waitUntil(5_000) { !notHeld.exists() }
      composeTestRule.waitForIdle()
      assertTrue(held.exists())
    } finally {
      held.delete()
      notHeld.delete()
    }
  }

  /** Makes the photo folder impossible to create: a plain file sits where it should be. */
  private fun blockPhotoFolder(): File {
    val folder = File(context.filesDir, LISTING_PHOTOS_DIR)
    folder.deleteRecursively()
    folder.writeText("not a folder")
    return folder
  }

  @Test
  fun aFailedFilePreparationShowsTheErrorAndLetsTheUserTapAgain() {
    val folder = blockPhotoFolder()
    try {
      val fake = FakeCapture()
      setCameraContent(fake)

      node(C.Tag.create_listing_camera_shutter).performClick()

      composeTestRule.waitForIdle()
      node(C.Tag.create_listing_camera_error).assertIsDisplayed()
      node(C.Tag.create_listing_camera_shutter).assertIsEnabled()
      assertEquals(0, fake.captures)

      // The folder works again: a new tap takes the photo and clears the error.
      folder.delete()
      node(C.Tag.create_listing_camera_shutter).performClick()
      composeTestRule.waitForIdle()
      assertEquals(1, fake.captures)
      node(C.Tag.create_listing_camera_error).assertDoesNotExist()
    } finally {
      folder.deleteRecursively()
    }
  }

  @Test
  fun closingTheCameraWhileTheFileIsPreparedStartsNoCapture() {
    val queued = mutableListOf<Runnable>()
    val manual =
        object : CoroutineDispatcher() {
          override fun dispatch(context: CoroutineContext, block: Runnable) {
            queued += block
          }
        }
    val fake = FakeCapture()
    var showCamera by mutableStateOf(true)
    composeTestRule.setContent {
      if (showCamera) {
        PolyLoopCameraContent(
            photoCount = 0,
            onPhotoTaken = {},
            onClose = {},
            preview = { _, modifier -> Box(modifier) },
            capture = fake::capture,
            onCaptureFailed = {},
            fileDispatcher = manual,
        )
      }
    }
    node(C.Tag.create_listing_camera_shutter).performClick()
    composeTestRule.waitForIdle()

    composeTestRule.runOnIdle { showCamera = false }
    composeTestRule.waitForIdle()
    // The preparation (and the opening cleanup) finishes only now, after the camera is gone.
    while (queued.isNotEmpty()) queued.removeAt(0).run()
    composeTestRule.waitForIdle()

    assertEquals(0, fake.captures)
  }
}
