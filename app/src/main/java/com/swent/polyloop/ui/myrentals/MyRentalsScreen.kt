// Made with Claude.

package com.swent.polyloop.ui.myrentals

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swent.polyloop.R
import com.swent.polyloop.model.rental.RentalStatus
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.components.ListingPhoto
import com.swent.polyloop.ui.components.formatDay
import com.swent.polyloop.ui.components.formatDayRange
import com.swent.polyloop.ui.theme.PolyLoopTheme
import java.time.LocalDate

/**
 * The My rentals screen, showing what the signed-in user borrows and lends (Figma frame 10).
 * [onReviewRequest] receives the rental id of a request whose "Review request" button is tapped.
 */
@Composable
fun MyRentalsScreen(
    viewModel: MyRentalsViewModel,
    onRentalClick: (String) -> Unit = {},
    onReviewRequest: (String) -> Unit = {},
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  MyRentalsContent(
      uiState = uiState,
      onTabSelected = viewModel::selectTab,
      onRentalClick = onRentalClick,
      onRetry = viewModel::retry,
      onReviewRequest = onReviewRequest,
  )
}

/**
 * Stateless My rentals content: the title and the Borrowing / Lending tabs, then a loading
 * indicator, an error with a Retry button, the empty state of the tab or its rows, depending on
 * [uiState]. Lending shows its rows in two sections, REQUESTS and CURRENT, each only when it has
 * rows.
 */
@Composable
fun MyRentalsContent(
    uiState: MyRentalsUiState,
    onTabSelected: (RentalsTab) -> Unit,
    onRentalClick: (String) -> Unit,
    onRetry: () -> Unit,
    onReviewRequest: (String) -> Unit = {},
    today: LocalDate = LocalDate.now(),
) {
  Column(
      modifier =
          Modifier.fillMaxSize()
              .background(MaterialTheme.colorScheme.background)
              .testTag(C.Tag.my_rentals_screen)
  ) {
    Text(
        text = stringResource(R.string.my_rentals_title),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp).testTag(C.Tag.my_rentals_title),
    )
    PrimaryTabRow(selectedTabIndex = uiState.selectedTab.ordinal) {
      RentalsTab.entries.forEach { tab ->
        Tab(
            selected = tab == uiState.selectedTab,
            onClick = { onTabSelected(tab) },
            text = { Text(stringResource(tab.labelRes)) },
            modifier = Modifier.testTag(tab.testTag),
        )
      }
    }
    when {
      uiState.isLoading && uiState.selectedRows.isEmpty() ->
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.testTag(C.Tag.my_rentals_loading))
          }
      uiState.errorMsg != null && uiState.selectedRows.isEmpty() ->
          MyRentalsError(
              errorMsg = uiState.errorMsg,
              onRetry = onRetry,
              modifier = Modifier.fillMaxSize(),
          )
      uiState.selectedRows.isEmpty() ->
          Box(
              modifier = Modifier.fillMaxSize().padding(16.dp),
              contentAlignment = Alignment.Center,
          ) {
            Text(
                text = stringResource(uiState.selectedTab.emptyRes),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag(C.Tag.my_rentals_empty),
            )
          }
      else -> {
        // The last rows stay visible while reloading and under the error, e.g. offline
        // (docs/SCREENS.md)
        if (uiState.isLoading) {
          LinearProgressIndicator(
              modifier = Modifier.fillMaxWidth().testTag(C.Tag.my_rentals_loading)
          )
        }
        uiState.errorMsg?.let {
          MyRentalsError(errorMsg = it, onRetry = onRetry, modifier = Modifier.fillMaxWidth())
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().testTag(C.Tag.my_rentals_list),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          if (uiState.selectedTab == RentalsTab.BORROWING) {
            items(uiState.borrowing, key = { it.rentalId }) { row ->
              RentalRowCard(
                  row = row,
                  tab = uiState.selectedTab,
                  today = today,
                  onClick = { onRentalClick(row.rentalId) },
              )
            }
          } else {
            if (uiState.lendingRequests.isNotEmpty()) {
              item(key = C.Tag.my_rentals_section_requests) {
                SectionHeader(
                    R.string.my_rentals_section_requests,
                    C.Tag.my_rentals_section_requests,
                )
              }
              items(uiState.lendingRequests, key = { it.rentalId }) { row ->
                RequestCard(
                    row = row,
                    today = today,
                    onClick = { onRentalClick(row.rentalId) },
                    onReviewRequest = { onReviewRequest(row.rentalId) },
                )
              }
            }
            if (uiState.lendingCurrent.isNotEmpty()) {
              item(key = C.Tag.my_rentals_section_current) {
                SectionHeader(R.string.my_rentals_section_current, C.Tag.my_rentals_section_current)
              }
              items(uiState.lendingCurrent, key = { it.rentalId }) { row ->
                CurrentCard(row = row, today = today, onClick = { onRentalClick(row.rentalId) })
              }
            }
          }
        }
      }
    }
  }
}

