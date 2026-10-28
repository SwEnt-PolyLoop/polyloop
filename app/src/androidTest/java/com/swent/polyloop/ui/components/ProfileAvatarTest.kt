// Made with Claude.

package com.swent.polyloop.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.theme.PolyLoopTheme
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileAvatarTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val files = mutableListOf<File>()

  @After
  fun tearDown() {
    files.forEach { it.delete() }
  }

  /** A real 4:3 JPEG in the cache, like a photo from the in-app camera, as a file Uri. */
  private fun photoFile(): String {
    val file = File(context.cacheDir, "avatar-${UUID.randomUUID()}.jpg")
    file.outputStream().use {
      Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888)
          .compress(Bitmap.CompressFormat.JPEG, 80, it)
    }
    files += file
    return Uri.fromFile(file).toString()
  }

  private val missingPhoto = "file:///data/missing-${UUID.randomUUID()}.jpg"

  private fun setContent(photo: String, name: String = "John") {
    composeTestRule.setContent { PolyLoopTheme { ProfileAvatar(photo, name, 80.dp) } }
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag)

  /** Waits until Coil gave up on the photo and the initial took its place. */
  private fun waitForTheInitial() {
    composeTestRule.waitUntil(TIMEOUT_MS) {
      composeTestRule.onAllNodesWithTag(C.Tag.profile_avatar).fetchSemanticsNodes().isNotEmpty()
    }
  }

  @Test
  fun withoutPhotoTheInitialIsShown() {
    setContent(photo = "")

    node(C.Tag.profile_avatar).assertTextEquals("J")
    node(C.Tag.profile_avatar_photo).assertDoesNotExist()
  }

  @Test
  fun withoutNameOrPhotoAQuestionMarkIsShown() {
    setContent(photo = "", name = "  ")

    node(C.Tag.profile_avatar).assertTextEquals("?")
  }

  @Test
  fun theInitialIsUppercaseAndIgnoresLeadingSpaces() {
    setContent(photo = "", name = "  jane")

    node(C.Tag.profile_avatar).assertTextEquals("J")
  }

  @Test
  fun aPhotoFillsTheWholeCircleInsteadOfTheInitial() {
    setContent(photo = photoFile())
    composeTestRule.waitForIdle()

    // Cropped to fill the circle: a 4:3 photo takes the full 80 × 80, not a smaller band.
    node(C.Tag.profile_avatar_photo).assertWidthIsEqualTo(80.dp).assertHeightIsEqualTo(80.dp)
    node(C.Tag.profile_avatar).assertDoesNotExist()
  }

  @Test
  fun aPhotoThatCannotLoadFallsBackToTheInitial() {
    setContent(photo = missingPhoto)

    waitForTheInitial()
    node(C.Tag.profile_avatar).assertTextEquals("J")
    node(C.Tag.profile_avatar_photo).assertDoesNotExist()
  }

  @Test
  fun aNewPhotoIsTriedAgainAfterAFailedOne() {
    var photo by mutableStateOf(missingPhoto)
    composeTestRule.setContent { PolyLoopTheme { ProfileAvatar(photo, "John", 80.dp) } }
    waitForTheInitial()

    val newPhoto = photoFile()
    composeTestRule.runOnIdle { photo = newPhoto }
    composeTestRule.waitForIdle()

    node(C.Tag.profile_avatar_photo).assertWidthIsEqualTo(80.dp)
    node(C.Tag.profile_avatar).assertDoesNotExist()
  }

  private companion object {
    const val TIMEOUT_MS = 5_000L
  }
}
