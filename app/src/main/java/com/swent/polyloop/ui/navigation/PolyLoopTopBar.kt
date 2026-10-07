// Made with Claude.

package com.swent.polyloop.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.swent.polyloop.R
import com.swent.polyloop.resources.C

/**
 * The transparent top bar shared by every signed-in screen: a menu button that expands to list
 * [destinations], and a profile button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PolyLoopTopBar(
    destinations: List<Screen.TopLevel>,
    onDestinationClick: (Screen.TopLevel) -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  var menuExpanded by remember { mutableStateOf(false) }

  TopAppBar(
      title = {},
      modifier = modifier.testTag(C.Tag.top_bar),
      navigationIcon = {
        Box {
          IconButton(
              onClick = { menuExpanded = true },
              modifier = Modifier.testTag(C.Tag.top_bar_menu_button),
          ) {
            Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.top_bar_menu))
          }
          DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            destinations.forEach { screen ->
              DropdownMenuItem(
                  text = { Text(stringResource(screen.labelRes)) },
                  onClick = {
                    menuExpanded = false
                    onDestinationClick(screen)
                  },
                  modifier = Modifier.testTag(C.Tag.topBarMenuItem(screen.route)),
              )
            }
          }
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