/** The title of a section of Lending, e.g. "REQUESTS". */
@Composable
private fun SectionHeader(@StringRes textRes: Int, testTag: String) {
  Text(
      text = stringResource(textRes),
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.testTag(testTag),
  )
}

/**
 * A pending request on one of the user's listings (Figma frame 10): the listing photo, the title
 * and a "New request" badge, the borrower and the dates, the reserved price, and a "Review request"
 * button calling [onReviewRequest]. Tapping the rest of the card calls [onClick].
 */
@Composable
private fun RequestCard(
    row: RentalRow,
    today: LocalDate,
    onClick: () -> Unit,
    onReviewRequest: () -> Unit,
) {
  OutlinedCard(
      onClick = onClick,
      modifier = Modifier.fillMaxWidth().testTag(C.Tag.my_rentals_request_ + row.rentalId),
  ) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      RentalCardTop(
          row = row,
          isNewRequest = true,
          personLine =
              stringResource(
                  R.string.my_rentals_person_and_dates,
                  row.personOrPlaceholder(),
                  formatDayRange(row.startDate, row.returnDate, today),
              ),
      )
      Text(
          text = stringResource(R.string.my_rentals_reserved, row.totalPrice),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.testTag(C.Tag.my_rentals_row_reserved),
      )
      Button(
          onClick = onReviewRequest,
          modifier = Modifier.fillMaxWidth().testTag(C.Tag.my_rentals_review_request_button),
      ) {
        Text(stringResource(R.string.my_rentals_review_request))
      }
    }
  }
}

/**
 * Any other rental the user lends (Figma frame 10): the listing photo, the title and the status
 * badge, the borrower, and a line saying what comes next (see [currentLine]). Tapping it calls
 * [onClick].
 */
@Composable
private fun CurrentCard(row: RentalRow, today: LocalDate, onClick: () -> Unit) {
  OutlinedCard(
      onClick = onClick,
      modifier = Modifier.fillMaxWidth().testTag(C.Tag.my_rentals_row_ + row.rentalId),
  ) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      RentalCardTop(row = row, isNewRequest = false, personLine = row.personOrPlaceholder())
      row.currentLine(today)?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(C.Tag.my_rentals_row_line),
        )
      }
    }
  }
}

/**
 * The top of a Lending card: the listing photo (a grey box when there is none), the title with the
 * status badge ("New request" with [isNewRequest]), and [personLine] under them.
 */
@Composable
private fun RentalCardTop(row: RentalRow, isNewRequest: Boolean, personLine: String) {
  Row {
    ListingPhoto(
        url = row.listingPhotoUrl.ifBlank { null },
        contentDescription = null,
        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
    )
    Column(
        modifier = Modifier.weight(1f).padding(start = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Row {
        Text(
            text = row.itemTitle,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).testTag(C.Tag.my_rentals_row_title),
        )
        StatusBadge(row.status, isNewRequest = isNewRequest)
      }
      Text(
          text = personLine,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.testTag(C.Tag.my_rentals_row_person),
      )
    }
  }
}

