// Made with Claude.

package com.swent.polyloop.ui.rentaldetail

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swent.polyloop.R
import com.swent.polyloop.model.location.MapPoint
import com.swent.polyloop.model.rental.DepositOffer
import com.swent.polyloop.model.rental.RentalStatus
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.components.PickupMap
import com.swent.polyloop.ui.components.RatingLine
import com.swent.polyloop.ui.components.badgeColors
import com.swent.polyloop.ui.components.formatDay
import com.swent.polyloop.ui.listingdetail.formatRangeEnds
import com.swent.polyloop.ui.myrentals.labelRes
import com.swent.polyloop.ui.navigation.PolyLoopTopBar
import com.swent.polyloop.ui.theme.PolyLoopTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The Rental detail screen, showing the rental loaded by [viewModel]. [onChatClick] opens the
 * rental's chat, [onPersonClick] the other person's profile, [onStartPickupClick] the pickup
 * (Handover) of an accepted rental, [onStartReturnClick] the return (Handover) of an active one,
 * [onViewDisputeClick] the dispute of a disputed one and [onReviewRequestClick] Review request, for
 * the lender of a request with no offer. Confirming a cancel calls [RentalDetailViewModel.cancel].
 * The app's top bar is drawn by the navigation scaffold, not here.
 */
@Composable
fun RentalDetailScreen(
    viewModel: RentalDetailViewModel,
    onChatClick: () -> Unit,
    onPersonClick: (uid: String) -> Unit,
    onStartPickupClick: () -> Unit,
    onStartReturnClick: () -> Unit,
    onViewDisputeClick: () -> Unit,
    onReviewRequestClick: () -> Unit = {},
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  // Reads the server's day again each time the screen comes back (e.g. from Handover), so a screen
  // kept in memory across days does not open Start Pickup or Start Return from an old day.
  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onScreenResumed() }
  // Split from the stateless content below so that tests and previews can set the state directly,
  // without a ViewModel and its repositories.
  RentalDetailContent(
      uiState = uiState,
      onRetry = viewModel::retry,
      onChatClick = onChatClick,
      onPersonClick = onPersonClick,
      onStartPickupClick = onStartPickupClick,
      onStartReturnClick = onStartReturnClick,
      onViewDisputeClick = onViewDisputeClick,
      onCancelClick = viewModel::cancel,
      onReviewRequestClick = onReviewRequestClick,
  )
}

/**
 * Stateless Rental detail content: a loading indicator, an error with a Retry button, a not-found
 * message, a placeholder for a status not built yet, or the Requested, Accepted, Active or Disputed
 * rental, depending on [uiState]. An Accepted rental has "Start Pickup" ([onStartPickupClick])
 * pinned at the bottom, an Active one "Start Return" ([onStartReturnClick]) and a Disputed one
 * "View Dispute" ([onViewDisputeClick]); a request with no offer has "Review request"
 * ([onReviewRequestClick]) for its lender. A Requested or Accepted rental has a cancel row (see
 * [CancelRow]) whose button opens a confirm sheet; "Yes" there calls [onCancelClick]. [today] and
 * [zone] decide how dates are written (e.g. whether the year is shown); tests pass fixed values so
 * their expected texts do not depend on the day they run. [today] is the phone's date, so it is
 * only used to write dates: Start Pickup, Start Return and the cancel window of an accepted rental
 * go by [RentalDetailUiState.serverToday] instead, and stay grey while
 * [RentalDetailUiState.isReadingServerToday]. A request has no cancel window. [locale] decides the
 * language of the month and weekday names; tests pass a fixed one so their expected dates do not
 * depend on the device's language.
 */
