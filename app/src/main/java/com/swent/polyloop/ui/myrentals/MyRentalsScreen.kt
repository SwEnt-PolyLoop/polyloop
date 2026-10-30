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
import com.swent.polyloop.ui.components.badgeColors
import com.swent.polyloop.ui.components.formatDay
import com.swent.polyloop.ui.listingdetail.formatRangeEnds
import com.swent.polyloop.ui.theme.PolyLoopTheme
import java.time.LocalDate

/**
 * The My rentals screen, showing what the signed-in user borrows and lends (Figma frames 10, 11).
 * [onReviewRequest] receives the rental id of a request whose "Review request" button is tapped,
 * and [onOpenChat] the one of a tapped Borrowing request. [onRentalClick] receives the one of any
 * tapped CURRENT card.
 */
@Composable
fun MyRentalsScreen(
    viewModel: MyRentalsViewModel,
    onRentalClick: (String) -> Unit = {},
    onReviewRequest: (String) -> Unit = {},
    onOpenChat: (String) -> Unit = {},
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  MyRentalsContent(
      uiState = uiState,
      onTabSelected = viewModel::selectTab,
      onRentalClick = onRentalClick,
      onRetry = viewModel::retry,
      onReviewRequest = onReviewRequest,
      onOpenChat = onOpenChat,
  )
}

/**
 * Stateless My rentals content: the title and the Borrowing / Lending tabs, then a loading
 * indicator, an error with a Retry button, the empty state of the tab or its rows, depending on
 * [uiState]. Both tabs show their rows in two sections, REQUESTS and CURRENT, each only when it has
 * rows.
 */
@Composable
fun MyRentalsContent(
    uiState: MyRentalsUiState,
    onTabSelected: (RentalsTab) -> Unit,
    onRentalClick: (String) -> Unit,
    onRetry: () -> Unit,
    onReviewRequest: (String) -> Unit = {},
    onOpenChat: (String) -> Unit = {},
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
        val tab = uiState.selectedTab
        val isBorrowing = tab == RentalsTab.BORROWING
        val requests = if (isBorrowing) uiState.borrowingRequests else uiState.lendingRequests
        val current = if (isBorrowing) uiState.borrowingCurrent else uiState.lendingCurrent
        LazyColumn(
            modifier = Modifier.fillMaxWidth().testTag(C.Tag.my_rentals_list),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          if (requests.isNotEmpty()) {
            item(key = C.Tag.my_rentals_section_requests) {
              SectionHeader(
                  R.string.my_rentals_section_requests,
                  C.Tag.my_rentals_section_requests,
              )
            }
            items(requests, key = { it.rentalId }) { row ->
              // Only the request card differs: the lender has a request to answer, while the
              // borrower can only wait, so a plain card is enough. It opens the Chat because
              // that is what is left to do before acceptance: agree on the deposit and meeting
              // point.
              if (isBorrowing) {
                RentalCard(
                    row = row,
                    personLine = row.personLine(tab),
                    today = today,
                    onClick = { onOpenChat(row.rentalId) },
                )
              } else {
                RequestCard(row = row, onReviewRequest = { onReviewRequest(row.rentalId) })
              }
            }
          }
          if (current.isNotEmpty()) {
            item(key = C.Tag.my_rentals_section_current) {
              SectionHeader(R.string.my_rentals_section_current, C.Tag.my_rentals_section_current)
            }
            items(current, key = { it.rentalId }) { row ->
              RentalCard(
                  row = row,
                  personLine = row.personLine(tab),
                  today = today,
                  onClick = { onRentalClick(row.rentalId) },
              )
            }
          }
        }
      }
    }
  }
}

/** The title of a section of My rentals, e.g. "REQUESTS". */
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
 * button calling [onReviewRequest]. Only the button reacts: the card itself is not clickable.
 */
@Composable
private fun RequestCard(row: RentalRow, onReviewRequest: () -> Unit) {
  val (from, to) = formatRangeEnds(row.startDate, row.returnDate)
  OutlinedCard(
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
                  stringResource(R.string.my_rentals_date_range, from, to),
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
 * A rental on either tab (Figma frames 10, 11), for every row but the lender's requests: the
 * listing photo, the title and the status badge, [personLine], and a line saying what comes next
 * (see [currentLine]). Tapping it calls [onClick].
 */
@Composable
private fun RentalCard(row: RentalRow, personLine: String, today: LocalDate, onClick: () -> Unit) {
  OutlinedCard(
      onClick = onClick,
      modifier = Modifier.fillMaxWidth().testTag(C.Tag.my_rentals_row_ + row.rentalId),
  ) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      RentalCardTop(row = row, isNewRequest = false, personLine = personLine)
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
 * The top of a card: the listing photo (a grey box when there is none), the title with the status
 * badge ("New request" with [isNewRequest]), and [personLine] under them.
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
 * The person line of a [RentalCard]. On Lending the card is about the borrower, so the name stands
 * alone; on Borrowing the item comes from someone, so it reads "from Loca".
 */
@Composable
private fun RentalRow.personLine(tab: RentalsTab): String =
    when (tab) {
      RentalsTab.BORROWING -> stringResource(R.string.my_rentals_from, personOrPlaceholder())
      RentalsTab.LENDING -> personOrPlaceholder()
    }

/**
 * What comes next for a rental, on both tabs: the dates while requested, "Pickup 22 Oct · area"
 * once accepted, "Return by 9 Oct · area" while active or overdue, the drop-off area alone while
 * disputed, frozen or lost, "Closed 2 Oct" once closed, and null when returned, declined or
 * cancelled. "· area" is left out when the area is blank, and the line is null when nothing is
 * left.
 */
@Composable
private fun RentalRow.currentLine(today: LocalDate): String? {
  val date =
      when (status) {
        RentalStatus.REQUESTED -> {
          val (from, to) = formatRangeEnds(startDate, returnDate)
          stringResource(R.string.my_rentals_date_range, from, to)
        }
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
        // Rental has no closing date, and adding one is a data model change, so the last day of
        // the rental stands in for it for now.
        RentalStatus.CLOSED ->
            stringResource(
                R.string.my_rentals_closed_on,
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

/** Statuses whose [RentalCard] names the area where the item is handed over. */
private val STATUSES_WITH_AREA =
    setOf(
        RentalStatus.ACCEPTED,
        RentalStatus.ACTIVE,
        RentalStatus.OVERDUE,
        RentalStatus.DISPUTED,
        RentalStatus.FROZEN,
        RentalStatus.LOST,
    )

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
      )
  PolyLoopTheme {
    MyRentalsContent(
        uiState = MyRentalsUiState(borrowingRequests = listOf(row), isLoading = false),
        onTabSelected = {},
        onRentalClick = {},
        onRetry = {},
    )
  }
}
