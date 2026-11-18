// Made with Claude.

package com.swent.polyloop.ui.handover

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** [QrCodeMatrix]: each QR code is read back with ZXing's own reader, as a scanner would. */
class QrCodeMatrixTest {

  /** Reads [matrix] back, drawn [scale] pixels per square with a white border of 4 squares. */
  private fun read(matrix: BitMatrix, scale: Int = 4): String {
    val border = 4
    val side = (matrix.width + 2 * border) * scale
    val pixels = IntArray(side * side) { WHITE }
    for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
      if (!matrix[x, y]) continue
      for (dy in 0 until scale) for (dx in 0 until scale) {
        pixels[((y + border) * scale + dy) * side + (x + border) * scale + dx] = BLACK
      }
    }
    val image = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(side, side, pixels)))
    return QRCodeReader().decode(image, mapOf(DecodeHintType.CHARACTER_SET to "UTF-8")).text
  }

  @Test
  fun theCodeIsReadBackExactly() {
    for (code in
        listOf(
            "secret-code",
            "x", // a single character
            "Zk3_q-9AbCdEfGhIjKlMnOpQrStUvWxYz0123456789", // 43 random URL-safe characters
            "a".repeat(64),
            "with spaces, and: symbols / ? & = #",
        )) {
      assertEquals(code, read(QrCodeMatrix.encode(code)))
    }
  }

  @Test
  fun aCodeWithAccentsIsReadBackExactly() {
    assertEquals("é ü 日本", read(QrCodeMatrix.encode("é ü 日本")))
  }

  @Test
  fun theCodeIsSquareWithAQrSize() {
    for (code in listOf("x", "a".repeat(64), "b".repeat(300))) {
      val matrix = QrCodeMatrix.encode(code)
      assertEquals(matrix.width, matrix.height)
      // QR codes come in sizes of 21, 25, 29... squares (21 + 4 per version).
      assertEquals(0, (matrix.width - 21) % 4)
    }
  }

  @Test
  fun aLongerCodeGivesABiggerQr() {
    assertTrue(QrCodeMatrix.encode("b".repeat(300)).width > QrCodeMatrix.encode("x").width)
  }

  @Test
  fun theQrHasNoWhiteBorder() {
    // The corner squares are dark right at the edge: the screen adds the white border.
    val matrix = QrCodeMatrix.encode("secret-code")
    val last = matrix.width - 1
    assertTrue(matrix[0, 0])
    assertTrue(matrix[last, 0])
    assertTrue(matrix[0, last])
    assertFalse(matrix[last, last]) // the one corner without a finder square
  }

  @Test
  fun theSameCodeAlwaysGivesTheSameQr() {
    assertEquals(QrCodeMatrix.encode("secret-code"), QrCodeMatrix.encode("secret-code"))
  }

  @Test
  fun anEmptyCodeIsRefused() {
    assertThrows(IllegalArgumentException::class.java) { QrCodeMatrix.encode("") }
  }

  private companion object {
    const val WHITE = 0xFFFFFFFF.toInt()
    const val BLACK = 0xFF000000.toInt()
  }
}