@Composable
fun RentalDetailContent(
    uiState: RentalDetailUiState,
    onRetry: () -> Unit,
    onChatClick: () -> Unit,
    onPersonClick: (uid: String) -> Unit,
    onStartPickupClick: () -> Unit,
    onStartReturnClick: () -> Unit,
    onViewDisputeClick: () -> Unit,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier,
    onReviewRequestClick: () -> Unit = {},
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = LocalConfiguration.current.locales[0],
) {
  // Kept here rather than in the ViewModel: the sheet is only a question, and nothing happens
  // until "Yes". Saveable, so it stays open across a rotation.
  var isCancelSheetOpen by rememberSaveable { mutableStateOf(false) }
  // The question no longer applies once cancelling is not possible (e.g. offline, or the pickup
  // day of an accepted rental came), so the sheet closes and does not come back by itself.
  LaunchedEffect(uiState.canCancel) { if (!uiState.canCancel) isCancelSheetOpen = false }
  Column(
      modifier =
          modifier
              .fillMaxSize()
              .background(MaterialTheme.colorScheme.background)
              .testTag(C.Tag.rental_detail_screen)
  ) {
    // The checks go from the most general to the most specific: while loading, nothing else in the
    // state is meaningful yet; an error or a missing rental leaves no rental to show; and only a
    // rental that exists can be one whose status is not built yet.
    when {
      uiState.isLoading ->
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.testTag(C.Tag.rental_detail_loading))
          }
      uiState.errorMsg != null -> RentalDetailError(errorMsg = uiState.errorMsg, onRetry = onRetry)
      uiState.notFound -> RentalNotFound()
      !uiState.isSupported ->
          Column(
              modifier = Modifier.fillMaxSize().padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            RentalTitle(title = uiState.itemTitle)
            Text(
                text = stringResource(R.string.rental_detail_unsupported),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(C.Tag.rental_detail_unsupported),
            )
          }
      else -> {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          RentalTitle(title = uiState.itemTitle)
          StageStrip(status = uiState.status)
          StatusCard(uiState = uiState, today = today, zone = zone, locale = locale)
          DetailsCard(uiState = uiState, today = today, locale = locale)
          uiState.otherPerson?.let { person ->
            PersonCard(
                person = person,
                onClick = { onPersonClick(person.uid) },
                onChatClick = onChatClick,
            )
          }
          when (uiState.status) {
            // A disputed rental shows no meeting point: the item is already back.
            RentalStatus.DISPUTED -> Unit
            RentalStatus.ACCEPTED ->
                PickupPoint(
                    label = stringResource(R.string.rental_detail_pickup_point_label),
                    labelTag = C.Tag.rental_detail_pickup_point_label,
                    address = uiState.pickupAddress,
                    addressTag = C.Tag.rental_detail_pickup_address,
                    dropOffAddress = uiState.returnAddress,
                )
            // The item is with the borrower, so only where it goes back matters now: the return
            // address alone, without a second "Drop-off" line.
            RentalStatus.ACTIVE ->
                PickupPoint(
                    label = stringResource(R.string.rental_detail_return_point_label),
                    labelTag = C.Tag.rental_detail_return_point_label,
                    address = uiState.returnAddress,
                    addressTag = C.Tag.rental_detail_return_address,
                )
            else ->
                PickupArea(
                    area = uiState.pickupArea,
                    point = uiState.pickupPoint,
                    showMap = !uiState.isOffline,
                )
          }
          // A request keeps its button at any date: a request without an offer never expires, so
          // without it the request would stay in My rentals forever. An accepted rental is locked
          // from its pickup day, so its row goes; while the server's day is unknown or being read,
          // the row shows with the button disabled.
          val showsCancelRow =
              when (uiState.status) {
                RentalStatus.REQUESTED -> true
                RentalStatus.ACCEPTED ->
                    uiState.cancelUntil?.let { uiState.serverToday?.isAfter(it) != true } ?: false
                else -> false
              }
          if (showsCancelRow) {
            CancelRow(
                status = uiState.status,
                cancelUntil = uiState.cancelUntil,
                serverToday = uiState.serverToday,
                isReadingServerToday = uiState.isReadingServerToday,
                canCancel = uiState.canCancel,
                cancelError = uiState.cancelError,
                onClick = { isCancelSheetOpen = true },
                today = today,
                locale = locale,
            )
          }
        }
        // Outside the scrolling column, so the main action stays at the bottom of the screen.
        ActionBar(
            uiState = uiState,
            today = today,
            locale = locale,
            onStartPickupClick = onStartPickupClick,
            onStartReturnClick = onStartReturnClick,
            onViewDisputeClick = onViewDisputeClick,
            onReviewRequestClick = onReviewRequestClick,
        )
        if (isCancelSheetOpen && uiState.canCancel) {
          CancelConfirmSheet(
              onYes = {
                isCancelSheetOpen = false
                onCancelClick()
              },
              onDismiss = { isCancelSheetOpen = false },
          )
        }
      }
    }
  }
}

