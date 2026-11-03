// Made with Claude.

package com.swent.polyloop.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.swent.polyloop.R
import com.swent.polyloop.resources.C

/**
 * The transparent top bar shared by every signed-in screen: a menu button that opens the navigation
 * drawer ([PolyLoopDrawerContent]), and a profile button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PolyLoopTopBar(
    onMenuClick: () -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  TopAppBar(
      title = {},
      modifier = modifier.testTag(C.Tag.top_bar),
      navigationIcon = {
        IconButton(
            onClick = onMenuClick,
            modifier = Modifier.testTag(C.Tag.top_bar_menu_button),
        ) {
          Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.top_bar_menu))
        }
      },
      actions = {
        IconButton(
            onClick = onProfileClick,
            modifier = Modifier.testTag(C.Tag.top_bar_profile_button),
        ) {
          Icon(
              Icons.Default.AccountCircle,
              contentDescription = stringResource(R.string.top_bar_profile),
          )
        }
      },
      colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
  )
}
