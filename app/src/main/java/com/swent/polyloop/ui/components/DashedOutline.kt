// Made with Claude.

package com.swent.polyloop.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Draws a 1 dp dashed outline of [color] around the content, with rounded corners of radius
 * [corner]: the look of an empty photo tile that opens the in-app camera.
 */
fun Modifier.dashedOutline(color: Color, corner: Dp): Modifier = drawBehind {
  drawRoundRect(
      color = color,
      cornerRadius = CornerRadius(corner.toPx()),
      style =
          Stroke(
              width = 1.dp.toPx(),
              pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
          ),
  )
}