/**
 * The main action at the bottom of the screen, for both roles: "Start Pickup" once accepted, "Start
 * Return" once active, "View Dispute" once disputed, and, for the lender of a request with no
 * offer, "Review request"; nothing otherwise for a request. Start Pickup opens on the pickup day
 * and Start Return on the return day, both by the server's date (see [ServerDayButton]); before
 * that day, the day it opens is written in [locale].
 */
@Composable
private fun ActionBar(
    uiState: RentalDetailUiState,
    today: LocalDate,
    locale: Locale,
    onStartPickupClick: () -> Unit,
    onStartReturnClick: () -> Unit,
    onViewDisputeClick: () -> Unit,
    onReviewRequestClick: () -> Unit,
) {
  val start = uiState.startDate
  val end = uiState.endDate
  when (uiState.status) {
    RentalStatus.ACCEPTED ->
        if (start != null) {
          ServerDayButton(
              uiState = uiState,
              text = stringResource(R.string.rental_detail_start_pickup),
              opensOn = start,
              availableOnText =
                  stringResource(
                      R.string.rental_detail_pickup_available_on,
                      formatDay(start, withYear = start.year != today.year, locale = locale),
                  ),
              availableOnTag = C.Tag.rental_detail_pickup_available_on,
              needsConnectionText = stringResource(R.string.rental_detail_pickup_needs_connection),
              needsConnectionTag = C.Tag.rental_detail_pickup_needs_connection,
              onClick = onStartPickupClick,
          )
        }
    RentalStatus.ACTIVE ->
        // From endDate, the "Return by" day, not startDate like Start Pickup: the item was already
        // picked up on startDate, and the return is due on the rental's last day.
        if (end != null) {
          ServerDayButton(
              uiState = uiState,
              text = stringResource(R.string.rental_detail_start_return),
              opensOn = end,
              availableOnText =
                  stringResource(
                      R.string.rental_detail_return_available_on,
                      formatDay(end, withYear = end.year != today.year, locale = locale),
                  ),
              availableOnTag = C.Tag.rental_detail_return_available_on,
              needsConnectionText = stringResource(R.string.rental_detail_return_needs_connection),
              needsConnectionTag = C.Tag.rental_detail_return_needs_connection,
              onClick = onStartReturnClick,
          )
        }
    RentalStatus.DISPUTED ->
        ActionBarSurface {
          // Never disabled: both roles can follow the dispute at every stage.
          ActionButton(
              text = stringResource(R.string.rental_detail_view_dispute),
              enabled = true,
              onClick = onViewDisputeClick,
          )
        }
    RentalStatus.REQUESTED ->
        if (uiState.canReviewRequest) {
          ActionBarSurface {
            ActionButton(
                text = stringResource(R.string.my_rentals_review_request),
                enabled = true,
                onClick = onReviewRequestClick,
            )
          }
        }
    else -> Unit
  }
}

/**
 * A bottom button that opens on [opensOn] by the server's date ([RentalDetailUiState.serverToday]),
 * shared by Start Pickup and Start Return so both follow the same rule. While that date is being
 * read, it is grey with nothing under it. Before [opensOn], it is grey with [availableOnText]
 * (tagged [availableOnTag]) under it; when the server's date is unknown, with [needsConnectionText]
 * (tagged [needsConnectionTag]). Once open, nothing is under it and a tap calls [onClick].
 */
@Composable
private fun ServerDayButton(
    uiState: RentalDetailUiState,
    text: String,
    opensOn: LocalDate,
    availableOnText: String,
    availableOnTag: String,
    needsConnectionText: String,
    needsConnectionTag: String,
    onClick: () -> Unit,
) {
  // From the server's day, never the phone's: the user can change the phone's date to open the
  // pickup or the return early. An unknown server day keeps it closed, as falling back on the
  // phone's date would let airplane mode plus a changed date open it.
  val serverToday = uiState.serverToday
  val isReading = uiState.isReadingServerToday
  val open = !isReading && (serverToday?.let { !it.isBefore(opensOn) } ?: false)
  ActionBarSurface {
    ActionButton(text = text, enabled = open, onClick = onClick)
    // Shown disabled rather than hidden before the day, so the user sees what comes next and from
    // when. Nothing is written while the date is being read: it is not known yet, and asking to
    // connect would be wrong online.
    if (!open && !isReading) {
      val (note, tag) =
          if (serverToday == null) needsConnectionText to needsConnectionTag
          else availableOnText to availableOnTag
      Text(
          text = note,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth().testTag(tag),
      )
    }
  }
}

