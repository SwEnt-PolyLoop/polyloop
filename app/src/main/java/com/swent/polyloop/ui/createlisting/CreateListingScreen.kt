// Made with Claude.

package com.swent.polyloop.ui.createlisting

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.swent.polyloop.R
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.components.ListingPhoto
import com.swent.polyloop.ui.listingdetail.formatRangeEnds
import com.swent.polyloop.ui.theme.PolyLoopTheme
import java.time.LocalDate

private val PHOTO_TILE_SIZE = 88.dp
private val PHOTO_TILE_CORNER = 12.dp
private const val DESCRIPTION_MIN_LINES = 3

/**
 * Stateless Create listing content: the photos card, the form, and a pinned "Publish" button. The
 * app's top bar is drawn by the navigation scaffold, not here.
 *
 * Like the Sign-up / login screen, an empty required field shows its error only once
 * [CreateListingUiState.hasAttemptedPublish] is true, while a price or item value that is not a
 * whole number above 0, and dates in the wrong order, show theirs right away.
 */
@Composable
fun CreateListingContent(
    uiState: CreateListingUiState,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onCategoryChange: (ListingCategory) -> Unit,
    onPricePerDayChange: (String) -> Unit,
    onItemValueChange: (String) -> Unit,
    onPickupAddressChange: (String) -> Unit,
    onDropOffAddressChange: (String) -> Unit,
    onPickDates: () -> Unit,
    onAddPhoto: () -> Unit,
    onRemovePhoto: (index: Int) -> Unit,
    onPublish: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val attempted = uiState.hasAttemptedPublish
  val addressPlaceholder = stringResource(R.string.create_listing_address_placeholder)
  val pp = stringResource(R.string.create_listing_pp_suffix)
  Column(
      modifier =
          modifier
              .fillMaxSize()
              .background(MaterialTheme.colorScheme.background)
              .testTag(C.Tag.create_listing_screen)
  ) {
    Column(
        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text(
          text = stringResource(R.string.create_listing_headline),
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
      )
      PhotosCard(
          photos = uiState.photos,
          canAddPhoto = uiState.canAddPhoto,
          missingPhotos = uiState.missingPhotos,
          onAddPhoto = onAddPhoto,
          onRemovePhoto = onRemovePhoto,
      )
      LabelledField(
          label = stringResource(R.string.create_listing_title_label),
          errorRes =
              requiredError(
                  uiState.isTitleBlank,
                  attempted,
                  R.string.create_listing_title_required,
              ),
          errorTag = C.Tag.create_listing_title_error,
      ) { isError ->
        FormTextField(
            value = uiState.title,
            onValueChange = onTitleChange,
            isError = isError,
            tag = C.Tag.create_listing_title_field,
        )
      }
      LabelledField(
          label = stringResource(R.string.create_listing_category_label),
          errorRes =
              requiredError(
                  uiState.isCategoryMissing,
                  attempted,
                  R.string.create_listing_category_required,
              ),
          errorTag = C.Tag.create_listing_category_error,
      ) { isError ->
        CategoryDropdown(
            category = uiState.category,
            isError = isError,
            onCategoryChange = onCategoryChange,
        )
      }
      LabelledField(
          label = stringResource(R.string.create_listing_description_label),
          errorRes =
              requiredError(
                  uiState.isDescriptionBlank,
                  attempted,
                  R.string.create_listing_description_required,
              ),
          errorTag = C.Tag.create_listing_description_error,
      ) { isError ->
        FormTextField(
            value = uiState.description,
            onValueChange = onDescriptionChange,
            isError = isError,
            tag = C.Tag.create_listing_description_field,
            minLines = DESCRIPTION_MIN_LINES,
        )
      }
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          LabelledField(
              label = stringResource(R.string.create_listing_price_label),
              errorRes =
                  amountError(
                      uiState.pricePerDay,
                      uiState.isPricePerDayValid,
                      attempted,
                      R.string.create_listing_price_required,
                      R.string.create_listing_price_invalid,
                  ),
              errorTag = C.Tag.create_listing_price_error,
              modifier = Modifier.weight(1f),
          ) { isError ->
            FormTextField(
                value = uiState.pricePerDay,
                onValueChange = onPricePerDayChange,
                isError = isError,
                tag = C.Tag.create_listing_price_field,
                keyboardType = KeyboardType.Number,
                suffix = pp,
            )
          }
          LabelledField(
              label = stringResource(R.string.create_listing_item_value_label),
              errorRes =
                  amountError(
                      uiState.itemValue,
                      uiState.isItemValueValid,
                      attempted,
                      R.string.create_listing_item_value_required,
                      R.string.create_listing_item_value_invalid,
                  ),
              errorTag = C.Tag.create_listing_item_value_error,
              modifier = Modifier.weight(1f),
          ) { isError ->
            FormTextField(
                value = uiState.itemValue,
                onValueChange = onItemValueChange,
                isError = isError,
                tag = C.Tag.create_listing_item_value_field,
                keyboardType = KeyboardType.Number,
                suffix = pp,
            )
          }
        }
        HelperText(stringResource(R.string.create_listing_deposit_helper))
      }
      LabelledField(
          label = stringResource(R.string.create_listing_availability_label),
          errorRes = availabilityError(uiState),
          errorTag = C.Tag.create_listing_availability_error,
      ) { isError ->
        AvailabilityField(
            from = uiState.availableFrom,
            to = uiState.availableTo,
            isError = isError,
            onClick = onPickDates,
        )
      }
      LabelledField(
          label = stringResource(R.string.create_listing_pickup_label),
          errorRes =
              requiredError(
                  uiState.isPickupAddressBlank,
                  attempted,
                  R.string.create_listing_pickup_required,
              ),
          errorTag = C.Tag.create_listing_pickup_error,
      ) { isError ->
        FormTextField(
            value = uiState.pickupAddress,
            onValueChange = onPickupAddressChange,
            isError = isError,
            tag = C.Tag.create_listing_pickup_field,
            placeholder = addressPlaceholder,
        )
      }
      LabelledField(
          label = stringResource(R.string.create_listing_drop_off_label),
          errorRes =
              requiredError(
                  uiState.isDropOffAddressBlank,
                  attempted,
                  R.string.create_listing_drop_off_required,
              ),
          errorTag = C.Tag.create_listing_drop_off_error,
          helperText = stringResource(R.string.create_listing_address_helper),
      ) { isError ->
        FormTextField(
            value = uiState.dropOffAddress,
            onValueChange = onDropOffAddressChange,
            isError = isError,
            tag = C.Tag.create_listing_drop_off_field,
            placeholder = addressPlaceholder,
        )
      }
    }
    PublishBar(isPublishing = uiState.isPublishing, onPublish = onPublish)
  }
}

