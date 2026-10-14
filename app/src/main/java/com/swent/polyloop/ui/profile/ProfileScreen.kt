// Made with Claude.

package com.swent.polyloop.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swent.polyloop.R
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingStatus
import com.swent.polyloop.model.profile.UserProfile
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.browse.ListingCard

/**
 * The Profile screen, for the signed-in user's own profile or someone else's.
 *
 * @param onListingClick opens one of the own listings.
 * @param onSignedOut called once the user signed out, to leave the screen for sign-in.
 */
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onListingClick: (listingId: String) -> Unit,
    onSignedOut: () -> Unit,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  LaunchedEffect(uiState.isSignedOut) { if (uiState.isSignedOut) onSignedOut() }
  ProfileContent(
      uiState = uiState,
      onListingClick = onListingClick,
      onSignOut = viewModel::signOut,
      onRetry = viewModel::retry,
  )
}

/** The Profile screen for a given [uiState], without a ViewModel (used by tests and previews). */
@Composable
fun ProfileContent(
    uiState: ProfileUiState,
    onListingClick: (listingId: String) -> Unit,
    onSignOut: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Box(
      modifier =
          modifier
              .fillMaxSize()
              .background(MaterialTheme.colorScheme.background)
              .testTag(C.Tag.profile_screen)
  ) {
    val profile = uiState.profile
    when {
      uiState.isLoading ->
          CircularProgressIndicator(
              modifier = Modifier.align(Alignment.Center).testTag(C.Tag.profile_loading)
          )
      uiState.errorMsg != null -> ProfileError(errorMsg = uiState.errorMsg, onRetry = onRetry)
      uiState.profileNotFound || profile == null ->
          Text(
              text = stringResource(R.string.profile_not_found),
              style = MaterialTheme.typography.bodyLarge,
              textAlign = TextAlign.Center,
              modifier =
                  Modifier.align(Alignment.Center).padding(16.dp).testTag(C.Tag.profile_not_found),
          )
      else ->
          ProfileLoaded(
              profile = profile,
              isOwnProfile = uiState.isOwnProfile,
              listings = uiState.listings,
              onListingClick = onListingClick,
              onSignOut = onSignOut,
          )
    }
  }
}

@Composable
private fun ProfileError(errorMsg: String, onRetry: () -> Unit) {
  Column(
      modifier = Modifier.fillMaxSize().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
        text = errorMsg.ifBlank { stringResource(R.string.profile_error_fallback) },
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.testTag(C.Tag.profile_error),
    )
    Button(onClick = onRetry, modifier = Modifier.testTag(C.Tag.profile_retry_button)) {
      Text(stringResource(R.string.profile_retry))
    }
  }
}

/** The header, then, on the own profile only, the listings and "Sign out". */
@Composable
private fun ProfileLoaded(
    profile: UserProfile,
    isOwnProfile: Boolean,
    listings: List<Listing>,
    onListingClick: (listingId: String) -> Unit,
    onSignOut: () -> Unit,
) {
  LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item { ProfileHeader(profile) }
    if (isOwnProfile) {
      item {
        Text(
            text = stringResource(R.string.profile_my_listings),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 12.dp).testTag(C.Tag.profile_listings_title),
        )
      }
      if (listings.isEmpty()) {
        item {
          Text(
              text = stringResource(R.string.profile_no_listings),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.testTag(C.Tag.profile_listings_empty),
          )
        }
      }
      items(listings, key = { it.id }) { listing ->
        ListingCard(
            listing = listing,
            // My listings have no distance; their status takes its place.
            distanceText =
                stringResource(
                    if (listing.status == ListingStatus.DRAFT) R.string.profile_listing_draft
                    else R.string.profile_listing_published
                ),
            onClick = { onListingClick(listing.id) },
        )
      }
      item {
        OutlinedButton(
            onClick = onSignOut,
            modifier =
                Modifier.fillMaxWidth().padding(top = 12.dp).testTag(C.Tag.profile_sign_out_button),
        ) {
          Text(stringResource(R.string.profile_sign_out))
        }
      }
    }
  }
}

/** Avatar with the initial, name, and rating with the number of reviews. */
@Composable
private fun ProfileHeader(profile: UserProfile) {
  Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Box(
        modifier =
            Modifier.size(80.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
      Text(
          text = profile.name.trim().firstOrNull()?.uppercase() ?: "?",
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.testTag(C.Tag.profile_avatar),
      )
    }
    Text(
        text = profile.name.ifBlank { stringResource(R.string.profile_name_placeholder) },
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier.testTag(C.Tag.profile_name),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
      if (profile.reviewCount > 0) {
        Icon(
            Icons.Default.Star,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(4.dp))
      }
      Text(
          text =
              if (profile.reviewCount > 0)
                  pluralStringResource(
                      R.plurals.profile_rating,
                      profile.reviewCount,
                      profile.averageRating,
                      profile.reviewCount,
                  )
              else stringResource(R.string.profile_no_reviews),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.testTag(C.Tag.profile_rating),
      )
    }
  }
}