@Composable
private fun ActionBarSurface(content: @Composable () -> Unit) {
  Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      content()
    }
  }
}

@Composable
private fun ActionButton(text: String, enabled: Boolean, onClick: () -> Unit) {
  Button(
      onClick = onClick,
      enabled = enabled,
      modifier = Modifier.fillMaxWidth().testTag(C.Tag.rental_detail_action_button),
  ) {
    Text(text)
  }
}

/**
 * The stages after the request, one bar each; the reached ones are red and the current one has a
 * bold label. A Requested rental has reached none of them, so all four stay grey; an Accepted one
 * has reached the first, an Active one the first two, up to In use, and a Disputed one the first
 * three, up to Returned.
 */
@Composable
private fun StageStrip(status: RentalStatus?) {
  val labels =
      listOf(
          R.string.rental_detail_stage_accepted,
          R.string.rental_detail_stage_in_use,
          R.string.rental_detail_stage_returned,
          R.string.rental_detail_stage_closed,
      )
  val reachedCount =
      when (status) {
        RentalStatus.ACCEPTED -> 1
        RentalStatus.ACTIVE -> 2
        RentalStatus.DISPUTED -> 3
        else -> 0
      }
  Row(
      modifier = Modifier.fillMaxWidth().testTag(C.Tag.rental_detail_stage_strip),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    labels.forEachIndexed { index, label ->
      val reached = index < reachedCount
      val current = index == reachedCount - 1
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .height(4.dp)
                    .then(
                        if (reached) Modifier.testTag(C.Tag.rental_detail_stage_reached)
                        else Modifier
                    )
                    .background(
                        if (reached) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape,
                    )
        )
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (current) FontWeight.Bold else null,
            color =
                if (current) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                if (current) Modifier.testTag(C.Tag.rental_detail_stage_current) else Modifier,
        )
      }
    }
  }
}

/**
 * The status badge, the headline and when the request was sent. The sent day is read in [zone] and
 * written in [locale], with its year only when it is not [today]'s year. While an offer waits for
 * an answer, the card says where it stands instead (Figma frame 13.10). Once accepted, it gives the
 * pickup day (Figma frame 13.01); once active, the return day (Figma frame 13.02-active). Once
 * disputed, it says that damage was flagged and the deposit stays held (Figma frame 13.09).
 */
@Composable
private fun StatusCard(
    uiState: RentalDetailUiState,
    today: LocalDate,
    zone: ZoneId,
    locale: Locale,
) {
  val status = uiState.status
  val sentAt = uiState.sentAt
  val offer = uiState.offer
  OutlinedCard(modifier = Modifier.fillMaxWidth()) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        status?.let { StatusBadge(it) }
        Text(
            text = statusHeadline(uiState, today, locale),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag(C.Tag.rental_detail_status_headline),
        )
      }
      val body: String? =
          when {
            status == RentalStatus.ACCEPTED -> stringResource(R.string.rental_detail_accepted_body)
            status == RentalStatus.ACTIVE -> stringResource(R.string.rental_detail_active_body)
            status == RentalStatus.DISPUTED -> stringResource(R.string.rental_detail_disputed_body)
            offer != null && uiState.isOfferExpired ->
                stringResource(R.string.rental_detail_offer_body_expired, offer.amount)
            offer != null -> offerBody(offer, uiState.isOfferMine, zone, locale)
            sentAt != null -> {
              val sentDay = sentAt.atZone(zone).toLocalDate()
              stringResource(
                  R.string.rental_detail_status_body,
                  formatDay(sentDay, withYear = sentDay.year != today.year, locale = locale),
              )
            }
            else -> null
          }
      body?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(C.Tag.rental_detail_status_body),
        )
      }
    }
  }
}

/**
 * The headline of the status card: "Damage flagged" once disputed, the return day once active, the
 * pickup day once accepted (both written in [locale]), else where the offer stands.
 */
@Composable
private fun statusHeadline(uiState: RentalDetailUiState, today: LocalDate, locale: Locale): String {
  if (uiState.status == RentalStatus.DISPUTED) {
    return stringResource(R.string.rental_detail_disputed_headline)
  }
  val end = uiState.endDate
  if (uiState.status == RentalStatus.ACTIVE && end != null) {
    return stringResource(
        R.string.rental_detail_active_headline,
        formatDay(end, withYear = end.year != today.year, locale = locale),
    )
  }
  val start = uiState.startDate
  if (uiState.status != RentalStatus.ACCEPTED || start == null) {
    return stringResource(offerHeadline(uiState.offer, uiState.isOfferMine, uiState.isOfferExpired))
  }
  return stringResource(
      R.string.rental_detail_accepted_headline,
      formatDay(start, withYear = start.year != today.year, locale = locale),
  )
}

