// Made with Claude.

package com.swent.polyloop.ui.createlisting

import androidx.annotation.StringRes
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.swent.polyloop.R
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.listingdetail.formatRangeEnds
import java.time.LocalDate

/** A field with its [label] above it, then its error (if any) and an optional [helperText]. */
@Composable
internal fun LabelledField(
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
internal fun HelperText(text: String) {
  Text(
      text = text,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

/** A full-width text field; multi-line when [minLines] is above 1. */
@Composable
internal fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    tag: String,
    label: String,
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
      modifier = Modifier.fillMaxWidth().testTag(tag).semantics { contentDescription = label },
  )
}

/** A read-only field showing the chosen category, opening a menu of every [ListingCategory]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategoryDropdown(
    category: ListingCategory?,
    isError: Boolean,
    label: String,
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
                .testTag(C.Tag.create_listing_category_field)
                .semantics { contentDescription = label },
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
internal fun AvailabilityField(
    from: LocalDate?,
    to: LocalDate?,
    isError: Boolean,
    label: String,
    onClick: () -> Unit,
) {
  val text =
      if (from != null && to != null) {
        val (start, end) = formatRangeEnds(from, to, LocalConfiguration.current.locales[0])
        stringResource(R.string.listing_detail_date_range, start, end)
      } else {
        ""
      }
  val pickDatesLabel = stringResource(R.string.create_listing_availability_placeholder)
  // A press interrupted by a scroll ends as a Cancel, so only a real tap opens the picker.
  val interactionSource = remember { MutableInteractionSource() }
  val currentOnClick by rememberUpdatedState(onClick)
  LaunchedEffect(interactionSource) {
    interactionSource.interactions.collect { if (it is PressInteraction.Release) currentOnClick() }
  }
  OutlinedTextField(
      value = text,
      onValueChange = {},
      readOnly = true,
      placeholder = { Text(pickDatesLabel) },
      trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
      isError = isError,
      singleLine = true,
      shape = MaterialTheme.shapes.medium,
      interactionSource = interactionSource,
      modifier =
          Modifier.fillMaxWidth().testTag(C.Tag.create_listing_availability_field).semantics {
            contentDescription = label
            // The text field's own accessibility click only focuses it and emits no press.
            onClick(label = pickDatesLabel) {
              currentOnClick()
              true
            }
          },
  )
}

/** The pinned bottom bar with the full-width "Publish" button, like Listing detail's RequestBar. */
@Composable
internal fun PublishBar(isPublishing: Boolean, onPublish: () -> Unit) {
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
