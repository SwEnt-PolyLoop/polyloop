// Made with Claude.

package com.swent.polyloop.ui.profile

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.swent.polyloop.R
import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.auth.FakeAuthRepository
import com.swent.polyloop.model.profile.FakeProfileRepository
import com.swent.polyloop.model.profile.UserProfile
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.theme.PolyLoopTheme
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Edit profile screen, through a real [EditProfileViewModel] with fake repositories, and its
 * stateless content for states that are hard to reach. No photo is taken: the camera preview needs
 * a real camera, which the CI emulator may not have.
 */
@RunWith(AndroidJUnit4::class)
class EditProfileScreenTest {

  @get:Rule(order = 0) val permissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

  @get:Rule(order = 1) val composeTestRule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  private val john = UserProfile(uid = "me", name = "John", email = "john@epfl.ch")
  private val profiles = FakeProfileRepository(listOf(john))
  private val auth =
      FakeAuthRepository(signedInUser = AuthUser(uid = "me", email = john.email, name = "John"))
  private val viewModel by lazy { EditProfileViewModel(profiles, auth) }

  private var savedCalls = 0
  private var takePhotoClicks = 0
  private var retryClicks = 0
  private val files = mutableListOf<File>()

  @After
  fun tearDown() {
    files.forEach { it.delete() }
  }

  private lateinit var backDispatcher: OnBackPressedDispatcher

  /** Shows the screen backed by the real ViewModel. */
  private fun showScreen() {
    composeTestRule.setContent {
      backDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
      PolyLoopTheme { EditProfileScreen(viewModel = viewModel, onSaved = { savedCalls++ }) }
    }
  }

  /** Shows only the stateless content in [state]. */
  private fun showContent(state: EditProfileUiState, cameraPermissionGranted: Boolean? = null) {
    composeTestRule.setContent {
      PolyLoopTheme {
        EditProfileContent(
            uiState = state,
            onNameChange = {},
            onTakePhoto = { takePhotoClicks++ },
            onPhotoTaken = {},
            onCloseCamera = {},
            onSave = {},
            onRetry = { retryClicks++ },
            onSaved = { savedCalls++ },
            cameraPermissionGranted = cameraPermissionGranted,
        )
      }
    }
  }

  private val loaded = EditProfileUiState(name = "John", isLoading = false)