/** The headline while requested: where the offer stands, or the default without one. */
@StringRes
private fun offerHeadline(offer: DepositOffer?, isMine: Boolean, isExpired: Boolean): Int =
    when {
      offer == null -> R.string.rental_detail_status_headline
      isExpired -> R.string.offer_expired
      isMine && offer.isCounter -> R.string.rental_detail_status_counter_sent
      isMine -> R.string.rental_detail_status_offer_sent
      offer.isCounter -> R.string.rental_detail_status_counter_received
      else -> R.string.rental_detail_status_offer_received
    }

/**
 * The amount of the offer, when it expires (the day in [zone], written in [locale]), and what comes
 * next.
 */
@Composable
private fun offerBody(offer: DepositOffer, isMine: Boolean, zone: ZoneId, locale: Locale): String {
  val expiry = formatDay(offer.expiresAt.atZone(zone).toLocalDate(), locale = locale)
  return stringResource(
      when {
        isMine && offer.isCounter -> R.string.rental_detail_counter_body_waiting
        isMine -> R.string.rental_detail_offer_body_waiting
        offer.isCounter -> R.string.rental_detail_counter_body_answer
        else -> R.string.rental_detail_offer_body_answer
      },
      offer.amount,
      expiry,
  )
}

/**
 * The dates, the return day, the reserved rental price, and the deposit held since acceptance
 * (Figma frame 13.01), the offer or the suggested deposit; a held deposit of 0 is unknown and shows
 * "—". A disputed rental shows the dates without the return day, the price as paid, the deposit as
 * held and the late fees, even when they are 0 (Figma frame 13.09). An active rental shows the
 * dates, the "Return by" day, the price as paid and the deposit as held, without late fees (Figma
 * frame 13.02-active). The dates are written in [locale].
 */
@Composable
private fun DetailsCard(uiState: RentalDetailUiState, today: LocalDate, locale: Locale) {
  val isDisputed = uiState.status == RentalStatus.DISPUTED
  val isActive = uiState.status == RentalStatus.ACTIVE
  OutlinedCard(modifier = Modifier.fillMaxWidth()) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
          text = stringResource(R.string.rental_detail_details_title),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
      )
      val start = uiState.startDate
      val end = uiState.endDate
      if (start != null && end != null) {
        val (from, to) = formatRangeEnds(start, end, locale, today)
        InfoRow(
            label = stringResource(R.string.rental_detail_dates_label),
            value = stringResource(R.string.listing_detail_date_range, from, to),
            valueTag = C.Tag.rental_detail_dates,
        )
        if (!isDisputed) {
          InfoRow(
              label =
                  stringResource(
                      if (isActive) R.string.rental_detail_return_by_label
                      else R.string.rental_detail_return_label
                  ),
              value = formatDay(end, withYear = end.year != today.year, locale = locale),
              valueTag = C.Tag.rental_detail_return,
          )
        }
      }
      InfoRow(
          label = stringResource(R.string.rental_detail_price_label),
          value =
              stringResource(
                  if (isDisputed || isActive) R.string.rental_detail_price_paid
                  else R.string.rental_detail_price,
                  uiState.rentalPrice,
              ),
          valueTag = C.Tag.rental_detail_price,
      )
      if (uiState.status == RentalStatus.ACCEPTED || isActive || isDisputed) {
        // An accepted deposit is at least 1, so 0 means it is unknown: "—", not "0 PP · held".
        InfoRow(
            label = stringResource(R.string.rental_detail_accepted_deposit_label),
            value =
                if (uiState.deposit > 0) {
                  stringResource(R.string.rental_detail_deposit_held, uiState.deposit)
                } else {
                  stringResource(R.string.rental_detail_deposit_unknown)
                },
            valueTag = C.Tag.rental_detail_accepted_deposit,
        )
      }
      if (isDisputed) {
        InfoRow(
            label = stringResource(R.string.rental_detail_late_fees_label),
            value = stringResource(R.string.rental_detail_late_fees, uiState.lateFees),
            valueTag = C.Tag.rental_detail_late_fees,
        )
      }
      uiState.offer?.let {
        InfoRow(
            label = stringResource(R.string.rental_detail_offer_label),
            value = stringResource(R.string.listing_card_price, it.amount),
            valueTag = C.Tag.rental_detail_offer,
        )
      }
      // Null means the listing could not be read, not that the deposit is 0: "0 PP" would be
      // wrong, so the row is left out instead.
      uiState.suggestedDeposit
          ?.takeIf { uiState.offer == null && uiState.status == RentalStatus.REQUESTED }
          ?.let {
            InfoRow(
                label = stringResource(R.string.rental_detail_deposit_label),
                value = stringResource(R.string.listing_card_price, it),
                valueTag = C.Tag.rental_detail_deposit,
                isBold = false,
            )
          }
    }
  }
}

