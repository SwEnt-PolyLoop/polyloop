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
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swent.polyloop.R
import com.swent.polyloop.model.rental.DepositOffer
import com.swent.polyloop.model.rental.RentalStatus
import com.swent.polyloop.resources.C
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

/**
 * The Rental detail screen, showing the rental loaded by [viewModel]. [onChatClick] opens the
 * rental's chat and [onPersonClick] the other person's profile. The app's top bar is drawn by the
 * navigation scaffold, not here.
 */
@Composable
fun RentalDetailScreen(
    viewModel: RentalDetailViewModel,
    onChatClick: () -> Unit,
    onPersonClick: (uid: String) -> Unit,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  // Split from the stateless content below so that tests and previews can set the state directly,
  // without a ViewModel and its repositories.
  RentalDetailContent(
      uiState = uiState,
      onRetry = viewModel::retry,
      onChatClick = onChatClick,
      onPersonClick = onPersonClick,
  )
}

/**
 * Stateless Rental detail content: a loading indicator, an error with a Retry button, a not-found
 * message, a placeholder for a status not built yet, or the Requested or Accepted rental, depending
 * on [uiState]. [today] and [zone] decide how dates are written (e.g. whether the year is shown);
 * tests pass fixed values so their expected texts do not depend on the day they run.
 */
@Composable
fun RentalDetailContent(
    uiState: RentalDetailUiState,
    onRetry: () -> Unit,
    onChatClick: () -> Unit,
    onPersonClick: (uid: String) -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
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
      else ->
          Column(
              modifier =
                  Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(16.dp),
          ) {
            RentalTitle(title = uiState.itemTitle)
            StageStrip(status = uiState.status)
            StatusCard(uiState = uiState, today = today, zone = zone)
            DetailsCard(uiState = uiState, today = today)
            uiState.otherPerson?.let { person ->
              PersonCard(
                  person = person,
                  onClick = { onPersonClick(person.uid) },
                  onChatClick = onChatClick,
              )
            }
            if (uiState.status == RentalStatus.ACCEPTED) {
              PickupPoint(uiState = uiState)
            } else {
              PickupArea(area = uiState.pickupArea)
            }
            // A request that was never accepted can always be withdrawn, even after its pickup day,
            // so the borrower keeps the button for as long as the rental is Requested.
            if (!uiState.isLender && uiState.status == RentalStatus.REQUESTED) {
              CancelRow(lastCancelDay = uiState.startDate?.minusDays(1), today = today)
            }
          }
    }
  }
}

/**
 * The stages after the request, one bar each. A Requested rental has reached none of them, so all
 * four stay grey; an Accepted one has reached the first, in red with a bold label.
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
  Row(
      modifier = Modifier.fillMaxWidth().testTag(C.Tag.rental_detail_stage_strip),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    labels.forEachIndexed { index, label ->
      val reached = index == 0 && status == RentalStatus.ACCEPTED
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
            fontWeight = if (reached) FontWeight.Bold else null,
            color =
                if (reached) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

/**
 * The status badge, the headline and when the request was sent. The sent day is read in [zone] and
 * written with its year only when it is not [today]'s year. While an offer waits for an answer, the
 * card says where it stands instead (Figma frame 13.10). Once accepted, it gives the pickup day
 * (Figma frame 13.01).
 */
@Composable
private fun StatusCard(uiState: RentalDetailUiState, today: LocalDate, zone: ZoneId) {
  val locale = LocalConfiguration.current.locales[0]
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
            text = statusHeadline(uiState, today),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag(C.Tag.rental_detail_status_headline),
        )
      }
      if (status == RentalStatus.ACCEPTED) {
        Text(
            text = stringResource(R.string.rental_detail_accepted_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(C.Tag.rental_detail_status_body),
        )
      } else if (offer != null) {
        Text(
            text = offerBody(offer, uiState.isOfferMine, zone),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(C.Tag.rental_detail_status_body),
        )
      } else if (sentAt != null) {
        val sentDay = sentAt.atZone(zone).toLocalDate()
        Text(
            text =
                stringResource(
                    R.string.rental_detail_status_body,
                    formatDay(sentDay, withYear = sentDay.year != today.year, locale = locale),
                ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(C.Tag.rental_detail_status_body),
        )
      }
    }
  }
}

