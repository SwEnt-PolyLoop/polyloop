// Made with Claude.

package com.swent.polyloop.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
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
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageResult
import coil3.request.SuccessResult
import com.swent.polyloop.ui.theme.PolyLoopTheme
import kotlinx.coroutines.awaitCancellation
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TAG = "photo"
private const val RED_URL = "https://test.invalid/red.jpg"
private const val LANDSCAPE_URL = "https://test.invalid/landscape.jpg"
private const val PORTRAIT_URL = "https://test.invalid/portrait.jpg"
private const val SLOW_URL = "https://test.invalid/slow.jpg"
private const val BROKEN_URL = "https://test.invalid/broken.jpg"

/**
 * Answers Coil's requests without the network: [RED_URL] loads a red square, [LANDSCAPE_URL] and
 * [PORTRAIT_URL] load red photos with blue strips (see [photoWithBlueStripsOnLongSides]),
 * [SLOW_URL] never finishes loading, and every other URL fails.
 */
private class FakePhotoServer : Interceptor {
  override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
    val request = chain.request
    val bitmap =
        when (request.data) {
          RED_URL -> redBitmap(width = 10, height = 10)
          LANDSCAPE_URL -> photoWithBlueStripsOnLongSides(width = 80, height = 60)
          PORTRAIT_URL -> photoWithBlueStripsOnLongSides(width = 60, height = 80)
          SLOW_URL -> awaitCancellation()
          else -> return ErrorResult(null, request, IllegalStateException("No such photo"))
        }
    return SuccessResult(bitmap.asImage(), request, DataSource.MEMORY)
  }

  private fun redBitmap(width: Int, height: Int): Bitmap =
      Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
        eraseColor(android.graphics.Color.RED)
      }

  /**
   * A red photo whose outer 5 pixels on its two long sides are blue. Cropping its centre square
   * cuts the blue strips away; stretching or fitting the whole photo into a square would not.
   */
  private fun photoWithBlueStripsOnLongSides(width: Int, height: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    for (x in 0 until width) {
      for (y in 0 until height) {
        val onStrip =
            if (width > height) x < STRIP || x >= width - STRIP
            else y < STRIP || y >= height - STRIP
        bitmap.setPixel(
            x,
            y,
            if (onStrip) android.graphics.Color.BLUE else android.graphics.Color.RED,
        )
      }
    }
    return bitmap
  }

  private companion object {
    const val STRIP = 5
  }
}

@OptIn(DelicateCoilApi::class)
@RunWith(AndroidJUnit4::class)
class ListingPhotoTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  /** The theme's grey, read while composing so it matches what the photo draws. */
  private var grey = Color.Unspecified

  @Before
  fun useFakePhotoServer() {
    SingletonImageLoader.setUnsafe(
        ImageLoader.Builder(context)
            .components { add(FakePhotoServer()) }
            .memoryCache(null)
            .diskCache(null)
            .build()
    )
  }

  @After
  fun restoreImageLoader() {
    SingletonImageLoader.reset()
  }

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

  /** The colour drawn at ([x], [y]) in the photo, in pixels from its top-left corner. */
  private fun pixel(x: Int, y: Int): Int =
      composeTestRule.onNodeWithTag(TAG).captureToImage().toPixelMap()[x, y].toArgb()

  private fun centerPixel(): Int {
    val image = composeTestRule.onNodeWithTag(TAG).captureToImage()
    return image.toPixelMap()[image.width / 2, image.height / 2].toArgb()
  }

  private fun waitUntilCenterIs(color: Color) {
    composeTestRule.waitUntil(timeoutMillis = 5_000) { centerPixel() == color.toArgb() }
  }

  @Test
  fun loadedPhotoIsDrawn() {
    setPhoto(RED_URL)

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
    setPhoto(BROKEN_URL)

    waitUntilCenterIs(grey)
  }

  @Test
  fun photoStillLoadingShowsGrey() {
    setPhoto(SLOW_URL)

    waitUntilCenterIs(grey)
  }

  /**
   * Checks the four corners are red: the photo covers the whole slot (not fitted, which leaves
   * empty bands) and its blue edge strips are cut off (not stretched, which keeps them).
   */
  private fun assertWholeSlotIsRed() {
    val image = composeTestRule.onNodeWithTag(TAG).captureToImage()
    val right = image.width - 1
    val bottom = image.height - 1
    for ((x, y) in listOf(0 to 0, right to 0, 0 to bottom, right to bottom)) {
      assertEquals("pixel ($x, $y)", Color.Red.toArgb(), pixel(x, y))
    }
  }

  @Test
  fun landscapePhotoShowsItsCentreSquareWithoutStretching() {
    setPhoto(LANDSCAPE_URL)
    waitUntilCenterIs(Color.Red)

    assertWholeSlotIsRed()
  }

  @Test
  fun portraitPhotoShowsItsCentreSquareWithoutStretching() {
    setPhoto(PORTRAIT_URL)
    waitUntilCenterIs(Color.Red)

    assertWholeSlotIsRed()
  }

  @Test
  fun keepsTheSizeGivenByTheCaller() {
    setPhoto(LANDSCAPE_URL)
    waitUntilCenterIs(Color.Red)

    composeTestRule.onNodeWithTag(TAG).assertWidthIsEqualTo(80.dp).assertHeightIsEqualTo(80.dp)
  }

  @Test
  fun exposesContentDescription() {
    setPhoto(RED_URL, contentDescription = "Boxing gloves")

    composeTestRule.onNodeWithTag(TAG).assertContentDescriptionEquals("Boxing gloves")
  }

  @Test
  fun nullContentDescriptionExposesNone() {
    setPhoto(RED_URL, contentDescription = null)

    composeTestRule
        .onNodeWithTag(TAG)
        .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
  }
}