/**
 * The other person, built like the Profile header: their initial, their name and their rating. The
 * card opens their profile; the Chat button opens the chat and, being its own clickable, does not
 * also open the profile.
 */
@Composable
internal fun PersonCard(person: OtherPerson, onClick: () -> Unit, onChatClick: () -> Unit) {
  OutlinedCard(
      onClick = onClick,
      modifier = Modifier.fillMaxWidth().testTag(C.Tag.rental_detail_person_card),
  ) {
    Row(
        modifier = Modifier.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Box(
          modifier =
              Modifier.size(40.dp)
                  .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
          contentAlignment = Alignment.Center,
      ) {
        Text(
            text = person.name.trim().firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag(C.Tag.rental_detail_person_initial),
        )
      }
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = person.name.ifBlank { stringResource(R.string.profile_name_placeholder) },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag(C.Tag.rental_detail_person_name),
        )
        // Null means their profile could not be read, so the rating is unknown: "No reviews yet"
        // would be a claim we cannot make. 0 reviews is known, and says so.
        if (person.averageRating != null && person.reviewCount != null) {
          RatingLine(
              averageRating = person.averageRating,
              reviewCount = person.reviewCount,
              modifier = Modifier.testTag(C.Tag.rental_detail_person_rating),
          )
        }
      }
      OutlinedButton(
          onClick = onChatClick,
          modifier = Modifier.testTag(C.Tag.rental_detail_chat_button),
      ) {
        Icon(
            painterResource(R.drawable.ic_chat),
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize),
        )
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(stringResource(R.string.rental_detail_chat))
      }
    }
  }
}

/**
 * An exact meeting point: the [label] (tagged [labelTag]), a map box, and the [address] (tagged
 * [addressTag]). Once accepted it is the pickup point (Figma frame 13.01), with [dropOffAddress] on
 * a "Drop-off" line under it; once active, the return point alone (Figma frame 13.02-active), with
 * no [dropOffAddress]. A missing address reads as "not known yet".
 */
@Composable
private fun PickupPoint(
    label: String,
    labelTag: String,
    address: String,
    addressTag: String,
    dropOffAddress: String? = null,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag(labelTag),
    )
    // A grey placeholder until the meeting point gets its own map (the pickup area's map shows only
    // the public area). The return point keeps it too, like the pickup point, as both are exact
    // addresses that the public map cannot show.
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .height(160.dp)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .testTag(C.Tag.rental_detail_map)
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(Icons.Outlined.Place, contentDescription = null, modifier = Modifier.size(20.dp))
      Text(
          text = address.ifBlank { stringResource(R.string.rental_detail_address_placeholder) },
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.testTag(addressTag),
      )
    }
    dropOffAddress?.let {
      Text(
          text =
              stringResource(
                  R.string.rental_detail_return_address,
                  it.ifBlank { stringResource(R.string.rental_detail_address_placeholder) },
              ),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.testTag(C.Tag.rental_detail_return_address),
      )
    }
  }
}

/**
 * The public pickup area: a small map centered on [point] (see [PickupMap] for when [showMap] and a
 * missing [point] replace it), the [area] name (or a placeholder until the listing is geocoded),
 * and a note that the exact address comes after acceptance.
 */
