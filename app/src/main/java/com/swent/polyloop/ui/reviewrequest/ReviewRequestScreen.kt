// Made with Claude.

package com.swent.polyloop.ui.reviewrequest

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swent.polyloop.R
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.components.OfflineBanner
import com.swent.polyloop.ui.listingdetail.formatRangeEnds
import com.swent.polyloop.ui.rentaldetail.InfoRow
import com.swent.polyloop.ui.rentaldetail.OtherPerson
import com.swent.polyloop.ui.rentaldetail.PersonCard
import com.swent.polyloop.ui.rentaldetail.RentalDetailError
import com.swent.polyloop.ui.rentaldetail.RentalNotFound
import com.swent.polyloop.ui.theme.PolyLoopTheme
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The Review request screen (Figma frame 12.10), where the lender answers a request with no offer
 * yet. The app's top bar is drawn by the navigation scaffold, not here.
 *
 * @param viewModel holds the request and the form.
 * @param onOfferSent called once the offer is sent.
 * @param onDeclined called once the request is declined.
 * @param onChatClick opens the rental's chat.
 * @param onPersonClick opens the borrower's profile.
 */
@Composable
fun ReviewRequestScreen(
    viewModel: ReviewRequestViewModel,
    onOfferSent: () -> Unit,
    onDeclined: () -> Unit,
    onChatClick: () -> Unit,
    onPersonClick: (uid: String) -> Unit,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  LaunchedEffect(uiState.outcome) {
    when (uiState.outcome) {
      ReviewOutcome.OFFER_SENT -> onOfferSent()
      ReviewOutcome.DECLINED -> onDeclined()
      null -> Unit
    }
  }
  // Split from the stateless content below so that tests and previews can set the state directly.
  ReviewRequestContent(
      uiState = uiState,
      onRetry = viewModel::retry,
      onDepositChange = viewModel::onDepositChange,
      onPickupAddressChange = viewModel::onPickupAddressChange,
      onReturnAddressChange = viewModel::onReturnAddressChange,
      onSendOffer = viewModel::sendOffer,
      onDecline = viewModel::decline,
      onChatClick = onChatClick,
      onPersonClick = onPersonClick,
  )
}

/**
 * Stateless Review request content: a loading indicator, an error with Retry, a not-found message,
 * a note when the request cannot be answered here, or the form, depending on [uiState]. [today]
 * decides whether the year is written; tests pass a fixed day.
 */
@Composable
fun ReviewRequestContent(
    uiState: ReviewRequestUiState,
    onRetry: () -> Unit,
    onDepositChange: (String) -> Unit,
    onPickupAddressChange: (String) -> Unit,
    onReturnAddressChange: (String) -> Unit,
    onSendOffer: () -> Unit,
    onDecline: () -> Unit,
    onChatClick: () -> Unit,
    onPersonClick: (uid: String) -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
) {
  Column(
      modifier =
          modifier
              .fillMaxSize()
              .background(MaterialTheme.colorScheme.background)
              .testTag(C.Tag.review_request_screen)
  ) {
    if (uiState.isOffline) OfflineBanner()
    when {
      uiState.isLoading ->
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.testTag(C.Tag.review_request_loading))
          }
      uiState.errorMsg != null -> RentalDetailError(errorMsg = uiState.errorMsg, onRetry = onRetry)
      uiState.notFound -> RentalNotFound()
      !uiState.canAnswer -> AlreadyAnswered(title = uiState.itemTitle)
      else -> {
        Column(
            modifier =
                Modifier.weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Header(uiState = uiState, today = today)
          uiState.borrower?.let { person ->
            PersonCard(
                person = person,
                onClick = { onPersonClick(person.uid) },
                onChatClick = onChatClick,
            )
          }
          PriceCard(uiState = uiState)
          Form(
              uiState = uiState,
              onDepositChange = onDepositChange,
              onPickupAddressChange = onPickupAddressChange,
              onReturnAddressChange = onReturnAddressChange,
          )
        }
        ActionBar(uiState = uiState, onSendOffer = onSendOffer, onDecline = onDecline)
      }
    }
  }
}

/** The title with the "You lend" badge, then "Camping tent · 15 – 17 Oct · 3 days". */
@Composable
private fun Header(uiState: ReviewRequestUiState, today: LocalDate) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
          text = stringResource(R.string.review_request_title),
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.weight(1f),
      )
      Surface(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
      ) {
        Text(
            text = stringResource(R.string.review_request_role),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
      }
    }
    val start = uiState.startDate
    val end = uiState.endDate
    if (start != null && end != null) {
      val locale = LocalConfiguration.current.locales[0]
      val (from, to) = formatRangeEnds(start, end, locale, today)
      val days = ChronoUnit.DAYS.between(start, end).toInt() + 1
      Text(
          text =
              stringResource(
                  R.string.review_request_summary,
                  uiState.itemTitle,
                  stringResource(R.string.listing_detail_date_range, from, to),
                  pluralStringResource(R.plurals.review_request_days, days, days),
              ),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.testTag(C.Tag.review_request_summary),
      )
    }
  }
}

