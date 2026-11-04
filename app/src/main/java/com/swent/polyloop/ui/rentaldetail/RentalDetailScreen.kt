// Made with Claude.

package com.swent.polyloop.ui.rentaldetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swent.polyloop.R
import com.swent.polyloop.model.rental.RentalStatus
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.listingdetail.formatRangeEnds

/**
 * The Rental detail screen. For a request the lender answers, it shows the request and lets the
 * lender accept it, with the deposit and both addresses, or decline it. [onAnswered] is called once
 * the request was answered, so the navigation can leave the screen.
 */
@Composable
fun RentalDetailScreen(
    viewModel: RentalDetailViewModel,
    onAnswered: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  LaunchedEffect(uiState.answered) {
    if (uiState.answered) {
      viewModel.onAnsweredHandled()
      onAnswered()
    }
  }
  RentalDetailContent(
      uiState = uiState,
      onDepositChange = viewModel::updateDeposit,
      onPickupChange = viewModel::updatePickupAddress,
      onReturnChange = viewModel::updateReturnAddress,
      onAccept = viewModel::accept,
      onDecline = viewModel::decline,
      onRetry = viewModel::retry,
      modifier = modifier,
  )
}

/** The body of the screen, without the ViewModel, so it can be tested with plain state. */
@Composable
internal fun RentalDetailContent(
    uiState: RentalDetailUiState,
    onDepositChange: (String) -> Unit,
    onPickupChange: (String) -> Unit,
    onReturnChange: (String) -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Column(
      modifier =
          modifier
              .fillMaxWidth()
              .verticalScroll(rememberScrollState())
              .padding(16.dp)
              .testTag(C.Tag.rental_detail_screen),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    val loadError = uiState.errorMsg
    when {
      uiState.isLoading ->
          CircularProgressIndicator(modifier = Modifier.testTag(C.Tag.rental_detail_loading))
      loadError != null -> LoadError(loadError, onRetry = onRetry)
      uiState.notFound ->
          Text(
              stringResource(R.string.rental_detail_not_found),
              modifier = Modifier.testTag(C.Tag.rental_detail_not_found),
          )
      else ->
          RequestBody(
              uiState,
              onDepositChange,
              onPickupChange,
              onReturnChange,
              onAccept,
              onDecline,
          )
    }
  }
}

@Composable
private fun LoadError(message: String, onRetry: () -> Unit) {
  Text(
      if (message.isBlank()) stringResource(R.string.rental_detail_load_error) else message,
      modifier = Modifier.testTag(C.Tag.rental_detail_load_error),
  )
  OutlinedButton(onClick = onRetry, modifier = Modifier.testTag(C.Tag.rental_detail_retry_button)) {
    Text(stringResource(R.string.rental_detail_retry))
  }
}

@Composable
private fun RequestBody(
    uiState: RentalDetailUiState,
    onDepositChange: (String) -> Unit,
    onPickupChange: (String) -> Unit,
    onReturnChange: (String) -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
  Text(uiState.itemTitle, style = MaterialTheme.typography.headlineSmall)
  val start = uiState.startDate
  val end = uiState.endDate
  if (start != null && end != null) {
    val (from, to) = formatRangeEnds(start, end)
    Text(stringResource(R.string.listing_detail_date_range, from, to))
  }
  Text(stringResource(R.string.rental_detail_total, uiState.rentalPrice))
  Text(
      stringResource(R.string.rental_detail_borrower_label),
      style = MaterialTheme.typography.labelLarge,
  )
  val borrower = uiState.otherPerson?.name.orEmpty()
  Text(borrower.ifBlank { stringResource(R.string.rental_detail_borrower_placeholder) })

  when {
    !uiState.isSupported || uiState.status != RentalStatus.REQUESTED ->
        Text(
            stringResource(R.string.rental_detail_answered),
            modifier = Modifier.testTag(C.Tag.rental_detail_answered),
        )
    !uiState.isLender ->
        Text(
            stringResource(R.string.rental_detail_not_lender),
            modifier = Modifier.testTag(C.Tag.rental_detail_not_lender),
        )
    else ->
        LenderAnswer(uiState, onDepositChange, onPickupChange, onReturnChange, onAccept, onDecline)
  }
}

@Composable
private fun LenderAnswer(
    uiState: RentalDetailUiState,
    onDepositChange: (String) -> Unit,
    onPickupChange: (String) -> Unit,
    onReturnChange: (String) -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
  OutlinedTextField(
      value = uiState.depositInput,
      onValueChange = onDepositChange,
      label = { Text(stringResource(R.string.rental_detail_deposit_label)) },
      supportingText = { Text(stringResource(R.string.rental_detail_deposit_hint)) },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      enabled = !uiState.isSubmitting,
      singleLine = true,
      modifier = Modifier.fillMaxWidth().testTag(C.Tag.rental_detail_deposit_field),
  )
  OutlinedTextField(
      value = uiState.pickupAddress,
      onValueChange = onPickupChange,
      label = { Text(stringResource(R.string.rental_detail_pickup_label)) },
      enabled = !uiState.isSubmitting,
      modifier = Modifier.fillMaxWidth().testTag(C.Tag.rental_detail_pickup_field),
  )
  OutlinedTextField(
      value = uiState.returnAddress,
      onValueChange = onReturnChange,
      label = { Text(stringResource(R.string.rental_detail_return_label)) },
      enabled = !uiState.isSubmitting,
      modifier = Modifier.fillMaxWidth().testTag(C.Tag.rental_detail_return_field),
  )

  if (uiState.overlapCount > 0) {
    Text(
        pluralStringResource(
            R.plurals.rental_detail_overlap,
            uiState.overlapCount,
            uiState.overlapCount,
        ),
        modifier = Modifier.testTag(C.Tag.rental_detail_overlap),
    )
  }

  uiState.actionError?.let { message ->
    Text(
        if (message.isBlank()) stringResource(R.string.rental_detail_action_error) else message,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.testTag(C.Tag.rental_detail_action_error),
    )
  }

  Spacer(Modifier.height(4.dp))
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Button(
        onClick = onAccept,
        enabled = uiState.canAccept,
        modifier = Modifier.testTag(C.Tag.rental_detail_accept_button),
    ) {
      if (uiState.isSubmitting) {
        CircularProgressIndicator(modifier = Modifier.height(18.dp))
      } else {
        Text(stringResource(R.string.rental_detail_accept))
      }
    }
    OutlinedButton(
        onClick = onDecline,
        enabled = uiState.canDecline,
        modifier = Modifier.testTag(C.Tag.rental_detail_decline_button),
    ) {
      Text(stringResource(R.string.rental_detail_decline))
    }
  }
}