@Composable
private fun PickupArea(area: String, point: MapPoint?, showMap: Boolean) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
        text = stringResource(R.string.rental_detail_pickup_label),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .height(160.dp)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .testTag(C.Tag.rental_detail_map)
    ) {
      PickupMap(area = area, point = point, showMap = showMap)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(Icons.Outlined.Place, contentDescription = null, modifier = Modifier.size(20.dp))
      Text(
          text = area.ifBlank { stringResource(R.string.rental_detail_area_placeholder) },
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.testTag(C.Tag.rental_detail_pickup_area),
      )
    }
    Text(
        text = stringResource(R.string.rental_detail_pickup_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag(C.Tag.rental_detail_pickup_note),
    )
  }
}

/**
 * The cancel button, on the right: "Cancel request" on a Requested rental, "Cancel rental" on an
 * Accepted one, enabled only when [canCancel], calling [onClick]. On its left, for an accepted
 * rental only: "You can cancel until" [cancelUntil], written in [locale] ("Wed 30 Sep" in English),
 * with the year when it is not [today]'s, while [serverToday] is known and not after [cancelUntil];
 * "Connect to the internet to check the cancel day." while [serverToday] is unknown and not being
 * read; nothing while it is being read. [today] is the phone's date and only decides the year.
 * Under the row, a generic error when the last cancel failed ([cancelError] not null).
 */
@Composable
private fun CancelRow(
    status: RentalStatus?,
    cancelUntil: LocalDate?,
    serverToday: LocalDate?,
    isReadingServerToday: Boolean,
    canCancel: Boolean,
    cancelError: String?,
    onClick: () -> Unit,
    today: LocalDate,
    locale: Locale,
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      // A request has no deadline, so no date is written for it: one would read as a deadline.
      val lastDay = cancelUntil?.takeIf { status == RentalStatus.ACCEPTED }
      when {
        lastDay != null && serverToday != null && !serverToday.isAfter(lastDay) -> {
          val pattern = if (lastDay.year != today.year) "EEE d MMM yyyy" else "EEE d MMM"
          Text(
              text =
                  stringResource(
                      R.string.rental_detail_cancel_until,
                      lastDay.format(DateTimeFormatter.ofPattern(pattern, locale)),
                  ),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.weight(1f).testTag(C.Tag.rental_detail_cancel_until),
          )
        }
        // Like Start Pickup: nothing while the day is being read, as asking to connect would be
        // wrong online.
        lastDay != null && serverToday == null && !isReadingServerToday ->
            Text(
                text = stringResource(R.string.rental_detail_cancel_needs_connection),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).testTag(C.Tag.rental_detail_cancel_needs_connection),
            )
        // Keeps the button on the right when no text is written.
        else -> Spacer(Modifier.weight(1f))
      }
      TextButton(
          onClick = onClick,
          enabled = canCancel,
          // Through the button's colours rather than on the Text, so the disabled state is grey.
          colors =
              ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
          modifier = Modifier.testTag(C.Tag.rental_detail_cancel_button),
      ) {
        Text(
            text =
                stringResource(
                    if (status == RentalStatus.ACCEPTED) R.string.rental_detail_cancel_rental
                    else R.string.rental_detail_cancel_request
                ),
            fontWeight = FontWeight.Bold,
        )
      }
    }
    if (cancelError != null) {
      // Always the generic text, never cancelError's message: it is a raw Firebase text the user
      // cannot act on. It does not say "Try again" either, as some refusals are final (e.g. an
      // accepted rental whose pickup day has come by the server's clock). Once #429 sends a
      // details.reason, the repository should read it and the screen show a matching message, as
      // DisputeRepositoryFirestore does; not yet, as #429 does not define the reasons.
      Text(
          text = stringResource(R.string.rental_detail_cancel_error_fallback),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.testTag(C.Tag.rental_detail_cancel_error),
      )
    }
  }
}

