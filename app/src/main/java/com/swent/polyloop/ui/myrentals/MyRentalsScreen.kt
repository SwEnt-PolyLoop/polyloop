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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import com.swent.polyloop.ui.theme.PolyLoopTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The My rentals screen, showing what the signed-in user borrows and lends (Figma frame 10). */
@Composable
fun MyRentalsScreen(viewModel: MyRentalsViewModel, onRentalClick: (String) -> Unit = {}) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  MyRentalsContent(
      uiState = uiState,
      onTabSelected = viewModel::selectTab,
      onRentalClick = onRentalClick,
      onRetry = viewModel::retry,
  )
}

/**
 * Stateless My rentals content: the title and the Borrowing / Lending tabs, then a loading
 * indicator, an error with a Retry button, the empty state of the tab or its rows, depending on
 * [uiState].
 */
@Composable
fun MyRentalsContent(
    uiState: MyRentalsUiState,
    onTabSelected: (RentalsTab) -> Unit,
    onRentalClick: (String) -> Unit,
    onRetry: () -> Unit,
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
      uiState.isLoading ->
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.testTag(C.Tag.my_rentals_loading))
          }
      uiState.errorMsg != null -> MyRentalsError(errorMsg = uiState.errorMsg, onRetry = onRetry)
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
      else ->
          LazyColumn(
              modifier = Modifier.fillMaxWidth().testTag(C.Tag.my_rentals_list),
              contentPadding = PaddingValues(16.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            items(uiState.selectedRows, key = { it.rentalId }) { row ->
              RentalRowCard(
                  row = row,
                  tab = uiState.selectedTab,
                  onClick = { onRentalClick(row.rentalId) },
              )
            }
          }
    }
  }
}

/** One rental: the item, the other person, the return date, the location and the status badge. */
@Composable
private fun RentalRowCard(row: RentalRow, tab: RentalsTab, onClick: () -> Unit) {
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
      Text(
          text = stringResource(R.string.my_rentals_return_by, formatDay(row.returnDate)),
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.testTag(C.Tag.my_rentals_row_return),
      )
      if (row.location.isNotBlank()) {
        Text(
            text = row.location,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(C.Tag.my_rentals_row_location),
        )
      }
    }
  }
}

/** The status of a rental as a small coloured label. */
@Composable
private fun StatusBadge(status: RentalStatus) {
  val (container, content) = status.badgeColors()
  Surface(
      shape = CircleShape,
      color = container,
      contentColor = content,
      modifier = Modifier.padding(start = 8.dp),
  ) {
    Text(
        text = stringResource(status.labelRes),
        style = MaterialTheme.typography.labelMedium,
        modifier =
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                .testTag(C.Tag.my_rentals_row_status),
    )
  }
}

/** The load error ([errorMsg], or a generic text if it is blank) and a Retry button. */
@Composable
private fun MyRentalsError(errorMsg: String, onRetry: () -> Unit) {
  Column(
      modifier = Modifier.fillMaxSize().padding(16.dp),
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

/** A day written briefly, e.g. "22 Oct". */
internal fun formatDay(day: LocalDate, locale: Locale = Locale.getDefault()): String =
    day.format(DateTimeFormatter.ofPattern("d MMM", locale))

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

/** "Lent by …" on a borrowed item, "Borrowed by …" on a lent one. */
@get:StringRes
private val RentalsTab.personRes: Int
  get() =
      when (this) {
        RentalsTab.BORROWING -> R.string.my_rentals_lent_by
        RentalsTab.LENDING -> R.string.my_rentals_borrowed_by
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
