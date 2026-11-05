// Made with Claude.

package com.swent.polyloop.ui.listingdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import com.swent.polyloop.R
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.model.listing.ListingStatus
import com.swent.polyloop.model.location.MapPoint
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.components.DateRangePickerMode
import com.swent.polyloop.ui.components.ListingPhoto
import com.swent.polyloop.ui.components.PolyLoopDateRangePicker
import com.swent.polyloop.ui.components.PriceText
import com.swent.polyloop.ui.navigation.PolyLoopTopBar
import com.swent.polyloop.ui.theme.PolyLoopTheme
import java.time.LocalDate

/** Placeholder until the Wallet exists; replace with the user's real balance. */
private const val PLACEHOLDER_BALANCE = 120

// Placeholder until the owner's profile comes from ProfileRepository.
internal const val FAKE_LENDER_RATING = 4.8
internal const val FAKE_LENDER_REVIEW_COUNT = 12

private const val STAR_COUNT = 5

/** Height of the pickup area, whether it shows the map or its placeholder. */
private val PICKUP_AREA_HEIGHT = 120.dp

/** Diameter of the circle drawn on the placeholder that stands in for the map. */
private val PLACEHOLDER_CIRCLE_SIZE = 88.dp

/** Opacity of the pickup circle's fill, on the map and on the placeholder. */
private const val PICKUP_CIRCLE_FILL_ALPHA = 0.2f

/** Width of the pickup circle's outline, on the map and on the placeholder. */
private val PICKUP_CIRCLE_STROKE_WIDTH = 1.dp

/**
 * Radius of the circle on the map, in meters. `geocodeListingAddresses` rounds the public point to
 * a 0.01° grid, so in Switzerland the exact address is at most about 680 m from it.
 */
private const val PICKUP_CIRCLE_RADIUS_METERS = 700.0

/**
 * Zoom of the map: about 2 km from top to bottom around EPFL, so the circle is about as big as the
 * placeholder's and the places around it still show. A whole number, because a lite-mode map may
 * not show fractional zooms.
 */
private const val PICKUP_MAP_ZOOM = 13f

/**
 * The Listing detail screen, showing the listing loaded by [viewModel]. Once a request is sent,
 * [onRequestSent] opens the new rental's chat. The app's top bar is drawn by the navigation
 * scaffold, not here.
 */
@Composable
fun ListingDetailScreen(
    viewModel: ListingDetailViewModel,
    onLenderClick: (ownerId: String) -> Unit,
    onRequestSent: (rentalId: String) -> Unit,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  ListingDetailContent(
      uiState = uiState,
      onRequestDates = viewModel::openDatePicker,
      onConfirmDates = viewModel::sendRequest,
      onDismissDatePicker = viewModel::dismissDatePicker,
      onLenderClick = onLenderClick,
      onRetry = viewModel::retry,
      onDayPicked = viewModel::clearSendError,
      onRequestSent = { rentalId ->
        onRequestSent(rentalId)
        viewModel.onRequestSentHandled()
      },
  )
}

/**
 * Stateless Listing detail content: a loading indicator, an error with a Retry button, a not-found
 * message or the loaded listing, depending on [uiState]. While the date picker is open, its sheet
 * is shown over the loaded listing, with [today] as the current date, and [onDayPicked] is called
 * on each tap on a day. When a request has been sent, the sheet closes and [onRequestSent] is
 * called with the new rental's id.
 */
