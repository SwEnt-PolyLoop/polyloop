// Made with Claude.

package com.swent.polyloop.ui.handover

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** [QrCode]: what it draws on screen is read back with ZXing's reader, as a scanner would. */
@RunWith(AndroidJUnit4::class)
class QrCodeTest {

  @get:Rule val composeTestRule = createComposeRule()

  private var code by mutableStateOf("secret-code")

  private fun setContent(width: Int = 200) {
    composeTestRule.setContent {
      QrCode(
          code,
          contentDescription = "Your code",
          modifier = Modifier.size(width.dp).testTag(TAG),
      )
    }
  }

  /** The text of the QR code on screen, read from a screenshot of it. */
  private fun readQrOnScreen(): String {
    val pixels = composeTestRule.onNodeWithTag(TAG).captureToImage().toPixelMap()
    val argb =
        IntArray(pixels.width * pixels.height) {
          pixels[it % pixels.width, it / pixels.width].toArgb()
        }
    val source = RGBLuminanceSource(pixels.width, pixels.height, argb)
    return QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text
  }

  @Test
  fun theQrOnScreenHoldsTheCode() {
    setContent()
    assertEquals("secret-code", readQrOnScreen())
  }

  @Test
  fun aLongCodeIsStillReadableAtTheCardSize() {
    code = "Zk3_q-9AbCdEfGhIjKlMnOpQrStUvWxYz0123456789"
    setContent(width = 160)
    assertEquals(code, readQrOnScreen())
  }

  @Test
  fun aNewCodeRedrawsTheQr() {
    setContent()
    code = "another-code"
    composeTestRule.waitForIdle()
    assertEquals("another-code", readQrOnScreen())
  }

  @Test
  fun theQrIsSquare() {
    // Only the width is given: the height follows it.
    composeTestRule.setContent {
      QrCode(code, contentDescription = "Your code", modifier = Modifier.width(200.dp).testTag(TAG))
    }
    composeTestRule.onNodeWithTag(TAG).assertWidthIsEqualTo(200.dp).assertHeightIsEqualTo(200.dp)
  }

  @Test
  fun screenReadersHearTheDescriptionNotTheCode() {
    setContent()
    composeTestRule.onNodeWithContentDescription("Your code").assertExists()
    composeTestRule.onNodeWithContentDescription("secret-code").assertDoesNotExist()
  }

  private companion object {
    const val TAG = "qr"
  }
}
