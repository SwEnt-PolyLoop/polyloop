// Made with Claude.

package com.swent.polyloop.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swent.polyloop.R
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.model.listing.ListingFilter
import com.swent.polyloop.model.listing.search
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.theme.PolyLoopTheme

// TODO: temporary until LocationRepository exists; every card shows the same distance.
private const val FAKE_DISTANCE_TEXT = "0.6 km"

/** The Browse screen, showing the published listings loaded by [viewModel]. */
@Composable
fun BrowseScreen(viewModel: BrowseViewModel, onListingClick: (String) -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf(ListingFilter()) }
    BrowseContent(
        uiState = uiState,
        onListingClick = onListingClick,
        onRetry = viewModel::refresh,
        filter = filter,
        onQueryChange = { filter = filter.copy(query = it) },
        onCategoryClick = { category ->
            val categories =
                if (category in filter.categories) filter.categories - category
                else filter.categories + category
            filter = filter.copy(categories = categories)
        },
        onAllClick = { filter = filter.copy(categories = emptySet()) },
    )
}

/**
 * Stateless Browse content: the header with the search bar and category chips, then a loading
 * indicator, an error with a Retry button, an empty state or the filtered list of listings,
 * depending on [uiState]. Only the listings matching [filter] are shown.
 */
@Composable
fun BrowseContent(
    uiState: BrowseUiState,
    onListingClick: (String) -> Unit,
    onRetry: () -> Unit,
    filter: ListingFilter = ListingFilter(),
    onQueryChange: (String) -> Unit = {},
    onCategoryClick: (ListingCategory) -> Unit = {},
    onAllClick: () -> Unit = {},
) {
    Column(
        modifier =
            Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .testTag(C.Tag.browse_screen)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.browse_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag(C.Tag.browse_title),
            )
            BrowseFilters(
                filter = filter,
                onQueryChange = onQueryChange,
                onCategoryClick = onCategoryClick,
                onAllClick = onAllClick,
                modifier = Modifier.testTag(C.Tag.browse_category_chips),
                categoryLabel = { it.label },
            )
            BrowseViewToggle()
        }
        val filteredListings = uiState.listings.search(filter)
        when {
            uiState.isLoading ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.testTag(C.Tag.browse_loading))
                }
            uiState.errorMsg != null -> BrowseError(errorMsg = uiState.errorMsg, onRetry = onRetry)
            filteredListings.isEmpty() ->
                Box(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.browse_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag(C.Tag.browse_empty),
                    )
                }
            else ->
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().testTag(C.Tag.browse_list),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(filteredListings, key = { it.id }) { listing ->
                        ListingCard(
                            listing = listing,
                            distanceText = FAKE_DISTANCE_TEXT,
                            onClick = { onListingClick(listing.id) },
                        )
                    }
                }
        }
    }
}

/** The load error ([errorMsg], or a generic text if it is blank) and a Retry button. */
@Composable
private fun BrowseError(errorMsg: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = errorMsg.ifBlank { stringResource(R.string.browse_error_fallback) },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(C.Tag.browse_error),
        )
        Button(onClick = onRetry, modifier = Modifier.testTag(C.Tag.browse_retry_button)) {
            Text(stringResource(R.string.browse_retry))
        }
    }
}

/** Map | List toggle with List selected. Pressing Map does nothing yet. */
@Composable
private fun BrowseViewToggle() {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = false,
            onClick = {},
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            icon = {},
            modifier = Modifier.testTag(C.Tag.browse_map_toggle),
        ) {
            Text(stringResource(R.string.browse_map))
        }
        SegmentedButton(
            selected = true,
            onClick = {},
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            icon = {},
            modifier = Modifier.testTag(C.Tag.browse_list_toggle),
        ) {
            Text(stringResource(R.string.browse_list))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BrowseContentPreview() {
    PolyLoopTheme {
        BrowseContent(uiState = BrowseUiState(), onListingClick = {}, onRetry = {})
    }
}