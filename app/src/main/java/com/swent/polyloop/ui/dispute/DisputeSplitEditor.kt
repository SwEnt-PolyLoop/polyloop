// Made with Claude.

package com.swent.polyloop.ui.dispute

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import com.swent.polyloop.R
import com.swent.polyloop.resources.C
import kotlin.math.roundToInt

/**
 * The split bar, always laid out the same so the card does not move when editing starts: it takes
 * the bar's height only, the handle drawn over the space around it. With [onLenderPercent], a
 * handle sits on the line between the two shares: dragging it, or tapping the bar, moves the line
 * by 1% and calls [onLenderPercent]. The bar is a visual shortcut, hidden from screen readers:
 * their users set the split with the percent fields of [InlinePercent].
 */
@Composable
internal fun ProposalSplitBar(lenderPercent: Int, onLenderPercent: ((Int) -> Unit)? = null) {
  val onChange by rememberUpdatedState(onLenderPercent)
  val thumbSize = dimensionResource(R.dimen.dispute_split_thumb_size)
  val barHeight = dimensionResource(R.dimen.dispute_split_bar_height)
  val gestures =
      if (onLenderPercent == null) Modifier
      else
          Modifier.testTag(C.Tag.dispute_split_slider)
              .pointerInput(Unit) {
                detectTapGestures {
                  onChange?.invoke(percentAt(it.x, size.width, thumbSize.toPx() / 2))
                }
              }
              .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                  change.consume()
                  onChange?.invoke(percentAt(change.position.x, size.width, thumbSize.toPx() / 2))
                }
              }
  BoxWithConstraints(
      modifier =
          Modifier.fillMaxWidth()
              .semantics { hideFromAccessibility() }
              // Takes only the bar's height in the card; the handle overflows above and below it.
              .layout { measurable, constraints ->
                val thumb = thumbSize.roundToPx()
                val placeable =
                    measurable.measure(constraints.copy(minHeight = thumb, maxHeight = thumb))
                val height = barHeight.roundToPx()
                layout(placeable.width, height) {
                  placeable.place(0, (height - placeable.height) / 2)
                }
              }
              .then(gestures)
  ) {
    val track = maxWidth - thumbSize
    SplitBar(
        lenderPercent = lenderPercent,
        modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = thumbSize / 2),
    )
    if (onLenderPercent != null) {
      Box(
          modifier =
              Modifier.align(Alignment.CenterStart)
                  .offset(x = track * lenderPercent / 100)
                  .size(thumbSize)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.surface)
                  .border(
                      dimensionResource(R.dimen.dispute_split_thumb_border),
                      MaterialTheme.colorScheme.primary,
                      CircleShape,
                  )
      )
    }
  }
}

/**
 * "[label] 70%", laid out the same whether it can be edited or not; [modifier] goes on the number,
 * which screen readers name [description]. With [onText], the number is underlined and can be typed
 * in: [onText] gets every accepted keystroke (a whole number from 0 to 100, or an empty field), and
 * [onBlur] runs when the field loses the focus.
 */
@Composable
internal fun InlinePercent(
    label: String,
    text: String,
    description: String,
    modifier: Modifier = Modifier,
    onText: ((String) -> Unit)? = null,
    onBlur: () -> Unit = {},
) {
  val style =
      MaterialTheme.typography.bodyMedium.copy(
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
          textAlign = TextAlign.Center,
      )
  val measurer = rememberTextMeasurer()
  val width =
      with(LocalDensity.current) { measurer.measure(text.ifEmpty { "0" }, style).size.width.toDp() }
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(text = "$label ", style = style)
    BasicTextField(
        value = text,
        onValueChange = { typed ->
          if (typed.isEmpty() || parsePercent(typed) != null) onText?.invoke(typed)
        },
        enabled = onText != null,
        singleLine = true,
        textStyle = style,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier =
            modifier
                .semantics { contentDescription = description }
                .width(width + dimensionResource(R.dimen.dispute_percent_extra_width))
                .onFocusChanged { if (!it.isFocused) onBlur() },
        decorationBox = { inner ->
          Column {
            inner()
            HorizontalDivider(
                thickness = dimensionResource(R.dimen.dispute_percent_underline),
                color =
                    if (onText != null) MaterialTheme.colorScheme.onSurface else Color.Transparent,
            )
          }
        },
    )
    Text(text = "%", style = style)
  }
}

/**
 * The lender's percentage at [x] on a bar [width] pixels wide whose ends are [inset] pixels in from
 * each side, rounded to 1% and kept from 0 to 100; 0 when the bar is no wider than its two insets,
 * so there is no track to move along.
 */
internal fun percentAt(x: Float, width: Int, inset: Float): Int =
    if (width <= 2 * inset) 0
    else ((x - inset) / (width - 2 * inset) * 100).roundToInt().coerceIn(0, 100)

/** [text] as a whole percentage from 0 to 100, or null if it is anything else. */
internal fun parsePercent(text: String): Int? =
    if (text.length in 1..3 && text.all { it.isDigit() }) text.toInt().takeIf { it <= 100 }
    else null

/** The split as a bar: the lender's share in red on the left, the borrower's in grey. */
@Composable
internal fun SplitBar(lenderPercent: Int, modifier: Modifier = Modifier) {
  Row(
      modifier =
          modifier
              .fillMaxWidth()
              .height(dimensionResource(R.dimen.dispute_split_bar_height))
              .clip(RoundedCornerShape(dimensionResource(R.dimen.dispute_split_bar_corner)))
              .background(MaterialTheme.colorScheme.outline)
              .testTag(C.Tag.dispute_split_bar)
  ) {
    if (lenderPercent > 0) {
      Box(
          modifier =
              Modifier.fillMaxHeight()
                  .weight(lenderPercent.toFloat())
                  .background(MaterialTheme.colorScheme.primary)
      )
    }
    if (lenderPercent < 100) Box(modifier = Modifier.weight((100 - lenderPercent).toFloat()))
  }
}

/** [label] on the left, and [tenths] of a PolyPoint in bold on the right. */
@Composable
internal fun GetBackRow(label: String, tenths: Int, modifier: Modifier = Modifier) {
  Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = stringResource(R.string.dispute_amount, formatTenths(tenths)),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
    )
  }
}

/** [tenths] of a PolyPoint written with one decimal, or none when it is whole: "18", "16.5". */
internal fun formatTenths(tenths: Int): String =
    if (tenths % 10 == 0) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
