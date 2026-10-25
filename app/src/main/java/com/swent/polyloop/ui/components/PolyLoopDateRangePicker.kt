// Made with Claude.

package com.swent.polyloop.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.swent.polyloop.R
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.listingdetail.formatRangeEnds
import com.swent.polyloop.ui.theme.PolyLoopTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

private const val UNAVAILABLE_ALPHA = 0.38f

/** What the picker is used for, which decides its rules and its bottom part. */
sealed interface DateRangePickerMode {
  /** Create listing: the lender picks the availability window. */
  data object Availability : DateRangePickerMode

  /**
   * Listing detail: the borrower picks rental dates and sees the price.
   *
   * The [balance] check here only guides the user; it is not a security check. The real check is
   * done server-side when the request is sent (docs/ARCHITECTURE.md, "Money").
   */
  data class Request(val pricePerDay: Int, val balance: Int) : DateRangePickerMode
}

/**
 * A bottom sheet to pick a date range inside [window]. In [DateRangePickerMode.Request] mode the
 * [disabledDates] are booked, the day after each booked stretch is a buffer day, and one free day
 * must follow the picked range. The sheet opens empty every time, and its selection is cleared when
 * [today] changes while it is open, and in Request mode when [disabledDates] change too;
 * [onConfirm] does not close it. [onDayPicked] is called on every tap on a day. While [isSending]
 * the main button is disabled and shows a progress indicator. A non-null [errorMsg] is shown above
 * the main button, or a generic text when it is blank.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PolyLoopDateRangePicker(
    window: ClosedRange<LocalDate>,
    disabledDates: Set<LocalDate>,
    mode: DateRangePickerMode,
    onConfirm: (start: LocalDate, end: LocalDate) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
    isSending: Boolean = false,
    errorMsg: String? = null,
    onDayPicked: () -> Unit = {},
) {
  ModalBottomSheet(
      onDismissRequest = onDismiss,
      sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
      modifier = modifier.testTag(C.Tag.date_picker_sheet),
  ) {
    DateRangePickerBody(
        window = window,
        disabledDates = disabledDates,
        mode = mode,
        onConfirm = onConfirm,
        today = today,
        isSending = isSending,
        errorMsg = errorMsg,
        onDayPicked = onDayPicked,
    )
  }
}

/**
 * The sheet's content holding the current selection, which starts empty and is cleared when the
 * booked days change (e.g. dates booked by someone else while the user was picking) or when [today]
 * changes (e.g. the picked start day passed at midnight). [onDayPicked] is called on every tap on a
 * day.
 */
@Composable
internal fun DateRangePickerBody(
    window: ClosedRange<LocalDate>,
    disabledDates: Set<LocalDate>,
    mode: DateRangePickerMode,
    onConfirm: (start: LocalDate, end: LocalDate) -> Unit,
    today: LocalDate,
    isSending: Boolean = false,
    errorMsg: String? = null,
    onDayPicked: () -> Unit = {},
) {
  // Availability mode has no booked days, hence no buffer days either.
  val booked = if (mode is DateRangePickerMode.Request) disabledDates else emptySet()
  var selection by remember(booked, today) { mutableStateOf(DateRangeSelection()) }
  DateRangePickerContent(
      window = window,
      disabledDates = booked,
      mode = mode,
      selection = selection,
      onDayClick = { day ->
        selection =
            selection.onTap(
                day = day,
                window = window,
                disabledDates = booked,
                today = today,
                requireBufferDay = mode is DateRangePickerMode.Request,
            )
        onDayPicked()
      },
      onConfirm = onConfirm,
      today = today,
      isSending = isSending,
      errorMsg = errorMsg,
  )
}

