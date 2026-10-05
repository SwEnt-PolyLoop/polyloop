// Made with Claude.

package com.swent.polyloop.ui.dummy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.swent.polyloop.R
import com.swent.polyloop.resources.C

/**
 * Stand-in for every screen that is not built yet.
 *
 * @param text shown in the middle of the screen; the NavHost passes the route so navigation is
 *   visible.
 * @param onContinue when set, shows a "Continue" button; sign-in uses it to enter the app until
 *   real authentication is wired in.
 */
@Composable
fun DummyScreen(
    text: String,
    modifier: Modifier = Modifier,
    onContinue: (() -> Unit)? = null,
) {
  Column(
      modifier = modifier.fillMaxSize().testTag(C.Tag.dummy_screen),
      verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(text, modifier = Modifier.testTag(C.Tag.dummy_screen_text))
    if (onContinue != null) {
      Button(
          onClick = onContinue,
          modifier = Modifier.testTag(C.Tag.dummy_screen_continue_button),
      ) {
        Text(stringResource(R.string.dummy_screen_continue))
      }
    }
  }
}
