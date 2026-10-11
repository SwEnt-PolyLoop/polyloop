// Made with claude
package com.swent.polyloop.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.polyloop.R
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.model.listing.ListingFilter
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.theme.PolyLoopTheme

/**
 * The search bar and the category chips of the Browse screen. Stateless: it only draws [filter] and
 * reports the user's actions, the ViewModel decides what they do.
 *
 * "All" is selected when no category is selected, matching [ListingFilter.categories] being empty.
 */
@Composable
fun BrowseFilters(
    filter: ListingFilter,
    onQueryChange: (String) -> Unit,
    onCategoryClick: (ListingCategory) -> Unit,
    onAllClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
    SearchField(filter.query, onQueryChange)

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.testTag(C.Tag.browse_category_chips),
    ) {
      item {
        FilterBubble(
            label = stringResource(R.string.browse_category_all),
            selected = filter.categories.isEmpty(),
            onClick = onAllClick,
            testTag = C.Tag.browse_category_all,
        )
      }
      items(ListingCategory.entries) { category ->
        FilterBubble(
            label = category.label,
            selected = category in filter.categories,
            onClick = { onCategoryClick(category) },
            testTag = C.Tag.browseCategoryChip(category),
        )
      }
    }
  }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
  val colors = MaterialTheme.colorScheme
  OutlinedTextField(
      value = query,
      onValueChange = onQueryChange,
      placeholder = {
        Text(
            stringResource(R.string.browse_search_hint),
            color = colors.onSurfaceVariant,
            fontSize = 17.sp,
        )
      },
      leadingIcon = {
        Icon(Icons.Default.Search, contentDescription = null, tint = colors.onSurfaceVariant)
      },
      trailingIcon = {
        if (query.isNotEmpty()) {
          IconButton(
              onClick = { onQueryChange("") },
              modifier = Modifier.testTag(C.Tag.browse_clear_search),
          ) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.browse_clear_search),
            )
          }
        }
      },
      singleLine = true,
      shape = RoundedCornerShape(16.dp),
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
      textStyle = TextStyle(fontSize = 17.sp, color = colors.onSurface),
      colors =
          OutlinedTextFieldDefaults.colors(
              focusedContainerColor = colors.surface,
              unfocusedContainerColor = colors.surface,
              focusedBorderColor = colors.onSurface,
              unfocusedBorderColor = colors.outlineVariant,
              cursorColor = colors.primary,
          ),
      modifier = Modifier.fillMaxWidth().testTag(C.Tag.browse_search_bar),
  )
}

/** One rounded chip: tinted with the primary color when selected, grey otherwise. */
@Composable
private fun FilterBubble(label: String, selected: Boolean, onClick: () -> Unit, testTag: String) {
  val colors = MaterialTheme.colorScheme
  FilterChip(
      selected = selected,
      onClick = onClick,
      label = { Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium) },
      shape = CircleShape,
      border = null,
      colors =
          FilterChipDefaults.filterChipColors(
              containerColor = colors.surfaceVariant,
              labelColor = colors.onSurface,
              selectedContainerColor = colors.primary.copy(alpha = 0.12f),
              selectedLabelColor = colors.primary,
          ),
      modifier = Modifier.testTag(testTag),
  )
}

@Preview(showBackground = true)
@Composable
private fun BrowseFiltersPreview() {
  PolyLoopTheme {
    BrowseFilters(
        filter = ListingFilter(),
        onQueryChange = {},
        onCategoryClick = {},
        onAllClick = {},
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun BrowseFiltersSelectedPreview() {
  PolyLoopTheme {
    BrowseFilters(
        filter =
            ListingFilter(
                query = "tent",
                categories = setOfNotNull(ListingCategory.entries.getOrNull(1)),
            ),
        onQueryChange = {},
        onCategoryClick = {},
        onAllClick = {},
    )
  }
}