/** The error of a required field: shown only once the user has pressed "Publish". */
@StringRes
private fun requiredError(isMissing: Boolean, hasAttemptedPublish: Boolean, @StringRes res: Int) =
    if (isMissing && hasAttemptedPublish) res else null

/** A typed amount that is not a whole number above 0 shows its error right away. */
@StringRes
private fun amountError(
    text: String,
    isValid: Boolean,
    hasAttemptedPublish: Boolean,
    @StringRes required: Int,
    @StringRes invalid: Int,
): Int? =
    when {
      isValid -> null
      text.isNotEmpty() -> invalid
      else -> requiredError(isMissing = true, hasAttemptedPublish, required)
    }

/** Dates in the wrong order show their error right away, missing dates only after "Publish". */
@StringRes
private fun availabilityError(uiState: CreateListingUiState): Int? =
    when {
      uiState.isAvailabilityValid -> null
      uiState.availableFrom != null && uiState.availableTo != null ->
          R.string.create_listing_availability_invalid
      else ->
          requiredError(
              isMissing = true,
              uiState.hasAttemptedPublish,
              R.string.create_listing_availability_required,
          )
    }

/**
 * The photo row with a remove button on each photo, the "Add photo" tile while more photos fit, and
 * the number of photos still needed to publish.
 */
