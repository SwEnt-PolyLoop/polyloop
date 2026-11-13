// Made with Claude.

package com.swent.polyloop.ui.rentaldetail

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swent.polyloop.R
import com.swent.polyloop.model.location.MapPoint
import com.swent.polyloop.model.rental.DepositOffer
import com.swent.polyloop.model.rental.RentalStatus
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.components.NotAvailableYet
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
 * (Handover) of an accepted rental, [onStartReturnClick] the return (Handover) of an active one and
 * [onViewDisputeClick] the dispute of a disputed one. The app's top bar is drawn by the navigation
 * scaffold, not here.
 */
@Composable
fun RentalDetailScreen(
    viewModel: RentalDetailViewModel,
    onChatClick: () -> Unit,
    onPersonClick: (uid: String) -> Unit,
    onStartPickupClick: () -> Unit,
    onStartReturnClick: () -> Unit,
    onViewDisputeClick: () -> Unit,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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
  )
}

/**
 * Stateless Rental detail content: a loading indicator, an error with a Retry button, a not-found
 * message, a placeholder for a status not built yet, or the Requested, Accepted, Active or Disputed
 * rental, depending on [uiState]. An Accepted rental has "Start Pickup" ([onStartPickupClick])
 * pinned at the bottom, an Active one "Start Return" ([onStartReturnClick]) and a Disputed one
 * "View Dispute" ([onViewDisputeClick]). [today] and [zone] decide how dates are written (e.g.
 * whether the year is shown); tests pass fixed values so their expected texts do not depend on the
 * day they run. [today] is the phone's date, so it is only used to write dates: Start Pickup and
 * Start Return open from [RentalDetailUiState.serverToday] instead, and stay grey while
 * [RentalDetailUiState.isReadingServerToday]. [locale] decides the language of the month and
 * weekday names; tests pass a fixed one so their expected dates do not depend on the device's
 * language.
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
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = LocalConfiguration.current.locales[0],
) {
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
          val isDisputed = uiState.status == RentalStatus.DISPUTED
          uiState.otherPerson?.let { person ->
            PersonCard(
                person = person,
                onClick = { onPersonClick(person.uid) },
                onChatClick = onChatClick,
                // Chat is paused while the dispute is open; the card itself stays tappable, so the
                // other person's profile can still be opened.
                chatEnabled = !isDisputed,
            )
          }
          if (isDisputed) {
            ChatPausedNote()
          } else if (uiState.status == RentalStatus.ACCEPTED) {
            PickupPoint(
                label = stringResource(R.string.rental_detail_pickup_point_label),
                labelTag = C.Tag.rental_detail_pickup_point_label,
                address = uiState.pickupAddress,
                addressTag = C.Tag.rental_detail_pickup_address,
                dropOffAddress = uiState.returnAddress,
            )
          } else if (uiState.status == RentalStatus.ACTIVE) {
            // The item is with the borrower, so only where it goes back matters now: the return
            // address alone, without a second "Drop-off" line.
            PickupPoint(
                label = stringResource(R.string.rental_detail_return_point_label),
                labelTag = C.Tag.rental_detail_return_point_label,
                address = uiState.returnAddress,
                addressTag = C.Tag.rental_detail_return_address,
            )
          } else {
            PickupArea(
                area = uiState.pickupArea,
                point = uiState.pickupPoint,
                showMap = !uiState.isOffline,
            )
          }
          // A request that was never accepted can always be withdrawn, even after its pickup day,
          // so the borrower keeps the button for as long as the rental is Requested.
          if (!uiState.isLender && uiState.status == RentalStatus.REQUESTED) {
            CancelRow(
                lastCancelDay = uiState.startDate?.minusDays(1),
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
        )
      }
    }
  }
}

/**
 * The main action at the bottom of the screen, for both roles: "Start Pickup" once accepted, "Start
 * Return" once active, "View Dispute" once disputed, and nothing for a request. Start Pickup opens
 * on the pickup day and Start Return on the return day, both by the server's date (see
 * [ServerDayButton]); before that day, the day it opens is written in [locale].
 */
@Composable
private fun ActionBar(
    uiState: RentalDetailUiState,
    today: LocalDate,
    locale: Locale,
    onStartPickupClick: () -> Unit,
    onStartReturnClick: () -> Unit,
    onViewDisputeClick: () -> Unit,
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
    return stringResource(offerHeadline(uiState.offer, uiState.isOfferMine))
  }
  return stringResource(
      R.string.rental_detail_accepted_headline,
      formatDay(start, withYear = start.year != today.year, locale = locale),
  )
}

