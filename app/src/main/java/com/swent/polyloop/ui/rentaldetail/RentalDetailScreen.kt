// Made with Claude.

package com.swent.polyloop.ui.rentaldetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swent.polyloop.R
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.listingdetail.formatRangeEnds

/**
 * The Rental detail screen for a rental request: the lender reviews it and accepts or declines
 * (Figma frame 12). Leaves through [onAnswered] once the answer is saved.
 */
@Composable
fun RentalDetailScreen(viewModel: RentalDetailViewModel, onAnswered: () -> Unit = {}) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  LaunchedEffect(uiState.isAnswered) { if (uiState.isAnswered) onAnswered() }
  RentalDetailContent(
      uiState = uiState,
      onPickupAddressChange = viewModel::onPickupAddressChange,
      onReturnAddressChange = viewModel::onReturnAddressChange,
      onDepositChange = viewModel::onDepositChange,
      onAccept = viewModel::accept,
      onDecline = viewModel::decline,
      onRetry = viewModel::retry,
  )
}

/**
 * Stateless content of the Rental detail screen: a loading indicator, an error with Retry, the
 * request to answer with its form, or a note when there is nothing to answer.
 */
@Composable
fun RentalDetailContent(
    uiState: RentalDetailUiState,
    onPickupAddressChange: (String) -> Unit,
    onReturnAddressChange: (String) -> Unit,
    onDepositChange: (String) -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onRetry: () -> Unit,
) {
  Column(
      modifier =
          Modifier.fillMaxSize()
              .background(MaterialTheme.colorScheme.background)
              .padding(16.dp)
              .verticalScroll(rememberScrollState())
              .testTag(C.Tag.rental_detail_screen),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(
        text = stringResource(R.string.rental_detail_title),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
    )
    val rental = uiState.rental
    when {
      uiState.isLoading ->
          Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.testTag(C.Tag.rental_detail_loading))
          }
      uiState.errorMsg != null && rental == null ->
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text =
                    uiState.errorMsg.ifBlank {
                      stringResource(R.string.rental_detail_error_fallback)
                    },
                modifier = Modifier.testTag(C.Tag.rental_detail_error),
            )
            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier.testTag(C.Tag.rental_detail_retry_button),
            ) {
              Text(stringResource(R.string.rental_detail_retry))
            }
          }
      rental == null -> Unit
      !uiState.isRequestToAnswer ->
          Text(
              text = stringResource(R.string.rental_detail_no_action),
              textAlign = TextAlign.Center,
              modifier = Modifier.fillMaxWidth().testTag(C.Tag.rental_detail_no_action),
          )
      else -> {
        Text(
            text = rental.listingTitle,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.testTag(C.Tag.rental_detail_item_title),
        )
        val (from, to) = formatRangeEnds(rental.startDate, rental.endDate)
        Text(
            text = stringResource(R.string.rental_detail_dates, from, to),
            modifier = Modifier.testTag(C.Tag.rental_detail_dates),
        )
        Text(
            text =
                stringResource(
                    R.string.rental_detail_price,
                    rental.totalPrice,
                    rental.endDate.toEpochDay() - rental.startDate.toEpochDay() + 1,
                ),
            modifier = Modifier.testTag(C.Tag.rental_detail_price),
        )
        OutlinedTextField(
            value = uiState.pickupAddress,
            onValueChange = onPickupAddressChange,
            label = { Text(stringResource(R.string.rental_detail_pickup_label)) },
            modifier = Modifier.fillMaxWidth().testTag(C.Tag.rental_detail_pickup_field),
        )
        OutlinedTextField(
            value = uiState.returnAddress,
            onValueChange = onReturnAddressChange,
            label = { Text(stringResource(R.string.rental_detail_return_label)) },
            modifier = Modifier.fillMaxWidth().testTag(C.Tag.rental_detail_return_field),
        )
        OutlinedTextField(
            value = uiState.deposit,
            onValueChange = onDepositChange,
            label = { Text(stringResource(R.string.rental_detail_deposit_label)) },
            modifier = Modifier.fillMaxWidth().testTag(C.Tag.rental_detail_deposit_field),
        )
        uiState.answerErrorMsg?.let {
          Text(
              text = it.ifBlank { stringResource(R.string.rental_detail_answer_error_fallback) },
              color = MaterialTheme.colorScheme.error,
              modifier = Modifier.testTag(C.Tag.rental_detail_answer_error),
          )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(
              onClick = onDecline,
              enabled = uiState.canDecline,
              modifier = Modifier.testTag(C.Tag.rental_detail_decline_button),
          ) {
            Text(stringResource(R.string.rental_detail_decline))
          }
          Button(
              onClick = onAccept,
              enabled = uiState.canAccept,
              modifier = Modifier.testTag(C.Tag.rental_detail_accept_button),
          ) {
            Text(stringResource(R.string.rental_detail_accept))
          }
        }
      }
    }
  }
}