@Composable
private fun PhotosCard(
    photos: List<String>,
    canAddPhoto: Boolean,
    missingPhotos: Int,
    onAddPhoto: () -> Unit,
    onRemovePhoto: (index: Int) -> Unit,
) {
  OutlinedCard(modifier = Modifier.fillMaxWidth()) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.create_listing_photos),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Text(
            text =
                stringResource(
                    R.string.create_listing_photo_count,
                    photos.size,
                    Listing.MAX_PHOTOS,
                    Listing.MIN_PHOTOS,
                ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(C.Tag.create_listing_photos_counter),
        )
      }
      Row(
          modifier = Modifier.horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        photos.forEachIndexed { index, uri ->
          PhotoTile(uri = uri, index = index, onRemove = { onRemovePhoto(index) })
        }
        if (canAddPhoto) {
          AddPhotoTile(onClick = onAddPhoto)
        }
      }
      if (missingPhotos > 0) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Icon(
              painter = painterResource(R.drawable.ic_error_outline),
              contentDescription = null,
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.size(16.dp),
          )
          Text(
              text =
                  pluralStringResource(
                      R.plurals.create_listing_missing_photos,
                      missingPhotos,
                      missingPhotos,
                  ),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
              modifier = Modifier.testTag(C.Tag.create_listing_photos_hint),
          )
        }
      }
    }
  }
}

/** A square photo with a small remove button in its corner. */
@Composable
private fun PhotoTile(uri: String, index: Int, onRemove: () -> Unit) {
  Box(modifier = Modifier.size(PHOTO_TILE_SIZE).clip(RoundedCornerShape(PHOTO_TILE_CORNER))) {
    ListingPhoto(
        url = uri,
        contentDescription = stringResource(R.string.create_listing_photo_description, index + 1),
        modifier = Modifier.fillMaxSize().testTag(C.Tag.create_listing_photo_ + index),
    )
    Surface(
        onClick = onRemove,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        modifier =
            Modifier.align(Alignment.TopEnd)
                .padding(4.dp)
                .size(24.dp)
                .testTag(C.Tag.create_listing_remove_photo_ + index),
    ) {
      Icon(
          Icons.Default.Close,
          contentDescription = stringResource(R.string.create_listing_remove_photo, index + 1),
          modifier = Modifier.padding(4.dp),
      )
    }
  }
}

/** The dashed "Add photo" tile, which will open the in-app camera. */
@Composable
private fun AddPhotoTile(onClick: () -> Unit) {
  val borderColor = MaterialTheme.colorScheme.outline
  val contentColor = MaterialTheme.colorScheme.primary
  Column(
      modifier =
          Modifier.size(PHOTO_TILE_SIZE)
              .clip(RoundedCornerShape(PHOTO_TILE_CORNER))
              .drawBehind {
                drawRoundRect(
                    color = borderColor,
                    cornerRadius = CornerRadius(PHOTO_TILE_CORNER.toPx()),
                    style =
                        Stroke(
                            width = 1.dp.toPx(),
                            pathEffect =
                                PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                        ),
                )
              }
              .clickable(onClick = onClick)
              .testTag(C.Tag.create_listing_add_photo),
      verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Icon(
        painter = painterResource(R.drawable.ic_camera),
        contentDescription = null,
        tint = contentColor,
    )
    Text(
        text = stringResource(R.string.create_listing_add_photo),
        style = MaterialTheme.typography.labelMedium,
        color = contentColor,
    )
  }
}

/** A field with its [label] above it, then its error (if any) and an optional [helperText]. */
@Composable
private fun LabelledField(
    label: String,
    @StringRes errorRes: Int?,
    errorTag: String,
    modifier: Modifier = Modifier,
    helperText: String? = null,
    field: @Composable (isError: Boolean) -> Unit,
) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(text = label, style = MaterialTheme.typography.labelLarge)
    field(errorRes != null)
    if (errorRes != null) {
      Text(
          text = stringResource(errorRes),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.testTag(errorTag),
      )
    }
    helperText?.let { HelperText(it) }
  }
}