/** The headline while requested: where the offer stands, or the default without one. */
@StringRes
private fun offerHeadline(offer: DepositOffer?, isMine: Boolean): Int =
    when {
      offer == null -> R.string.rental_detail_status_headline
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
 * The dates, the return day, the reserved rental price, and the accepted deposit, the offer or the
 * suggested deposit. The deposit is held from acceptance, but this screen does not show the "Held"
 * state of an accepted deposit yet. A disputed rental shows the dates without the return day, the
 * price as paid, the deposit as held and the late fees, even when they are 0 (Figma frame 13.09).
 * An active rental shows the dates, the "Return by" day, the price as paid and the deposit as held,
 * without late fees (Figma frame 13.02-active). The dates are written in [locale].
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
      // 0 means the deposit is unknown, since an accepted one is at least 1: "0 PP" would be wrong.
      if (uiState.status == RentalStatus.ACCEPTED && uiState.deposit > 0) {
        InfoRow(
            label = stringResource(R.string.rental_detail_accepted_deposit_label),
            value = stringResource(R.string.listing_card_price, uiState.deposit),
            valueTag = C.Tag.rental_detail_accepted_deposit,
        )
      }
      // Left out at 0 for the same reason as on Accepted.
      if (isActive && uiState.deposit > 0) {
        InfoRow(
            label = stringResource(R.string.rental_detail_accepted_deposit_label),
            value = stringResource(R.string.rental_detail_deposit_held, uiState.deposit),
            valueTag = C.Tag.rental_detail_accepted_deposit,
        )
      }
      if (isDisputed) {
        // Shown even at 0, unlike Accepted: the dispute is about the deposit, so the row stays.
        InfoRow(
            label = stringResource(R.string.rental_detail_accepted_deposit_label),
            value = stringResource(R.string.rental_detail_deposit_held, uiState.deposit),
            valueTag = C.Tag.rental_detail_accepted_deposit,
        )
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
 * also open the profile. When [chatEnabled] is false the Chat button is shown disabled, while the
 * card still opens the profile.
 */
@Composable
internal fun PersonCard(
    person: OtherPerson,
    onClick: () -> Unit,
    onChatClick: () -> Unit,
    chatEnabled: Boolean = true,
) {
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
          enabled = chatEnabled,
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

@Composable
private fun ChatPausedNote() {
  Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Icon(
        Icons.Outlined.Lock,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(16.dp),
    )
    Text(
        text = stringResource(R.string.rental_detail_chat_paused),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag(C.Tag.rental_detail_chat_paused),
    )
  }
}

/**
 * An exact meeting point: the [label] (tagged [labelTag]), a map box, and the [address] (tagged
 * [addressTag]). Once accepted it is the pickup point (Figma frame 13.01), with [dropOffAddress]
 * on a "Drop-off" line under it; once active, the return point alone (Figma frame 13.02-active),
 * with no [dropOffAddress]. A missing address reads as "not known yet".
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
 * A "Cancel request" button, on the right, and on its left the last day before pickup, written in
 * [locale] ("Wed 30 Sep" in English), with the year when it is not [today]'s. [lastCancelDay] is
 * null when the rental has no start date.
 */
@Composable
private fun CancelRow(lastCancelDay: LocalDate?, today: LocalDate, locale: Locale) {
  Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    // After the last cancel day the date would be in the past, so only the button is left.
    if (lastCancelDay != null && !today.isAfter(lastCancelDay)) {
      val pattern = if (lastCancelDay.year != today.year) "EEE d MMM yyyy" else "EEE d MMM"
      Text(
          text =
              stringResource(
                  R.string.rental_detail_cancel_until,
                  lastCancelDay.format(DateTimeFormatter.ofPattern(pattern, locale)),
              ),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.weight(1f).testTag(C.Tag.rental_detail_cancel_until),
      )
    } else {
      // Keeps the button on the right when the text is left out.
      Spacer(Modifier.weight(1f))
    }
    // Does nothing on purpose: cancelling needs a repository call and a rules change, which the
    // cancel issue adds. Until then the button is only shown, with "Not available yet" under it.
    Column(horizontalAlignment = Alignment.End) {
      TextButton(onClick = {}, modifier = Modifier.testTag(C.Tag.rental_detail_cancel_button)) {
        Text(
            text = stringResource(R.string.rental_detail_cancel_request),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
      }
      NotAvailableYet(C.Tag.rental_detail_cancel_not_available, Modifier.padding(end = 12.dp))
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
          today = LocalDate.of(2026, 9, 28),
      )
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun RentalDetailActivePreview() {
  PolyLoopTheme {
    Column {
      PolyLoopTopBar(onMenuClick = {}, onProfileClick = {})
      RentalDetailContent(
          uiState =
              RentalDetailUiState(
                  itemTitle = "Ski helmet, size M",
                  status = RentalStatus.ACTIVE,
                  startDate = LocalDate.of(2026, 10, 1),
                  endDate = LocalDate.of(2026, 10, 8),
                  rentalPrice = 120,
                  deposit = 60,
                  returnAddress = "Rue de Lausanne 10, Renens",
                  otherPerson =
                      OtherPerson(uid = "lea", name = "Léa", averageRating = 4.6, reviewCount = 5),
                  serverToday = LocalDate.of(2026, 10, 8),
              ),
          onRetry = {},
          onChatClick = {},
          onPersonClick = {},
          onStartPickupClick = {},
          onStartReturnClick = {},
          onViewDisputeClick = {},
          today = LocalDate.of(2026, 10, 8),
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
          today = LocalDate.of(2026, 10, 12),
      )
    }
  }
}