/**
 * The confirm sheet of a cancel, over the dimmed screen: "Are you sure you want to cancel this
 * rental?" with "No" and "Yes". [onYes] confirms; "No", a swipe down, Back or a tap on the dimmed
 * screen call [onDismiss].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CancelConfirmSheet(onYes: () -> Unit, onDismiss: () -> Unit) {
  val red = MaterialTheme.colorScheme.primary
  val white = MaterialTheme.colorScheme.onPrimary
  ModalBottomSheet(
      onDismissRequest = onDismiss,
      sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
      modifier = Modifier.testTag(C.Tag.rental_detail_cancel_sheet),
      containerColor = red,
      contentColor = white,
      dragHandle = null,
      // Keeps the buttons above the navigation bar.
      contentWindowInsets = { WindowInsets.navigationBars },
  ) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text(
          text = stringResource(R.string.rental_detail_cancel_confirm_title),
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          color = white,
      )
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        OutlinedButton(
            onClick = onDismiss,
            border = BorderStroke(1.dp, white),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = white),
            modifier = Modifier.weight(1f).testTag(C.Tag.rental_detail_cancel_no),
        ) {
          Text(stringResource(R.string.rental_detail_cancel_no), fontWeight = FontWeight.Bold)
        }
        Button(
            onClick = onYes,
            colors = ButtonDefaults.buttonColors(containerColor = white, contentColor = red),
            modifier = Modifier.weight(1f).testTag(C.Tag.rental_detail_cancel_yes),
        ) {
          Text(stringResource(R.string.rental_detail_cancel_yes), fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}

/** A label on the left and its value on the right, bold unless [isBold] is false. */
@Composable
internal fun InfoRow(label: String, value: String, valueTag: String, isBold: Boolean = true) {
  Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(1f),
    )
    Text(
        text = value,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
        color =
            if (isBold) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag(valueTag),
    )
  }
}

@Composable
private fun StatusBadge(status: RentalStatus) {
  val (container, content) = status.badgeColors()
  Surface(shape = CircleShape, color = container, contentColor = content) {
    Text(
        text = stringResource(status.labelRes),
        style = MaterialTheme.typography.labelMedium,
        modifier =
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                .testTag(C.Tag.rental_detail_status),
    )
  }
}

/** The load error ([errorMsg], or a generic text if it is blank) and a Retry button. */
@Composable
internal fun RentalDetailError(errorMsg: String, onRetry: () -> Unit) {
  Column(
      modifier = Modifier.fillMaxSize().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
        text = errorMsg.ifBlank { stringResource(R.string.rental_detail_error_fallback) },
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.testTag(C.Tag.rental_detail_error),
    )
    Button(onClick = onRetry, modifier = Modifier.testTag(C.Tag.rental_detail_retry_button)) {
      Text(stringResource(R.string.rental_detail_retry))
    }
  }
}

@Composable
internal fun RentalNotFound() {
  Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
    Text(
        text = stringResource(R.string.rental_detail_not_found),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.testTag(C.Tag.rental_detail_not_found),
    )
  }
}

@Composable
private fun RentalTitle(title: String) {
  Text(
      text = title,
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.testTag(C.Tag.rental_detail_title),
  )
}

@Preview(showBackground = true)
@Composable
private fun RentalDetailContentPreview() {
  PolyLoopTheme {
    Column {
      PolyLoopTopBar(onMenuClick = {}, onProfileClick = {})
      RentalDetailContent(
          uiState =
              RentalDetailUiState(
                  itemTitle = "Ski helmet, size M",
                  status = RentalStatus.REQUESTED,
                  sentAt = Instant.parse("2026-09-28T10:00:00Z"),
                  startDate = LocalDate.of(2026, 10, 1),
                  endDate = LocalDate.of(2026, 10, 8),
                  rentalPrice = 120,
                  suggestedDeposit = 180,
                  pickupArea = "Ecublens",
                  otherPerson =
                      OtherPerson(uid = "lea", name = "Léa", averageRating = 4.6, reviewCount = 5),
              ),
          onRetry = {},
          onChatClick = {},
          onPersonClick = {},
          onStartPickupClick = {},
          onStartReturnClick = {},
          onViewDisputeClick = {},
          onCancelClick = {},
          today = LocalDate.of(2026, 9, 28),
      )
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun RentalDetailDisputedPreview() {
  PolyLoopTheme {
    Column {
      PolyLoopTopBar(onMenuClick = {}, onProfileClick = {})
      RentalDetailContent(
          uiState =
              RentalDetailUiState(
                  itemTitle = "Ski helmet, size M",
                  status = RentalStatus.DISPUTED,
                  startDate = LocalDate.of(2026, 10, 1),
                  endDate = LocalDate.of(2026, 10, 8),
                  rentalPrice = 120,
                  deposit = 60,
                  otherPerson =
                      OtherPerson(uid = "lea", name = "Léa", averageRating = 4.6, reviewCount = 5),
              ),
          onRetry = {},
          onChatClick = {},
          onPersonClick = {},
          onStartPickupClick = {},
          onStartReturnClick = {},
          onViewDisputeClick = {},
          onCancelClick = {},
          today = LocalDate.of(2026, 10, 12),
      )
    }
  }
}