@Composable
private fun HelperText(text: String) {
  Text(
      text = text,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

/** A full-width text field; multi-line when [minLines] is above 1. */
@Composable
private fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    tag: String,
    placeholder: String? = null,
    suffix: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    minLines: Int = 1,
) {
  OutlinedTextField(
      value = value,
      onValueChange = onValueChange,
      placeholder = placeholder?.let { { Text(it) } },
      suffix = suffix?.let { { Text(it) } },
      isError = isError,
      singleLine = minLines == 1,
      minLines = minLines,
      keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
      shape = MaterialTheme.shapes.medium,
      modifier = Modifier.fillMaxWidth().testTag(tag),
  )
}

/** A read-only field showing the chosen category, opening a menu of every [ListingCategory]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(
    category: ListingCategory?,
    isError: Boolean,
    onCategoryChange: (ListingCategory) -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }
  ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
    OutlinedTextField(
        value = category?.label.orEmpty(),
        onValueChange = {},
        readOnly = true,
        placeholder = { Text(stringResource(R.string.create_listing_category_placeholder)) },
        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        isError = isError,
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        modifier =
            Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .testTag(C.Tag.create_listing_category_field),
    )
    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      ListingCategory.entries.forEach { option ->
        DropdownMenuItem(
            text = { Text(option.label) },
            onClick = {
              onCategoryChange(option)
              expanded = false
            },
            modifier = Modifier.testTag(C.Tag.create_listing_category_option_ + option.name),
        )
      }
    }
  }
}

/** A read-only field showing the availability window; tapping it calls [onClick]. */
@Composable
private fun AvailabilityField(
    from: LocalDate?,
    to: LocalDate?,
    isError: Boolean,
    onClick: () -> Unit,
) {
  val text =
      if (from != null && to != null) {
        val (start, end) = formatRangeEnds(from, to, LocalConfiguration.current.locales[0])
        stringResource(R.string.listing_detail_date_range, start, end)
      } else {
        ""
      }
  OutlinedTextField(
      value = text,
      onValueChange = {},
      readOnly = true,
      placeholder = { Text(stringResource(R.string.create_listing_availability_placeholder)) },
      trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
      isError = isError,
      singleLine = true,
      shape = MaterialTheme.shapes.medium,
      modifier =
          Modifier.fillMaxWidth()
              .testTag(C.Tag.create_listing_availability_field)
              // A read-only text field keeps taps for itself, so they are caught on the way in.
              .pointerInput(onClick) {
                awaitEachGesture {
                  awaitFirstDown(pass = PointerEventPass.Initial)
                  if (waitForUpOrCancellation(pass = PointerEventPass.Initial) != null) onClick()
                }
              }
              // The tap above is invisible to accessibility services, so expose it as a click too.
              .semantics {
                onClick {
                  onClick()
                  true
                }
              },
  )
}

/** The pinned bottom bar with the full-width "Publish" button, like Listing detail's RequestBar. */
@Composable
private fun PublishBar(isPublishing: Boolean, onPublish: () -> Unit) {
  Surface(color = MaterialTheme.colorScheme.surface) {
    Column {
      HorizontalDivider()
      Button(
          onClick = onPublish,
          enabled = !isPublishing,
          shape = MaterialTheme.shapes.medium,
          modifier =
              Modifier.fillMaxWidth()
                  .padding(16.dp)
                  .height(48.dp)
                  .testTag(C.Tag.create_listing_publish_button),
      ) {
        if (isPublishing) {
          CircularProgressIndicator(
              color = LocalContentColor.current,
              strokeWidth = 2.dp,
              modifier =
                  Modifier.size(ButtonDefaults.IconSize).testTag(C.Tag.create_listing_publishing),
          )
          Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        }
        Text(stringResource(R.string.create_listing_publish))
      }
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun CreateListingContentPreview() {
  PolyLoopTheme {
    CreateListingContent(
        uiState =
            CreateListingUiState(
                title = "Camping tent, 2 people",
                description = "Light 2-person tent, used three times. Comes with pegs and rainfly.",
                category = ListingCategory.SPORTS_OUTDOOR,
                pricePerDay = "15",
                itemValue = "180",
                availableFrom = LocalDate.of(2026, 10, 6),
                availableTo = LocalDate.of(2026, 10, 20),
                photos = List(2) { "" },
            ),
        onTitleChange = {},
        onDescriptionChange = {},
        onCategoryChange = {},
        onPricePerDayChange = {},
        onItemValueChange = {},
        onPickupAddressChange = {},
        onDropOffAddressChange = {},
        onPickDates = {},
        onAddPhoto = {},
        onRemovePhoto = {},
        onPublish = {},
    )
  }
}
