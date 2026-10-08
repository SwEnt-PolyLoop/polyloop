// Made with Claude.

package com.swent.polyloop.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.polyloop.ui.theme.PolyLoopTheme
import com.swent.polyloop.utils.FakePhotoServerRule
import com.swent.polyloop.utils.FakePhotos
import com.swent.polyloop.utils.colorAt
import com.swent.polyloop.utils.isCloseTo
import com.swent.polyloop.utils.waitUntilCenterIs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TAG = "photo"

@RunWith(AndroidJUnit4::class)
class ListingPhotoTest {

  @get:Rule val composeTestRule = createComposeRule()

  @get:Rule val fakePhotoServer = FakePhotoServerRule()

  /** The theme's grey, read while composing so it matches what the photo draws. */
  private var grey = Color.Unspecified

  private fun setPhoto(url: String?, contentDescription: String? = "Boxing gloves") {
    composeTestRule.setContent {
      PolyLoopTheme {
        grey = MaterialTheme.colorScheme.surfaceVariant
        ListingPhoto(
            url = url,
            contentDescription = contentDescription,
            modifier = Modifier.size(80.dp).testTag(TAG),
        )
      }
    }
  }

  private fun photo() = composeTestRule.onNodeWithTag(TAG)

  private fun waitUntilCenterIs(color: Color) = composeTestRule.waitUntilCenterIs(color, ::photo)

  @Test
  fun loadedPhotoIsDrawn() {
    setPhoto(FakePhotos.RED)

    waitUntilCenterIs(Color.Red)
  }

  @Test
  fun nullUrlShowsGrey() {
    setPhoto(url = null)

    waitUntilCenterIs(grey)
  }

  @Test
  fun blankUrlShowsGrey() {
    setPhoto(url = "  ")

    waitUntilCenterIs(grey)
  }

  @Test
  fun brokenUrlShowsGrey() {
    setPhoto(FakePhotos.BROKEN)

    waitUntilCenterIs(grey)
  }

  @Test
  fun photoStillLoadingShowsGrey() {
    setPhoto(FakePhotos.SLOW)

    waitUntilCenterIs(grey)
  }

  /**
   * Checks the four corners are red: the photo covers the whole slot (not fitted, which leaves
   * empty bands) and its blue edge strips are cut off (not stretched, which keeps them).
   */
  private fun assertWholeSlotIsRed() {
    val image = photo().captureToImage()
    val right = image.width - 1
    val bottom = image.height - 1
    for ((x, y) in listOf(0 to 0, right to 0, 0 to bottom, right to bottom)) {
      val color = photo().colorAt(x, y)
      assertTrue("pixel ($x, $y) is $color, not red", color.isCloseTo(Color.Red))
    }
  }

  @Test
  fun landscapePhotoShowsItsCentreSquareWithoutStretching() {
    setPhoto(FakePhotos.LANDSCAPE)
    waitUntilCenterIs(Color.Red)

    assertWholeSlotIsRed()
  }

  @Test
  fun portraitPhotoShowsItsCentreSquareWithoutStretching() {
    setPhoto(FakePhotos.PORTRAIT)
    waitUntilCenterIs(Color.Red)

    assertWholeSlotIsRed()
  }

  @Test
  fun keepsTheSizeGivenByTheCaller() {
    setPhoto(FakePhotos.LANDSCAPE)
    waitUntilCenterIs(Color.Red)

    photo().assertWidthIsEqualTo(80.dp).assertHeightIsEqualTo(80.dp)
  }

  @Test
  fun exposesContentDescription() {
    setPhoto(FakePhotos.RED, contentDescription = "Boxing gloves")

    photo().assertContentDescriptionEquals("Boxing gloves")
  }

  @Test
  fun nullContentDescriptionExposesNone() {
    setPhoto(FakePhotos.RED, contentDescription = null)

    photo().assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
  }
}