/** Stateless sheet content: header, month grid, legend, summary, send error and main button. */
@Composable
internal fun DateRangePickerContent(
    window: ClosedRange<LocalDate>,
    disabledDates: Set<LocalDate>,
    mode: DateRangePickerMode,
    selection: DateRangeSelection,
    onDayClick: (LocalDate) -> Unit,
    onConfirm: (start: LocalDate, end: LocalDate) -> Unit,
    today: LocalDate,
    modifier: Modifier = Modifier,
    isSending: Boolean = false,
    errorMsg: String? = null,
) {
  val firstMonth = YearMonth.from(window.start)
  val lastMonth = YearMonth.from(window.endInclusive)
  var month by remember { mutableStateOf(initialMonth(window, disabledDates, today)) }
  val buffers = remember(disabledDates) { bufferDays(disabledDates) }
  Column(
      modifier =
          modifier
              .fillMaxWidth()
              .verticalScroll(rememberScrollState())
              .padding(horizontal = 24.dp)
              .padding(bottom = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Header(
        month = month,
        window = window,
        mode = mode,
        showArrows = firstMonth != lastMonth,
        onPrevious = { month = month.minusMonths(1) }.takeIf { month > firstMonth },
        onNext = { month = month.plusMonths(1) }.takeIf { month < lastMonth },
    )
    MonthGrid(
        month = month,
        dayKind = { day -> selection.kindOf(day, window, disabledDates, today, buffers) },
        onDayClick = onDayClick,
    )
    if (mode is DateRangePickerMode.Request) {
      Legend()
      if (selection.bufferError) {
        Text(
            text = stringResource(R.string.date_picker_buffer_error),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag(C.Tag.date_picker_error),
        )
      }
      if (selection.isComplete) Summary(selection = selection, mode = mode)
      if (selection.exceedsBalance(mode.pricePerDay, mode.balance)) {
        Text(
            text = stringResource(R.string.date_picker_not_enough_balance),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag(C.Tag.date_picker_balance_error),
        )
      }
    }
    if (errorMsg != null) {
      Text(
          text = errorMsg.ifBlank { stringResource(R.string.date_picker_send_error_fallback) },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.testTag(C.Tag.date_picker_send_error),
      )
    }
    ConfirmButton(
        mode = mode,
        selection = selection,
        isSending = isSending,
        onConfirm = onConfirm,
    )
  }
}

/**
 * The month title with its arrows and, in request mode, the window. A null [onPrevious] or [onNext]
 * disables that arrow.
 */
@Composable
private fun Header(
    month: YearMonth,
    window: ClosedRange<LocalDate>,
    mode: DateRangePickerMode,
    showArrows: Boolean,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
) {
  val locale = LocalConfiguration.current.locales[0]
  Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text(
        text = month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.testTag(C.Tag.date_picker_month_title),
    )
    if (showArrows) {
      IconButton(
          onClick = { onPrevious?.invoke() },
          enabled = onPrevious != null,
          modifier = Modifier.testTag(C.Tag.date_picker_prev_month),
      ) {
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = stringResource(R.string.date_picker_previous_month),
        )
      }
      IconButton(
          onClick = { onNext?.invoke() },
          enabled = onNext != null,
          modifier = Modifier.testTag(C.Tag.date_picker_next_month),
      ) {
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = stringResource(R.string.date_picker_next_month),
        )
      }
    }
    Spacer(Modifier.weight(1f))
    if (mode is DateRangePickerMode.Request) {
      val (from, to) = formatRangeEnds(window.start, window.endInclusive, locale)
      Text(
          text = stringResource(R.string.date_picker_available, from, to),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.testTag(C.Tag.date_picker_available),
      )
    }
  }
}

