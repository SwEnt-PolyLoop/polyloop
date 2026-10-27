// Made with Claude.

package com.swent.polyloop.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.swent.polyloop.R
import com.swent.polyloop.model.profile.UserProfile
import com.swent.polyloop.resources.C

/**
 * The navigation drawer opened by the top bar's menu button (Figma frame 03): a header with the
 * signed-in user's [profile] (placeholders while it is null), one item per destination with the
 * [currentRoute]'s item highlighted, then, after a divider, Admin court ruling with an "Admin"
 * badge when it is in [destinations], and a footer.
 */
@Composable
fun PolyLoopDrawerContent(
    profile: UserProfile?,
    destinations: List<Screen.TopLevel>,
    currentRoute: String?,
    onDestinationClick: (Screen.TopLevel) -> Unit,
    modifier: Modifier = Modifier,
) {
  ModalDrawerSheet(modifier = modifier.testTag(C.Tag.drawer)) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
      DrawerHeader(profile)
      HorizontalDivider()
      Spacer(Modifier.size(8.dp))
      val (adminItems, items) = destinations.partition { it == Screen.AdminCourtRuling }
      items.forEach { DrawerItem(it, currentRoute == it.route, onDestinationClick) }
      if (adminItems.isNotEmpty()) {
        HorizontalDivider(modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
        adminItems.forEach {
          DrawerItem(it, currentRoute == it.route, onDestinationClick, showAdminBadge = true)
        }
      }
      Text(
          text = stringResource(R.string.drawer_footer),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(28.dp).testTag(C.Tag.drawer_footer),
      )
    }
  }
}

/** Avatar (photo, or the initial without one), name, and rating with the number of reviews. */
@Composable
private fun DrawerHeader(profile: UserProfile?) {
  val name = profile?.name.orEmpty()
  Row(
      modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Box(
        modifier =
            Modifier.size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
      if (profile != null && profile.photoUrl.isNotBlank()) {
        AsyncImage(
            model = profile.photoUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().testTag(C.Tag.drawer_avatar_photo),
        )
      } else {
        Text(
            text = name.trim().firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag(C.Tag.drawer_avatar_initial),
        )
      }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(
          text = name.ifBlank { stringResource(R.string.profile_name_placeholder) },
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.testTag(C.Tag.drawer_name),
      )
      val reviewCount = profile?.reviewCount ?: 0
      Row(verticalAlignment = Alignment.CenterVertically) {
        if (reviewCount > 0) {
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
                if (profile != null && reviewCount > 0)
                    pluralStringResource(
                        R.plurals.profile_rating,
                        reviewCount,
                        profile.averageRating,
                        reviewCount,
                    )
                else stringResource(R.string.profile_no_reviews),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(C.Tag.drawer_rating),
        )
      }
    }
  }
}

@Composable
private fun DrawerItem(
    screen: Screen.TopLevel,
    selected: Boolean,
    onClick: (Screen.TopLevel) -> Unit,
    showAdminBadge: Boolean = false,
) {
  NavigationDrawerItem(
      label = { Text(stringResource(screen.labelRes)) },
      icon = { Icon(screen.iconPainter(), contentDescription = null) },
      badge = {
        if (showAdminBadge) {
          Badge(modifier = Modifier.testTag(C.Tag.drawer_admin_badge)) {
            Text(stringResource(R.string.drawer_admin_badge))
          }
        }
      },
      selected = selected,
      onClick = { onClick(screen) },
      modifier = Modifier.padding(horizontal = 12.dp).testTag(C.Tag.topBarMenuItem(screen.route)),
  )
}

/** Core Material icons where one fits, the app's own outlined drawables otherwise. */
@Composable
private fun Screen.TopLevel.iconPainter(): Painter =
    when (this) {
      Screen.Browse -> rememberVectorPainter(Icons.Default.Search)
      Screen.CreateListing -> rememberVectorPainter(Icons.Default.Add)
      Screen.MyRentals -> painterResource(R.drawable.ic_package)
      Screen.ChatList -> painterResource(R.drawable.ic_chat)
      Screen.Wallet -> painterResource(R.drawable.ic_wallet)
      Screen.Profile -> rememberVectorPainter(Icons.Default.Person)
      Screen.AdminCourtRuling -> painterResource(R.drawable.ic_gavel)
    }

@Preview(showBackground = true)
@Composable
private fun PolyLoopDrawerContentPreview() {
  PolyLoopDrawerContent(
      profile =
          UserProfile(
              uid = "me",
              name = "Yacine",
              email = "yacine@epfl.ch",
              averageRating = 4.8,
              reviewCount = 12,
          ),
      destinations = Screen.topLevelDestinations(isAdmin = true),
      currentRoute = Screen.Browse.route,
      onDestinationClick = {},
  )
}
