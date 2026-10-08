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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.model.listing.ListingFilter
import com.swent.polyloop.ui.theme.PolyLoopTheme

const val BROWSE_SEARCH_FIELD_TAG = "browseSearchField"
const val BROWSE_CLEAR_SEARCH_TAG = "browseClearSearch"
const val BROWSE_CHIP_ALL_TAG = "browseChipAll"

/** Test tag of the chip of [category]. */
fun browseChipTag(category: ListingCategory) = "browseChip_${category.name}"

/**
 * The search bar and the category chips of the Browse screen. Stateless: it only draws [filter] and
 * reports the user's actions, the ViewModel decides what they do.
 *
 * "All" is selected when no category is selected, matching [ListingFilter.categories] being empty.
 *
 * @param categories the chips to show after "All", in order.
 * @param categoryLabel the text of a chip; replace the default with the string resources.
 */
@Composable
fun BrowseFilters(
    filter: ListingFilter,
    onQueryChange: (String) -> Unit,
    onCategoryClick: (ListingCategory) -> Unit,
    onAllClick: () -> Unit,
    modifier: Modifier = Modifier,
    categories: List<ListingCategory> = ListingCategory.entries,
    categoryLabel: (ListingCategory) -> String = { it.defaultLabel() },
) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
    SearchField(filter.query, onQueryChange)

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      item {
        FilterBubble(
            label = "All",
            selected = filter.categories.isEmpty(),
            onClick = onAllClick,
            testTag = BROWSE_CHIP_ALL_TAG,
        )
      }
      items(categories) { category ->
        FilterBubble(
            label = categoryLabel(category),
            selected = category in filter.categories,
            onClick = { onCategoryClick(category) },
            testTag = browseChipTag(category),
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
      placeholder = { Text("Search items", color = colors.onSurfaceVariant, fontSize = 17.sp) },
      leadingIcon = {
        Icon(Icons.Default.Search, contentDescription = null, tint = colors.onSurfaceVariant)
      },
      trailingIcon = {
        if (query.isNotEmpty()) {
          IconButton(
              onClick = { onQueryChange("") },
              modifier = Modifier.testTag(BROWSE_CLEAR_SEARCH_TAG),
          ) {
            Icon(Icons.Default.Close, contentDescription = "Clear search")
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
      modifier = Modifier.fillMaxWidth().testTag(BROWSE_SEARCH_FIELD_TAG),
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

/** Placeholder label ("SPORTS_OUTDOOR" -> "Sports outdoor"); real labels belong in strings.xml. */
private fun ListingCategory.defaultLabel(): String =
    name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

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
