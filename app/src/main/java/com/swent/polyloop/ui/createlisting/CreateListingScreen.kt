// Made with Claude.

package com.swent.polyloop.ui.createlisting

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swent.polyloop.R
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.components.DateRangePickerMode
import com.swent.polyloop.ui.components.PolyLoopDateRangePicker
import com.swent.polyloop.ui.theme.PolyLoopTheme
import java.time.LocalDate

private const val DESCRIPTION_MIN_LINES = 3

/** The Create listing screen, showing the form held by [viewModel]. */
@Composable
fun CreateListingScreen(
    viewModel: CreateListingViewModel,
    modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  var cameraPermissionGranted by rememberSaveable { mutableStateOf<Boolean?>(null) }
  val cameraPermissionLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraPermissionGranted = granted
        if (granted) viewModel.openCamera()
      }
  CreateListingContent(
      uiState = uiState,
      onTitleChange = viewModel::onTitleChange,
      onDescriptionChange = viewModel::onDescriptionChange,
      onCategoryChange = viewModel::onCategoryChange,
      onPricePerDayChange = viewModel::onPricePerDayChange,
      onItemValueChange = viewModel::onItemValueChange,
      onPickupAddressChange = viewModel::onPickupAddressChange,
      onDropOffAddressChange = viewModel::onDropOffAddressChange,
      onPickDates = viewModel::openDatePicker,
      onConfirmDates = viewModel::selectDates,
      onDismissDatePicker = viewModel::dismissDatePicker,
      onAddPhoto = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
      onRemovePhoto = viewModel::onRemovePhoto,
      onPhotoTaken = viewModel::onPhotoTaken,
      onCloseCamera = viewModel::closeCamera,
      onPublish = viewModel::onPublish,
      modifier = modifier,
      cameraPermissionGranted = cameraPermissionGranted,
  )
}