/** The other person's name, or the placeholder when it is missing. */
@Composable
private fun RentalRow.personOrPlaceholder(): String = otherPersonName.ifBlank {
  stringResource(R.string.my_rentals_unknown_person)
}

/**
 * What comes next for a lent rental: "Pickup 22 Oct · area" once accepted, "Return by 9 Oct · area"
 * while active or overdue, the drop-off area alone while disputed, frozen or lost, and null once it
 * is over. "· area" is left out when the area is blank, and the line is null when nothing is left.
 */
@Composable
private fun RentalRow.currentLine(today: LocalDate): String? {
  val date =
      when (status) {
        RentalStatus.ACCEPTED ->
            stringResource(
                R.string.my_rentals_pickup_on,
                formatDay(startDate, withYear = startDate.year != today.year),
            )
        RentalStatus.ACTIVE,
        RentalStatus.OVERDUE ->
            stringResource(
                R.string.my_rentals_return_by,
                formatDay(returnDate, withYear = returnDate.year != today.year),
            )
        else -> null
      }
  // location is the pickup area while accepted and the drop-off area once picked up.
  val area = location.takeIf { status in STATUSES_WITH_AREA && it.isNotBlank() }
  return when {
    date != null && area != null -> stringResource(R.string.my_rentals_date_and_area, date, area)
    else -> date ?: area
  }
}

/** Statuses whose Current card names the area where the item is handed over. */
private val STATUSES_WITH_AREA =
    setOf(
        RentalStatus.ACCEPTED,
        RentalStatus.ACTIVE,
        RentalStatus.OVERDUE,
        RentalStatus.DISPUTED,
        RentalStatus.FROZEN,
        RentalStatus.LOST,
    )

/** One rental: the item, the other person, the return date, the location and the status badge. */
@Composable
private fun RentalRowCard(row: RentalRow, tab: RentalsTab, today: LocalDate, onClick: () -> Unit) {
  OutlinedCard(
      onClick = onClick,
      modifier = Modifier.fillMaxWidth().testTag(C.Tag.my_rentals_row_ + row.rentalId),
  ) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = row.itemTitle,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).testTag(C.Tag.my_rentals_row_title),
        )
        StatusBadge(row.status)
      }
      val person =
          row.otherPersonName.ifBlank { stringResource(R.string.my_rentals_unknown_person) }
      Text(
          text = stringResource(tab.personRes, person),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.testTag(C.Tag.my_rentals_row_person),
      )
      if (row.showReturnDate) {
        val returnDay = formatDay(row.returnDate, withYear = row.returnDate.year != today.year)
        Text(
            text = stringResource(R.string.my_rentals_return_by, returnDay),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag(C.Tag.my_rentals_row_return),
        )
      }
      if (row.location.isNotBlank()) {
        val locationRes =
            if (row.isPickupArea) R.string.my_rentals_pickup_at else R.string.my_rentals_drop_off_at
        Text(
            text = stringResource(locationRes, row.location),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(C.Tag.my_rentals_row_location),
        )
      }
    }
  }
}

/** The status of a rental as a small coloured label, or "New request" with [isNewRequest]. */
@Composable
private fun StatusBadge(status: RentalStatus, isNewRequest: Boolean = false) {
  val colors = MaterialTheme.colorScheme
  val (container, content) =
      if (isNewRequest) colors.primaryContainer to colors.onPrimaryContainer
      else status.badgeColors()
  Surface(
      shape = CircleShape,
      color = container,
      contentColor = content,
      modifier = Modifier.padding(start = 8.dp),
  ) {
    Text(
        text =
            stringResource(
                if (isNewRequest) R.string.my_rentals_badge_new_request else status.labelRes
            ),
        style = MaterialTheme.typography.labelMedium,
        modifier =
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                .testTag(C.Tag.my_rentals_row_status),
    )
  }
}

/**
 * The load error ([errorMsg], or a generic text if it is blank) and a Retry button: on the whole
 * screen when there are no rows, above them otherwise.
 */