@Composable
fun ListingDetailContent(
    uiState: ListingDetailUiState,
    onRequestDates: () -> Unit,
    onConfirmDates: (start: LocalDate, end: LocalDate) -> Unit,
    onDismissDatePicker: () -> Unit,
    onLenderClick: (ownerId: String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
    onDayPicked: () -> Unit = {},
    onRequestSent: (rentalId: String) -> Unit = {},
) {
  LaunchedEffect(uiState.sentRentalId) { uiState.sentRentalId?.let(onRequestSent) }
  val listing = uiState.listing
  Column(
      modifier =
          modifier
              .fillMaxSize()
              .background(MaterialTheme.colorScheme.background)
              .testTag(C.Tag.listing_detail_screen)
  ) {
    when {
      uiState.isLoading ->
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.testTag(C.Tag.listing_detail_loading))
          }
      uiState.errorMsg != null -> ListingDetailError(errorMsg = uiState.errorMsg, onRetry = onRetry)
      uiState.listingNotFound || listing == null ->
          Box(
              modifier = Modifier.fillMaxSize().padding(16.dp),
              contentAlignment = Alignment.Center,
          ) {
            Text(
                text = stringResource(R.string.listing_detail_not_found),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag(C.Tag.listing_detail_not_found),
            )
          }
      else -> {
        ListingDetailLoaded(
            listing = listing,
            showRequestButton = !uiState.isOwnListing,
            showMap = !uiState.isOffline,
            onRequestDates = onRequestDates,
            onLenderClick = { onLenderClick(listing.ownerId) },
        )
        if (uiState.isDatePickerOpen && uiState.sentRentalId == null) {
          PolyLoopDateRangePicker(
              window = listing.availableFrom..listing.availableTo,
              disabledDates = uiState.disabledDates,
              mode =
                  DateRangePickerMode.Request(
                      pricePerDay = listing.pricePerDay,
                      balance = PLACEHOLDER_BALANCE,
                  ),
              onConfirm = onConfirmDates,
              onDismiss = onDismissDatePicker,
              today = today,
              isSending = uiState.isSending,
              errorMsg =
                  if (uiState.datesTaken) stringResource(R.string.listing_detail_dates_taken)
                  else uiState.sendError,
              onDayPicked = onDayPicked,
          )
        }
      }
    }
  }
}

/** The load error ([errorMsg], or a generic text if it is blank) and a Retry button. */
@Composable
private fun ListingDetailError(errorMsg: String, onRetry: () -> Unit) {
  Column(
      modifier = Modifier.fillMaxSize().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
        text = errorMsg.ifBlank { stringResource(R.string.listing_detail_error_fallback) },
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.testTag(C.Tag.listing_detail_error),
    )
    Button(onClick = onRetry, modifier = Modifier.testTag(C.Tag.listing_detail_retry_button)) {
      Text(stringResource(R.string.listing_detail_retry))
    }
  }
}

/**
 * The loaded listing for a borrower: photos, title, price, description, the lender, the suggested
 * deposit and availability, the pickup area, and a pinned "Request dates" button unless
 * [showRequestButton] is false (the user's own listing). The pickup area is a map only if [showMap]
 * is true (the phone is online) and the listing has a pickup point; see [PickupArea].
 */
@Composable
private fun ListingDetailLoaded(
    listing: Listing,
    showRequestButton: Boolean,
    showMap: Boolean,
    onRequestDates: () -> Unit,
    onLenderClick: () -> Unit,
) {
  Column(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      PhotoPager(photoUrls = listing.photoUrls)
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = listing.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag(C.Tag.listing_detail_title),
        )
        PriceText(
            pricePerDay = listing.pricePerDay,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag(C.Tag.listing_detail_price),
        )
      }
      Text(
          text = listing.description,
          style = MaterialTheme.typography.bodyLarge,
          modifier = Modifier.testTag(C.Tag.listing_detail_description),
      )
      LenderCard(onClick = onLenderClick)
      InfoCard(listing = listing)
      PickupArea(area = listing.pickupArea, point = listing.pickupPoint, showMap = showMap)
    }
    if (showRequestButton) RequestBar(onRequestDates = onRequestDates)
  }
}

/** Swipeable listing photos with a "current / total" badge. */
@Composable
private fun PhotoPager(photoUrls: List<String>) {
  // One grey page even without photos, so the area keeps its size.
  val pagerState = rememberPagerState { maxOf(photoUrls.size, 1) }
  Box(modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(MaterialTheme.shapes.large)) {
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize().testTag(C.Tag.listing_detail_photos),
    ) { page ->
      ListingPhoto(
          url = photoUrls.getOrNull(page),
          contentDescription =
              if (photoUrls.isEmpty()) null
              else
                  stringResource(
                      R.string.listing_detail_photo_description,
                      page + 1,
                      photoUrls.size,
                  ),
          modifier = Modifier.fillMaxSize().testTag(C.Tag.listing_detail_photo_ + page),
      )
    }
    if (photoUrls.isNotEmpty()) {
      Surface(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.surface,
          modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
      ) {
        Text(
            text =
                stringResource(
                    R.string.listing_detail_photo_count,
                    pagerState.currentPage + 1,
                    photoUrls.size,
                ),
            style = MaterialTheme.typography.labelMedium,
            modifier =
                Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    .testTag(C.Tag.listing_detail_photo_badge),
        )
      }
    }
  }
}