/**
 * Stateless Create listing content: the photos card, the form, and a pinned "Publish" button. The
 * app's top bar is drawn by the navigation scaffold, not here.
 *
 * Like the Sign-up / login screen, an empty required field shows its error only once
 * [CreateListingUiState.hasAttemptedPublish] is true, while a price or item value that is not a
 * whole number from 1 to Listing.MAX_AMOUNT, and dates in the wrong order or on the same day, show
 * theirs right away. While the date picker is open, its sheet is shown over the form, with [today]
 * as the current date. While the camera is open, [PolyLoopCamera] fills the whole screen in a
 * full-screen dialog, over the form, the Publish bar and the top bar; Back closes it.
 * [cameraPermissionGranted] is the answer to the last camera permission request, null if it was
 * never asked; a refusal shows how to allow the camera.
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
    onConfirmDates: (start: LocalDate, end: LocalDate) -> Unit,
    onDismissDatePicker: () -> Unit,
    onAddPhoto: () -> Unit,
    onRemovePhoto: (index: Int) -> Unit,
    onPhotoTaken: (uri: String) -> Unit,
    onCloseCamera: () -> Unit,
    onPublish: () -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
    cameraPermissionGranted: Boolean? = null,
) {
  val attempted = uiState.hasAttemptedPublish
  val addressPlaceholder = stringResource(R.string.create_listing_address_placeholder)
  val pp = stringResource(R.string.create_listing_pp_suffix)
  val titleLabel = stringResource(R.string.create_listing_title_label)
  val categoryLabel = stringResource(R.string.create_listing_category_label)
  val descriptionLabel = stringResource(R.string.create_listing_description_label)
  val priceLabel = stringResource(R.string.create_listing_price_label)
  val itemValueLabel = stringResource(R.string.create_listing_item_value_label)
  val availabilityLabel = stringResource(R.string.create_listing_availability_label)
  val pickupLabel = stringResource(R.string.create_listing_pickup_label)
  val dropOffLabel = stringResource(R.string.create_listing_drop_off_label)
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
          showError = attempted && !uiState.hasValidPhotoCount,
          onAddPhoto = onAddPhoto,
          onRemovePhoto = onRemovePhoto,
      )
      CameraPermissionMessage(cameraPermissionGranted)
      LabelledField(
          label = titleLabel,
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
            label = titleLabel,
            tag = C.Tag.create_listing_title_field,
        )
      }
      LabelledField(
          label = categoryLabel,
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
            label = categoryLabel,
            onCategoryChange = onCategoryChange,
        )
      }
      LabelledField(
          label = descriptionLabel,
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
            label = descriptionLabel,
            tag = C.Tag.create_listing_description_field,
            minLines = DESCRIPTION_MIN_LINES,
        )
      }
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          LabelledField(
              label = priceLabel,
              errorRes =
                  amountError(
                      uiState.pricePerDay,
                      uiState.isPricePerDayValid,
                      attempted,
                      R.string.create_listing_price_required,
                      R.string.create_listing_amount_invalid,
                  ),
              errorTag = C.Tag.create_listing_price_error,
              modifier = Modifier.weight(1f),
          ) { isError ->
            FormTextField(
                value = uiState.pricePerDay,
                onValueChange = onPricePerDayChange,
                isError = isError,
                label = priceLabel,
                tag = C.Tag.create_listing_price_field,
                keyboardType = KeyboardType.Number,
                suffix = pp,
            )
          }
          LabelledField(
              label = itemValueLabel,
              errorRes =
                  amountError(
                      uiState.itemValue,
                      uiState.isItemValueValid,
                      attempted,
                      R.string.create_listing_item_value_required,
                      R.string.create_listing_amount_invalid,
                  ),
              errorTag = C.Tag.create_listing_item_value_error,
              modifier = Modifier.weight(1f),
          ) { isError ->
            FormTextField(
                value = uiState.itemValue,
                onValueChange = onItemValueChange,
                isError = isError,
                label = itemValueLabel,
                tag = C.Tag.create_listing_item_value_field,
                keyboardType = KeyboardType.Number,
                suffix = pp,
            )
          }
        }
        HelperText(stringResource(R.string.create_listing_deposit_helper))
      }
      LabelledField(
          label = availabilityLabel,
          errorRes = availabilityError(uiState),
          errorTag = C.Tag.create_listing_availability_error,
      ) { isError ->
        AvailabilityField(
            from = uiState.availableFrom,
            to = uiState.availableTo,
            isError = isError,
            label = availabilityLabel,
            onClick = onPickDates,
        )
      }
      LabelledField(
          label = pickupLabel,
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
            label = pickupLabel,
            tag = C.Tag.create_listing_pickup_field,
            placeholder = addressPlaceholder,
        )
      }
      LabelledField(
          label = dropOffLabel,
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
            label = dropOffLabel,
            tag = C.Tag.create_listing_drop_off_field,
            placeholder = addressPlaceholder,
        )
      }
    }
    PublishBar(isPublishing = uiState.isPublishing, onPublish = onPublish)
    if (uiState.isDatePickerOpen) {
      PolyLoopDateRangePicker(
          window = today..today.plusYears(1),
          disabledDates = emptySet(),
          mode = DateRangePickerMode.Availability,
          onConfirm = onConfirmDates,
          onDismiss = onDismissDatePicker,
          today = today,
      )
    }
    if (uiState.isCameraOpen) {
      // Its own window, so the camera covers the whole screen: the Publish bar and the top bar too.
      // Back dismisses the dialog, which closes the camera.
      Dialog(
          onDismissRequest = onCloseCamera,
          properties =
              DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
      ) {
        PolyLoopCamera(
            photoCount = uiState.photos.size,
            onPhotoTaken = onPhotoTaken,
            onClose = onCloseCamera,
        )
      }
    }
  }
}

/** The error of a required field: shown only once the user has pressed "Publish". */
@StringRes
private fun requiredError(isMissing: Boolean, hasAttemptedPublish: Boolean, @StringRes res: Int) =
    if (isMissing && hasAttemptedPublish) res else null

/**
 * A typed amount that is not a whole number from 1 to Listing.MAX_AMOUNT shows its error right
 * away.
 */
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

/**
 * Dates in the wrong order or on the same day show their error right away, missing dates only after
 * "Publish".
 */
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
                availableTo = LocalDate.of(2026, 10, 5),
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
        onConfirmDates = { _, _ -> },
        onDismissDatePicker = {},
        onAddPhoto = {},
        onRemovePhoto = {},
        onPhotoTaken = {},
        onCloseCamera = {},
        onPublish = {},
    )
  }
}