@Composable
private fun MyRentalsError(errorMsg: String, onRetry: () -> Unit, modifier: Modifier) {
  Column(
      modifier = modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
        text = errorMsg.ifBlank { stringResource(R.string.my_rentals_error_fallback) },
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.testTag(C.Tag.my_rentals_error),
    )
    Button(onClick = onRetry, modifier = Modifier.testTag(C.Tag.my_rentals_retry_button)) {
      Text(stringResource(R.string.my_rentals_retry))
    }
  }
}

@get:StringRes
private val RentalsTab.labelRes: Int
  get() =
      when (this) {
        RentalsTab.BORROWING -> R.string.my_rentals_tab_borrowing
        RentalsTab.LENDING -> R.string.my_rentals_tab_lending
      }

@get:StringRes
private val RentalsTab.emptyRes: Int
  get() =
      when (this) {
        RentalsTab.BORROWING -> R.string.my_rentals_empty_borrowing
        RentalsTab.LENDING -> R.string.my_rentals_empty_lending
      }

/** "Lender: …" on a borrowed item, "Borrower: …" on a lent one, true for every status. */
@get:StringRes
private val RentalsTab.personRes: Int
  get() =
      when (this) {
        RentalsTab.BORROWING -> R.string.my_rentals_lender
        RentalsTab.LENDING -> R.string.my_rentals_borrower
      }

private val RentalsTab.testTag: String
  get() =
      when (this) {
        RentalsTab.BORROWING -> C.Tag.my_rentals_tab_borrowing
        RentalsTab.LENDING -> C.Tag.my_rentals_tab_lending
      }

@get:StringRes
internal val RentalStatus.labelRes: Int
  get() =
      when (this) {
        RentalStatus.REQUESTED -> R.string.rental_status_requested
        RentalStatus.ACCEPTED -> R.string.rental_status_accepted
        RentalStatus.ACTIVE -> R.string.rental_status_active
        RentalStatus.RETURNED -> R.string.rental_status_returned
        RentalStatus.CLOSED -> R.string.rental_status_closed
        RentalStatus.DECLINED -> R.string.rental_status_declined
        RentalStatus.CANCELLED -> R.string.rental_status_cancelled
        RentalStatus.OVERDUE -> R.string.rental_status_overdue
        RentalStatus.LOST -> R.string.rental_status_lost
        RentalStatus.DISPUTED -> R.string.rental_status_disputed
        RentalStatus.FROZEN -> R.string.rental_status_frozen
      }

/** Badge colours: waiting, in progress, a problem, or finished. */
@Composable
private fun RentalStatus.badgeColors(): Pair<Color, Color> {
  val colors = MaterialTheme.colorScheme
  return when (this) {
    RentalStatus.REQUESTED -> colors.secondaryContainer to colors.onSecondaryContainer
    RentalStatus.ACCEPTED,
    RentalStatus.ACTIVE -> colors.primaryContainer to colors.onPrimaryContainer
    RentalStatus.OVERDUE,
    RentalStatus.LOST,
    RentalStatus.DISPUTED,
    RentalStatus.FROZEN -> colors.errorContainer to colors.onErrorContainer
    RentalStatus.RETURNED,
    RentalStatus.CLOSED,
    RentalStatus.DECLINED,
    RentalStatus.CANCELLED -> colors.surfaceVariant to colors.onSurfaceVariant
  }
}

@Preview(showBackground = true)
@Composable
private fun MyRentalsContentPreview() {
  val row =
      RentalRow(
          rentalId = "1",
          itemTitle = "2-person camping tent",
          otherPersonName = "Loca",
          startDate = LocalDate.of(2026, 10, 20),
          returnDate = LocalDate.of(2026, 10, 22),
          location = "Renens",
          status = RentalStatus.REQUESTED,
          isPickupArea = true,
          showReturnDate = true,
      )
  PolyLoopTheme {
    MyRentalsContent(
        uiState = MyRentalsUiState(borrowing = listOf(row), isLoading = false),
        onTabSelected = {},
        onRentalClick = {},
        onRetry = {},
    )
  }
}