/** The lender with a placeholder name and rating; the whole card opens their profile. */
@Composable
private fun LenderCard(onClick: () -> Unit) {
  val name = stringResource(R.string.listing_detail_lender_placeholder)
  OutlinedCard(
      onClick = onClick,
      modifier = Modifier.fillMaxWidth().testTag(C.Tag.listing_detail_lender_card),
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
            text = name.firstOrNull()?.uppercase().orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag(C.Tag.listing_detail_lender_avatar),
        )
      }
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag(C.Tag.listing_detail_lender_name),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          repeat(STAR_COUNT) {
            Icon(
                Icons.Default.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(14.dp),
            )
          }
          Spacer(Modifier.width(4.dp))
          Text(
              text =
                  pluralStringResource(
                      R.plurals.listing_detail_rating,
                      FAKE_LENDER_REVIEW_COUNT,
                      FAKE_LENDER_RATING,
                      FAKE_LENDER_REVIEW_COUNT,
                  ),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.testTag(C.Tag.listing_detail_lender_rating),
          )
        }
      }
      Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
  }
}

/** Suggested deposit, availability window, and a note that the deposit is agreed in chat. */
@Composable
private fun InfoCard(listing: Listing) {
  val locale = LocalConfiguration.current.locales[0]
  val (from, to) = formatRangeEnds(listing.availableFrom, listing.availableTo, locale)
  OutlinedCard(modifier = Modifier.fillMaxWidth()) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      InfoRow(
          label = stringResource(R.string.listing_detail_suggested_deposit),
          value = stringResource(R.string.listing_card_price, listing.itemValue),
          valueTag = C.Tag.listing_detail_deposit,
      )
      InfoRow(
          label = stringResource(R.string.listing_detail_available),
          value = stringResource(R.string.listing_detail_date_range, from, to),
          valueTag = C.Tag.listing_detail_availability,
      )
      Text(
          text = stringResource(R.string.listing_detail_deposit_note),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.testTag(C.Tag.listing_detail_deposit_note),
      )
    }
  }
}

@Composable
private fun InfoRow(label: String, value: String, valueTag: String) {
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
        fontWeight = FontWeight.Bold,
        modifier = Modifier.testTag(valueTag),
    )
  }
}

/**
 * The pickup area, with a caption chip saying the exact address comes after acceptance. It shows a
 * small map centered on [point] if [showMap] is true; without a [point] (the address is not
 * geocoded yet, or its geocoding failed) or when [showMap] is false (offline, since the map tiles
 * need a connection), it shows a placeholder with the [area] name instead. The caption sits at the
 * top: the Maps SDK draws the Google logo at the bottom-left, and its terms forbid covering it.
 */
@Composable
private fun PickupArea(area: String, point: MapPoint?, showMap: Boolean) {
  Box(
      modifier =
          Modifier.fillMaxWidth()
              .height(PICKUP_AREA_HEIGHT)
              .clip(MaterialTheme.shapes.large)
              .background(MaterialTheme.colorScheme.surfaceVariant)
              .testTag(C.Tag.listing_detail_pickup_area)
  ) {
    if (point != null && showMap) PickupMap(point = point) else PickupPlaceholder(area = area)
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
    ) {
      Text(
          text = stringResource(R.string.listing_detail_pickup_caption),
          style = MaterialTheme.typography.labelSmall,
          modifier =
              Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                  .testTag(C.Tag.listing_detail_pickup_caption),
      )
    }
  }
}

/**
 * A map centered on [point], with a circle over the pickup area. It is a lite-mode map: a still
 * picture that cannot be scrolled or zoomed, so it never takes over the screen's scrolling, and
 * tapping it does nothing instead of opening Google Maps. It shows no exact address.
 */
