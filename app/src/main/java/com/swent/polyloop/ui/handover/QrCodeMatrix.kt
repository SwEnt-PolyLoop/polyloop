// Made with Claude.

package com.swent.polyloop.ui.handover

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Turns the handover code into the squares of a QR code, which the screen then draws. */
object QrCodeMatrix {

  /**
   * The QR code holding [text]: one cell per square, true for a dark one. It has no white border,
   * so the screen adds its own around it. Fails with IllegalArgumentException on an empty text.
   */
  fun encode(text: String): BitMatrix {
    require(text.isNotEmpty()) { "Nothing to put in the QR code" }
    val hints =
        mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            // M still reads with about 15% of the code hidden, e.g. a glare on the lender's screen.
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 0,
        )
    // A size of 0 asks for the smallest one: one cell per square.
    return QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
  }
}