/** Weekday labels, Monday first, then the days of [month] in a 7-column grid. */
@Composable
private fun MonthGrid(
    month: YearMonth,
    dayKind: (LocalDate) -> DayKind,
    onDayClick: (LocalDate) -> Unit,
) {
  val locale = LocalConfiguration.current.locales[0]
  val leadingBlanks = month.atDay(1).dayOfWeek.value - 1
  val cells: List<LocalDate?> =
      List(leadingBlanks) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      DayOfWeek.entries.forEach { weekday ->
        Text(
            text = weekday.getDisplayName(TextStyle.NARROW, locale),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
      }
    }
    cells.chunked(DayOfWeek.entries.size).forEach { week ->
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(DayOfWeek.entries.size) { index ->
          val day = week.getOrNull(index)
          if (day == null) {
            Spacer(Modifier.weight(1f))
          } else {
            DayCell(
                day = day,
                kind = dayKind(day),
                onClick = { onDayClick(day) },
                modifier = Modifier.weight(1f),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun DayCell(day: LocalDate, kind: DayKind, onClick: () -> Unit, modifier: Modifier) {
  val colors = MaterialTheme.colorScheme
  val description =
      when (kind) {
        DayKind.BOOKED -> stringResource(R.string.date_picker_day_booked, day.dayOfMonth)
        DayKind.BUFFER -> stringResource(R.string.date_picker_day_buffer, day.dayOfMonth)
        else -> null
      }
  val textColor =
      when (kind) {
        DayKind.UNAVAILABLE -> colors.onSurfaceVariant.copy(alpha = UNAVAILABLE_ALPHA)
        DayKind.AVAILABLE -> colors.onSurface
        DayKind.BOOKED,
        DayKind.BUFFER -> colors.onSurfaceVariant
        DayKind.SELECTED -> colors.onPrimary
      }
  Box(
      modifier =
          modifier
              .aspectRatio(1f)
              .testTag(C.Tag.datePickerDay(day))
              .dayStyle(kind, MaterialTheme.shapes.small)
              .clickable(enabled = kind == DayKind.AVAILABLE || kind == DayKind.SELECTED) {
                onClick()
              }
              .semantics {
                selected = kind == DayKind.SELECTED
                if (description != null) contentDescription = description
              },
      contentAlignment = Alignment.Center,
  ) {
    Text(
        text = day.dayOfMonth.toString(),
        style = MaterialTheme.typography.bodyLarge,
        color = textColor,
        fontWeight = if (kind == DayKind.SELECTED) FontWeight.Bold else null,
        textDecoration = if (kind == DayKind.BOOKED) TextDecoration.LineThrough else null,
    )
  }
}

/** The box behind a day number (or a legend swatch), drawn according to [kind]. */
@Composable
private fun Modifier.dayStyle(kind: DayKind, shape: Shape): Modifier {
  val colors = MaterialTheme.colorScheme
  return when (kind) {
    DayKind.UNAVAILABLE -> this
    DayKind.AVAILABLE -> clip(shape).border(1.dp, colors.outlineVariant, shape)
    DayKind.BOOKED -> clip(shape).drawBehind { drawRect(colors.surfaceVariant) }
    DayKind.BUFFER ->
        clip(shape).drawBehind {
          drawRect(colors.surfaceVariant)
          drawStripes(colors.outlineVariant)
        }
    DayKind.SELECTED -> clip(shape).drawBehind { drawRect(colors.primary) }
  }
}

/** Thin diagonal stripes from bottom left to top right, across the whole box. */
private fun DrawScope.drawStripes(color: Color) {
  val gap = 6.dp.toPx()
  val stroke = 1.dp.toPx()
  var x = -size.height
  while (x < size.width) {
    drawLine(color, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = stroke)
    x += gap
  }
}

/** Swatches for booked days, buffer days and the user's dates. */
@Composable
private fun Legend() {
  Row(
      modifier = Modifier.testTag(C.Tag.date_picker_legend),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    LegendItem(DayKind.BOOKED, stringResource(R.string.date_picker_legend_booked))
    LegendItem(DayKind.BUFFER, stringResource(R.string.date_picker_legend_buffer))
    LegendItem(DayKind.SELECTED, stringResource(R.string.date_picker_legend_selected))
  }
}

@Composable
private fun LegendItem(kind: DayKind, label: String) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(12.dp).dayStyle(kind, MaterialTheme.shapes.extraSmall))
    Spacer(Modifier.width(6.dp))
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/** Days times price, the total, the user's balance and a note on what is reserved. */
@Composable
private fun Summary(selection: DateRangeSelection, mode: DateRangePickerMode.Request) {
  val total = selection.totalPrice(mode.pricePerDay)
  OutlinedCard(modifier = Modifier.fillMaxWidth().testTag(C.Tag.date_picker_summary)) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      SummaryRow(
          label =
              pluralStringResource(
                  R.plurals.date_picker_summary_days,
                  selection.numberOfDays,
                  selection.numberOfDays,
                  mode.pricePerDay,
              ),
          value = stringResource(R.string.listing_card_price, total),
          valueTag = C.Tag.date_picker_summary_total,
          boldValue = true,
      )
      SummaryRow(
          label = stringResource(R.string.date_picker_balance),
          value = stringResource(R.string.listing_card_price, mode.balance),
          valueTag = C.Tag.date_picker_balance,
          boldValue = false,
      )
      Text(
          text = pluralStringResource(R.plurals.date_picker_reserved_note, total, total),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun SummaryRow(label: String, value: String, valueTag: String, boldValue: Boolean) {
  Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    Text(
        text = value,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = if (boldValue) FontWeight.Bold else null,
        modifier = Modifier.testTag(valueTag),
    )
  }
}

/**
 * "Send request" or "Confirm dates", enabled once a full range is picked, and in request mode only
 * if the balance covers its total. Disabled while [isSending], with a progress indicator in place
 * of its icon.
 */
@Composable
private fun ConfirmButton(
    mode: DateRangePickerMode,
    selection: DateRangeSelection,
    isSending: Boolean,
    onConfirm: (start: LocalDate, end: LocalDate) -> Unit,
) {
  Button(
      onClick = {
        val start = selection.start
        val end = selection.end
        if (start != null && end != null) onConfirm(start, end)
      },
      enabled =
          !isSending &&
              selection.isComplete &&
              !(mode is DateRangePickerMode.Request &&
                  selection.exceedsBalance(mode.pricePerDay, mode.balance)),
      shape = MaterialTheme.shapes.medium,
      modifier = Modifier.fillMaxWidth().height(48.dp).testTag(C.Tag.date_picker_confirm_button),
  ) {
    if (mode is DateRangePickerMode.Request) {
      if (isSending) {
        CircularProgressIndicator(
            strokeWidth = 2.dp,
            modifier = Modifier.size(ButtonDefaults.IconSize).testTag(C.Tag.date_picker_sending),
        )
      } else {
        Icon(
            Icons.AutoMirrored.Filled.Send,
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize),
        )
      }
      Spacer(Modifier.width(ButtonDefaults.IconSpacing))
      Text(stringResource(R.string.date_picker_send_request))
    } else {
      Text(stringResource(R.string.date_picker_confirm_dates))
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun DateRangePickerRequestPreview() {
  PolyLoopTheme {
    DateRangePickerContent(
        window = LocalDate.of(2026, 10, 6)..LocalDate.of(2026, 10, 20),
        disabledDates = setOf(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 11)),
        mode = DateRangePickerMode.Request(pricePerDay = 15, balance = 120),
        selection =
            DateRangeSelection(
                start = LocalDate.of(2026, 10, 15),
                end = LocalDate.of(2026, 10, 17),
            ),
        onDayClick = {},
        onConfirm = { _, _ -> },
        today = LocalDate.of(2026, 10, 1),
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun DateRangePickerAvailabilityPreview() {
  val today = LocalDate.of(2026, 10, 13)
  PolyLoopTheme {
    DateRangePickerContent(
        window = today..today.plusYears(1),
        disabledDates = emptySet(),
        mode = DateRangePickerMode.Availability,
        selection = DateRangeSelection(start = today.plusDays(2), end = today.plusDays(12)),
        onDayClick = {},
        onConfirm = { _, _ -> },
        today = today,
    )
  }
}