@Composable
private fun PickupMap(point: MapPoint) {
  val center = LatLng(point.latitude, point.longitude)
  val circleColor = MaterialTheme.colorScheme.primary
  val strokeWidth = with(LocalDensity.current) { PICKUP_CIRCLE_STROKE_WIDTH.toPx() }
  // Keyed on the point, so a new point moves the camera instead of keeping the saved position.
  key(center) {
    GoogleMap(
        modifier = Modifier.fillMaxSize().testTag(C.Tag.listing_detail_pickup_map),
        cameraPositionState =
            rememberCameraPositionState {
              position = CameraPosition.fromLatLngZoom(center, PICKUP_MAP_ZOOM)
            },
        contentDescription = stringResource(R.string.listing_detail_pickup_map_description),
        googleMapOptionsFactory = { GoogleMapOptions().liteMode(true) },
        uiSettings = MapUiSettings(mapToolbarEnabled = false, zoomControlsEnabled = false),
        onMapClick = {},
    ) {
      Circle(
          center = center,
          radius = PICKUP_CIRCLE_RADIUS_METERS,
          fillColor = circleColor.copy(alpha = PICKUP_CIRCLE_FILL_ALPHA),
          strokeColor = circleColor,
          strokeWidth = strokeWidth,
      )
    }
  }
}

/**
 * Stands in for the map: a translucent circle with the [area] name over it, or the circle alone
 * when [area] is blank (the address is not geocoded yet).
 */
@Composable
private fun PickupPlaceholder(area: String) {
  val circleColor = MaterialTheme.colorScheme.primary
  Box(
      modifier = Modifier.fillMaxSize().testTag(C.Tag.listing_detail_pickup_placeholder),
      contentAlignment = Alignment.Center,
  ) {
    Box(
        modifier =
            Modifier.size(PLACEHOLDER_CIRCLE_SIZE)
                .background(circleColor.copy(alpha = PICKUP_CIRCLE_FILL_ALPHA), CircleShape)
                .border(PICKUP_CIRCLE_STROKE_WIDTH, circleColor, CircleShape)
    )
    if (area.isNotBlank()) {
      Text(
          text = area,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier =
              Modifier.padding(horizontal = 16.dp).testTag(C.Tag.listing_detail_pickup_area_name),
      )
    }
  }
}

/** The pinned bottom bar with the full-width "Request dates" button. */
@Composable
private fun RequestBar(onRequestDates: () -> Unit) {
  Surface(color = MaterialTheme.colorScheme.surface) {
    Column {
      HorizontalDivider()
      Button(
          onClick = onRequestDates,
          shape = MaterialTheme.shapes.medium,
          modifier =
              Modifier.fillMaxWidth()
                  .padding(16.dp)
                  .height(48.dp)
                  .testTag(C.Tag.listing_detail_request_button),
      ) {
        Icon(
            Icons.Default.Add,
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize),
        )
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(stringResource(R.string.listing_detail_request_dates))
      }
    }
  }
}

private val previewListing =
    Listing(
        id = "sample-tent",
        ownerId = "sample-owner",
        title = "Camping tent, 2 people",
        description = "Light 2-person tent, used three times. Comes with pegs and a rain cover.",
        category = ListingCategory.SPORTS_OUTDOOR,
        photoUrls = List(5) { "" },
        pricePerDay = 15,
        itemValue = 180,
        availableFrom = LocalDate.of(2026, 10, 6),
        availableTo = LocalDate.of(2026, 10, 20),
        status = ListingStatus.PUBLISHED,
        pickupArea = "Ecublens",
    )

@Preview(showBackground = true)
@Composable
private fun ListingDetailContentPreview() {
  PolyLoopTheme {
    Column {
      PolyLoopTopBar(onMenuClick = {}, onProfileClick = {})
      ListingDetailContent(
          uiState = ListingDetailUiState(listing = previewListing),
          onRequestDates = {},
          onConfirmDates = { _, _ -> },
          onDismissDatePicker = {},
          onLenderClick = {},
          onRetry = {},
      )
    }
  }
}
