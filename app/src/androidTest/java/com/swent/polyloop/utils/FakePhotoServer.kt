// Made with Claude.

package com.swent.polyloop.utils

import android.graphics.Bitmap
import android.graphics.Color.BLUE
import android.graphics.Color.RED
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeTestRule
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
import kotlin.math.abs
import kotlinx.coroutines.awaitCancellation
import org.junit.rules.ExternalResource

/** Photo URLs answered by [FakePhotoServerRule]. `.invalid` domains never exist on the internet. */
object FakePhotos {
  const val RED = "https://test.invalid/red.jpg" // red square
  const val BLUE = "https://test.invalid/blue.jpg" // blue square
  const val LANDSCAPE = "https://test.invalid/landscape.jpg" // 80×60, blue strips left and right
  const val PORTRAIT = "https://test.invalid/portrait.jpg" // 60×80, blue strips top and bottom
  const val SLOW = "https://test.invalid/slow.jpg" // never finishes loading
  const val BROKEN = "https://test.invalid/broken.jpg" // fails, like any other URL
}

/** Makes Coil load every photo from [FakePhotoServer], without cache, and restores it after. */
@OptIn(DelicateCoilApi::class)
class FakePhotoServerRule : ExternalResource() {
  override fun before() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val loader = ImageLoader.Builder(context).components { add(FakePhotoServer()) }
    SingletonImageLoader.setUnsafe(loader.memoryCache(null).diskCache(null).build())
  }

  override fun after() = SingletonImageLoader.reset()
}

/** Answers Coil's requests for the [FakePhotos] URLs instead of the network. */
private class FakePhotoServer : Interceptor {
  override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
    val bitmap =
        when (chain.request.data) {
          FakePhotos.RED -> photo(10, 10)
          FakePhotos.BLUE -> photo(10, 10, color = BLUE)
          FakePhotos.LANDSCAPE -> photo(80, 60)
          FakePhotos.PORTRAIT -> photo(60, 80)
          FakePhotos.SLOW -> awaitCancellation()
          else -> return ErrorResult(null, chain.request, IllegalStateException("No such photo"))
        }
    return SuccessResult(bitmap.asImage(), chain.request, DataSource.MEMORY)
  }

  /**
   * A [color] photo. If it is not square, its outer 5 pixels on the two long sides are blue:
   * cropping its centre square cuts them off, while stretching or fitting it would not.
   */
  private fun photo(width: Int, height: Int, color: Int = RED): Bitmap {
    val pixels =
        IntArray(width * height) { i ->
          val (x, y) = i % width to i / width

          if (
              (width > height && (x < 5 || x >= width - 5)) ||
                  (height > width && (y < 5 || y >= height - 5))
          )
              BLUE
          else color
        }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
  }
}

/** The colour drawn at ([x], [y]) in this node, in pixels from its top-left corner. */
fun SemanticsNodeInteraction.colorAt(x: Int, y: Int): Color = captureToImage().toPixelMap()[x, y]

/** The colour drawn in the middle of this node. */
fun SemanticsNodeInteraction.centerColor(): Color =
    captureToImage().let { it.toPixelMap()[it.width / 2, it.height / 2] }

/**
 * True if this colour is [other], give or take 8 out of 255 in each of red, green and blue.
 * Emulators that draw without a graphics card, like the one in CI, round edge pixels slightly
 * differently (e.g. red 255, green 1, blue 1), so exact equality would fail there.
 */
fun Color.isCloseTo(other: Color): Boolean =
    abs(red - other.red) <= TOLERANCE &&
        abs(green - other.green) <= TOLERANCE &&
        abs(blue - other.blue) <= TOLERANCE

private const val TOLERANCE = 8 / 255f

/** Waits (photos load in the background) until the middle of [node] shows [color]. */
fun ComposeTestRule.waitUntilCenterIs(color: Color, node: () -> SemanticsNodeInteraction) =
    waitUntil(timeoutMillis = 5_000) { node().centerColor().isCloseTo(color) }