/** The reserved rental price and, when the listing could be read, the suggested deposit. */
@Composable
private fun PriceCard(uiState: ReviewRequestUiState) {
  OutlinedCard(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      InfoRow(
          label = stringResource(R.string.rental_detail_price_label),
          value = stringResource(R.string.rental_detail_price, uiState.rentalPrice),
          valueTag = C.Tag.review_request_price,
      )
      uiState.suggestedDeposit?.let {
        InfoRow(
            label = stringResource(R.string.rental_detail_deposit_label),
            value = stringResource(R.string.listing_card_price, it),
            valueTag = C.Tag.review_request_suggested_deposit,
            isBold = false,
        )
      }
    }
  }
}

/** The deposit and both addresses. The fields are locked while an answer is being sent. */
@Composable
private fun Form(
    uiState: ReviewRequestUiState,
    onDepositChange: (String) -> Unit,
    onPickupAddressChange: (String) -> Unit,
    onReturnAddressChange: (String) -> Unit,
) {
  Field(
      label = stringResource(R.string.review_request_deposit_label),
      value = uiState.deposit,
      onValueChange = onDepositChange,
      enabled = !uiState.isSubmitting,
      tag = C.Tag.review_request_deposit,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      suffix = stringResource(R.string.review_request_deposit_suffix),
      hint = stringResource(R.string.review_request_deposit_hint),
  )
  Field(
      label = stringResource(R.string.review_request_pickup_label),
      value = uiState.pickupAddress,
      onValueChange = onPickupAddressChange,
      enabled = !uiState.isSubmitting,
      tag = C.Tag.review_request_pickup,
      placeholder = stringResource(R.string.review_request_address_placeholder),
  )
  Field(
      label = stringResource(R.string.review_request_return_label),
      value = uiState.returnAddress,
      onValueChange = onReturnAddressChange,
      enabled = !uiState.isSubmitting,
      tag = C.Tag.review_request_return,
      placeholder = stringResource(R.string.review_request_address_placeholder),
      hint = stringResource(R.string.review_request_address_hint),
  )
}

/** A label above a one-line field, with an optional hint under it. */
@Composable
private fun Field(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    tag: String,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    suffix: String? = null,
    placeholder: String? = null,
    hint: String? = null,
) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
    )
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        keyboardOptions = keyboardOptions,
        suffix = suffix?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
    hint?.let {
      Text(
          text = it,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/** The warning about overlapping requests, the last error, then "Decline" and "Send offer". */
@Composable
private fun ActionBar(
    uiState: ReviewRequestUiState,
    onSendOffer: () -> Unit,
    onDecline: () -> Unit,
) {
  HorizontalDivider()
  Column(
      modifier =
          Modifier.fillMaxWidth()
              .background(MaterialTheme.colorScheme.surface)
              .padding(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    if (uiState.overlappingRequests > 0) {
      Text(
          text =
              pluralStringResource(
                  R.plurals.review_request_overlapping,
                  uiState.overlappingRequests,
                  uiState.overlappingRequests,
              ),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.testTag(C.Tag.review_request_overlapping),
      )
    }
    uiState.actionError?.let {
      Text(
          text = it.ifBlank { stringResource(R.string.review_request_action_error) },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.testTag(C.Tag.review_request_action_error),
      )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      OutlinedButton(
          onClick = onDecline,
          enabled = uiState.canDecline,
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier.weight(1f).height(54.dp).testTag(C.Tag.review_request_decline),
      ) {
        Text(stringResource(R.string.review_request_decline))
      }
      Button(
          onClick = onSendOffer,
          enabled = uiState.canSendOffer,
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier.weight(1f).height(54.dp).testTag(C.Tag.review_request_send),
      ) {
        Text(stringResource(R.string.review_request_send))
      }
    }
  }
}

/** Shown when the request has an offer already, was answered, or belongs to another lender. */
@Composable
private fun AlreadyAnswered(title: String) {
  Column(
      modifier = Modifier.fillMaxSize().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = stringResource(R.string.review_request_not_answerable),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag(C.Tag.review_request_not_answerable),
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun ReviewRequestContentPreview() {
  PolyLoopTheme {
    ReviewRequestContent(
        uiState =
            ReviewRequestUiState(
                canAnswer = true,
                itemTitle = "Camping tent",
                startDate = LocalDate.of(2026, 10, 15),
                endDate = LocalDate.of(2026, 10, 17),
                rentalPrice = 45,
                borrower = OtherPerson("b", "Borrower", 4.6, 5),
                suggestedDeposit = 180,
                overlappingRequests = 1,
                deposit = "180",
            ),
        onRetry = {},
        onDepositChange = {},
        onPickupAddressChange = {},
        onReturnAddressChange = {},
        onSendOffer = {},
        onDecline = {},
        onChatClick = {},
        onPersonClick = {},
        today = LocalDate.of(2026, 10, 1),
    )
  }
}
