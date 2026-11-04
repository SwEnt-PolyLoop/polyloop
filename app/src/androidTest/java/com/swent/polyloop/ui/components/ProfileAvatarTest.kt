// Made with Claude.

package com.swent.polyloop.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
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
import kotlin.math.abs
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

  /**
   * A real 4:3 JPEG in the cache, like a photo from the in-app camera, filled with [color], as a
   * file Uri.
   */
  private fun photoFile(color: Int = Color.RED): String {
    val file = File(context.cacheDir, "avatar-${UUID.randomUUID()}.jpg")
    file.outputStream().use {
      Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888)
          .apply { eraseColor(color) }
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

  /**
   * Waits until the avatar's pixel at the top middle of the circle ([atTop]) or at its centre is
   * mostly [color], i.e. Coil has drawn that photo there.
   */
  private fun waitUntilAvatarIs(color: Int, atTop: Boolean = false) {
    composeTestRule.waitUntil(TIMEOUT_MS) {
      val image =
          composeTestRule
              .onNodeWithTag(C.Tag.profile_avatar_photo)
              .captureToImage()
              .asAndroidBitmap()
      val y = if (atTop) image.height / 16 else image.height / 2
      isMostly(image.getPixel(image.width / 2, y), color)
    }
  }

  /** True when [pixel] is close to [color] (JPEG changes colours slightly). */
  private fun isMostly(pixel: Int, color: Int): Boolean =
      abs(Color.red(pixel) - Color.red(color)) < 40 &&
          abs(Color.green(pixel) - Color.green(color)) < 40 &&
          abs(Color.blue(pixel) - Color.blue(color)) < 40

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
    setContent(photo = photoFile(Color.RED))

    // Cropped to fill the circle: the top of the circle is photo too. If the 4:3 photo were only
    // fitted, a band of background would be left there instead.
    waitUntilAvatarIs(Color.RED, atTop = true)
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

    val newPhoto = photoFile(Color.RED)
    composeTestRule.runOnIdle { photo = newPhoto }

    waitUntilAvatarIs(Color.RED)
    node(C.Tag.profile_avatar).assertDoesNotExist()
  }

  private companion object {
    const val TIMEOUT_MS = 5_000L
  }
}
