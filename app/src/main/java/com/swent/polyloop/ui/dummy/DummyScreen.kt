// Made with Claude.

package com.swent.polyloop.ui.dummy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.swent.polyloop.resources.C

/**
 * Stand-in for every screen that is not built yet.
 *
 * @param text shown in the middle of the screen; the NavHost passes the route so navigation is
 *   visible.
 */
@Composable
fun DummyScreen(
    text: String,
    modifier: Modifier = Modifier,
) {
  Column(
      modifier = modifier.fillMaxSize().testTag(C.Tag.dummy_screen),
      verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(text, modifier = Modifier.testTag(C.Tag.dummy_screen_text))
  }
}