  /** A real JPEG in the cache filled with [color], like a photo from the camera, as a file Uri. */
  private fun photoFile(color: Int = Color.RED): String {
    val file = File(context.cacheDir, "edit-profile-${UUID.randomUUID()}.jpg")
    file.outputStream().use {
      Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888)
          .apply { eraseColor(color) }
          .compress(Bitmap.CompressFormat.JPEG, 80, it)
    }
    files += file
    return Uri.fromFile(file).toString()
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag)

  // ---------- loading ----------

  @Test
  fun loadingShowsASpinner() {
    showContent(EditProfileUiState())

    node(C.Tag.edit_profile_loading).assertIsDisplayed()
    node(C.Tag.edit_profile_name_field).assertDoesNotExist()
  }

  @Test
  fun aFailedLoadShowsTheErrorAndRetry() {
    showContent(EditProfileUiState(isLoading = false, loadFailed = true))

    node(C.Tag.edit_profile_load_error)
        .assertTextEquals(context.getString(R.string.edit_profile_load_error))
    node(C.Tag.edit_profile_retry_button).performClick()

    composeTestRule.runOnIdle { assertEquals(1, retryClicks) }
    node(C.Tag.edit_profile_save_button).assertDoesNotExist()
  }

  @Test
  fun theLoadedProfileFillsTheForm() {
    showScreen()

    node(C.Tag.edit_profile_name_field).assertTextContains("John")
    node(C.Tag.profile_avatar).assertTextEquals("J")
    node(C.Tag.edit_profile_take_photo_button).assertIsEnabled()
    node(C.Tag.edit_profile_save_button).assertIsEnabled()
  }

  // ---------- saving ----------

  @Test
  fun savingANewNameSavesItAndGoesBack() {
    showScreen()

    node(C.Tag.edit_profile_name_field).performTextReplacement("Johnny")
    node(C.Tag.edit_profile_save_button).performClick()

    composeTestRule.runOnIdle {
      assertEquals("Johnny", profiles.profiles.single().name)
      assertEquals(1, savedCalls)
    }
  }

  @Test
  fun aBlankNameShowsTheErrorAndSavesNothing() {
    showScreen()

    node(C.Tag.edit_profile_name_field).performTextReplacement("   ")
    node(C.Tag.edit_profile_save_button).performClick()

    // The supporting text is merged into the field's node, so it is looked up unmerged.
    composeTestRule
        .onNodeWithTag(C.Tag.edit_profile_name_error, useUnmergedTree = true)
        .assertTextEquals(context.getString(R.string.edit_profile_name_required))
    composeTestRule.runOnIdle {
      assertEquals(john, profiles.profiles.single())
      assertEquals(0, savedCalls)
    }
  }

  @Test
  fun aFailedSaveShowsTheErrorAndKeepsTheName() {
    showScreen()
    profiles.failure = IllegalStateException("offline")

    node(C.Tag.edit_profile_name_field).performTextReplacement("Johnny")
    node(C.Tag.edit_profile_save_button).performClick()

    node(C.Tag.edit_profile_save_error)
        .assertTextEquals(context.getString(R.string.edit_profile_save_error))
    node(C.Tag.edit_profile_name_field).assertTextContains("Johnny")
    node(C.Tag.edit_profile_save_button).assertIsEnabled()
    composeTestRule.runOnIdle { assertEquals(0, savedCalls) }
  }

  @Test
  fun backDoesNothingWhileSaving() {
    showScreen()
    val gate = CompletableDeferred<Unit>()
    profiles.gate = gate
    try {
      node(C.Tag.edit_profile_save_button).performClick()
      node(C.Tag.edit_profile_saving).assertIsDisplayed()

      // Through the dispatcher, as in CheckInboxScreenTest: Espresso.pressBack() waits for focus.
      composeTestRule.runOnUiThread { backDispatcher.onBackPressed() }

      node(C.Tag.edit_profile_screen).assertIsDisplayed()
      composeTestRule.runOnIdle { assertTrue(viewModel.uiState.value.isSaving) }
    } finally {
      gate.complete(Unit)
    }
    composeTestRule.runOnIdle { assertEquals(1, savedCalls) }
  }

  @Test
  fun whileSavingTheFormIsDisabledAndShowsASpinner() {
    showContent(loaded.copy(isSaving = true))

    node(C.Tag.edit_profile_saving).assertIsDisplayed()
    node(C.Tag.edit_profile_save_button).assertIsNotEnabled()
    node(C.Tag.edit_profile_name_field).assertIsNotEnabled()
    node(C.Tag.edit_profile_take_photo_button).assertIsNotEnabled()
  }

  // ---------- photo ----------

  @Test
  fun takePhotoAsksForTheCamera() {
    showContent(loaded)

    node(C.Tag.edit_profile_take_photo_button).performClick()

    composeTestRule.runOnIdle { assertEquals(1, takePhotoClicks) }
  }

  @Test
  fun aRefusedCameraPermissionShowsHowToAllowIt() {
    showContent(loaded, cameraPermissionGranted = false)

    node(C.Tag.create_listing_camera_permission_message)
        .assertTextEquals(context.getString(R.string.create_listing_camera_permission_denied))
  }

  @Test
  fun theNewPhotoReplacesTheSavedOneInTheAvatar() {
    showContent(loaded.copy(photoUrl = photoFile(Color.BLUE), newPhotoUri = photoFile(Color.RED)))

    // The centre of the avatar turns red (the new photo), never staying blue (the saved one).
    composeTestRule.waitUntil(5_000) {
      val image = node(C.Tag.profile_avatar_photo).captureToImage().asAndroidBitmap()
      val pixel = image.getPixel(image.width / 2, image.height / 2)
      Color.red(pixel) > 200 && Color.blue(pixel) < 80
    }
  }

  @Test
  fun theCameraOpensForASinglePhotoWithoutCount() {
    showScreen()
    composeTestRule.runOnIdle { viewModel.openCamera() }

    node(C.Tag.create_listing_camera).assertIsDisplayed()
    node(C.Tag.create_listing_camera_shutter).assertIsEnabled()
    node(C.Tag.create_listing_camera_count).assertDoesNotExist()
  }

  @Test
  fun theCloseButtonClosesTheCamera() {
    showScreen()
    composeTestRule.runOnIdle { viewModel.openCamera() }

    node(C.Tag.create_listing_camera_close).performClick()

    node(C.Tag.create_listing_camera).assertDoesNotExist()
    composeTestRule.runOnIdle { assertFalse(viewModel.uiState.value.isCameraOpen) }
  }

  @Test
  fun aPhotoTakenClosesTheCameraAndIsSavedWithTheName() {
    showScreen()
    composeTestRule.runOnIdle { viewModel.openCamera() }
    val photo = photoFile()

    composeTestRule.runOnIdle { viewModel.onPhotoTaken(photo) }
    node(C.Tag.create_listing_camera).assertDoesNotExist()
    node(C.Tag.edit_profile_save_button).performClick()

    composeTestRule.runOnIdle {
      assertEquals(listOf("me" to photo), profiles.uploadedPhotos)
      assertEquals(FakeProfileRepository.uploadedUrl(photo), profiles.profiles.single().photoUrl)
      assertEquals(1, savedCalls)
    }
  }
}