/** The headline of the status card: the pickup day once accepted, else where the offer stands. */
@Composable
private fun statusHeadline(uiState: RentalDetailUiState, today: LocalDate): String {
  val start = uiState.startDate
  if (uiState.status != RentalStatus.ACCEPTED || start == null) {
    return stringResource(offerHeadline(uiState.offer, uiState.isOfferMine))
  }
  val locale = LocalConfiguration.current.locales[0]
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

/** The amount of the offer, when it expires, and what comes next. */
@Composable
private fun offerBody(offer: DepositOffer, isMine: Boolean, zone: ZoneId): String {
  val locale = LocalConfiguration.current.locales[0]
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
 * suggested deposit. "Held" is left out of the deposit until deposits are really reserved.
 */
@Composable
private fun DetailsCard(uiState: RentalDetailUiState, today: LocalDate) {
  val locale = LocalConfiguration.current.locales[0]
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
        InfoRow(
            label = stringResource(R.string.rental_detail_return_label),
            value = formatDay(end, withYear = end.year != today.year, locale = locale),
            valueTag = C.Tag.rental_detail_return,
        )
      }
      InfoRow(
          label = stringResource(R.string.rental_detail_price_label),
          value = stringResource(R.string.rental_detail_price, uiState.rentalPrice),
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
          RatingLine(averageRating = person.averageRating, reviewCount = person.reviewCount)
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

/** A star and "4.6 · 5 reviews", or "No reviews yet" when [reviewCount] is 0. */
@Composable
private fun RatingLine(averageRating: Double, reviewCount: Int) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    if (reviewCount > 0) {
      Icon(
          Icons.Default.Star,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.tertiary,
          modifier = Modifier.size(14.dp),
      )
      Spacer(Modifier.width(4.dp))
    }
    Text(
        text =
            if (reviewCount > 0)
                pluralStringResource(
                    R.plurals.profile_rating,
                    reviewCount,
                    averageRating,
                    reviewCount,
                )
            else stringResource(R.string.profile_no_reviews),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag(C.Tag.rental_detail_person_rating),
    )
  }
}

/**
 * The exact meeting point once the rental is accepted (Figma frame 13.01): a map box, the pickup
 * address, and the drop-off address. A missing address reads as "not known yet".
 */
@Composable
private fun PickupPoint(uiState: RentalDetailUiState) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
        text = stringResource(R.string.rental_detail_pickup_point_label),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag(C.Tag.rental_detail_pickup_point_label),
    )
    // A placeholder for the map, as on the pickup area.
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
          text =
              uiState.pickupAddress.ifBlank {
                stringResource(R.string.rental_detail_address_placeholder)
              },
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.testTag(C.Tag.rental_detail_pickup_address),
      )
    }
    Text(
        text =
            stringResource(
                R.string.rental_detail_return_address,
                uiState.returnAddress.ifBlank {
                  stringResource(R.string.rental_detail_address_placeholder)
                },
            ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag(C.Tag.rental_detail_return_address),
    )
  }
}

/**
 * The public pickup area: a map box, the area's name (or a placeholder until the listing is
 * geocoded), and a note that the exact address comes after acceptance.
 */
@Composable
private fun PickupArea(area: String) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
        text = stringResource(R.string.rental_detail_pickup_label),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    // A placeholder for the map, which the map issue draws here. Keep the tag: its tests and the
    // map's will find the box by it.
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
 * A "Cancel request" button, on the right, and on its left the last day before pickup, written "Wed
 * 30 Sep" (with the year when it is not [today]'s). [lastCancelDay] is null when the rental has no
 * start date.
 */
@Composable
private fun CancelRow(lastCancelDay: LocalDate?, today: LocalDate) {
  val locale = LocalConfiguration.current.locales[0]
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
    // cancel issue adds. Until then the button is only shown.
    TextButton(onClick = {}, modifier = Modifier.testTag(C.Tag.rental_detail_cancel_button)) {
      Text(
          text = stringResource(R.string.rental_detail_cancel_request),
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.Bold,
      )
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
          today = LocalDate.of(2026, 9, 28),
      )
    }
  }
}
