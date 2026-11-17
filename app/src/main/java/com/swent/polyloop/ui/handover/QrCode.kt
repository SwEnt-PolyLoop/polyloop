// Made with Claude.

package com.swent.polyloop.ui.handover

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** The white squares around a QR code, on each side, which scanners need to find its edges. */
private const val QUIET_ZONE = 2

/**
 * [code] drawn as a square QR code, black on white, as large as [modifier] allows. Screen readers
 * read [contentDescription] instead of the code itself.
 */
@Composable
fun QrCode(code: String, contentDescription: String, modifier: Modifier = Modifier) {
  val matrix = remember(code) { QrCodeMatrix.encode(code) }
  Canvas(
      modifier =
          modifier.aspectRatio(1f).background(Color.White).semantics {
            this.contentDescription = contentDescription
          }
  ) {
    val cell = size.width / (matrix.width + 2 * QUIET_ZONE)
    // One path for all the black squares, so no thin line shows between two squares side by side.
    val squares = Path()
    for (y in 0 until matrix.height) {
      for (x in 0 until matrix.width) {
        if (!matrix[x, y]) continue
        val left = (x + QUIET_ZONE) * cell
        val top = (y + QUIET_ZONE) * cell
        squares.addRect(Rect(left, top, left + cell, top + cell))
      }
    }
    drawPath(squares, Color.Black)
  }
}
