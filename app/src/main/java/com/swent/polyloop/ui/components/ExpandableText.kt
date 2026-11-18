// Made with Claude.

package com.swent.polyloop.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.swent.polyloop.R

/** How many lines a long text shows before it is cut behind "See more". */
const val COLLAPSED_TEXT_LINES = 4

/**
 * A text shown on at most [collapsedLines] lines. When it does not fit, it ends with an ellipsis
 * and a "See more" button under it shows it whole; the button then reads "See less" and collapses
 * it again. A text that fits shows no button. Whether it is expanded survives rotation, and starts
 * collapsed again when [text] changes.
 *
 * @param text the text to show.
 * @param modifier applied to the text itself, e.g. its test tag.
 * @param style the text's style.
 * @param collapsedLines the most lines shown while collapsed.
 * @param toggleTag the test tag of the "See more" / "See less" button.
 */
@Composable
fun ExpandableText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    collapsedLines: Int = COLLAPSED_TEXT_LINES,
    toggleTag: String = "",
) {
  // null while the text fits (no button), false while it is cut, true once expanded. Measured
  // again on each collapsed layout, so a text that fits after a rotation loses its button.
  var isExpanded by rememberSaveable(text) { mutableStateOf<Boolean?>(null) }
  Column {
    Text(
        text = text,
        style = style,
        maxLines = if (isExpanded == true) Int.MAX_VALUE else collapsedLines,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout ->
          if (isExpanded != true) isExpanded = if (layout.hasVisualOverflow) false else null
        },
        modifier = modifier,
    )
    isExpanded?.let { expanded ->
      TextButton(
          onClick = { isExpanded = !expanded },
          contentPadding = PaddingValues(),
          modifier = Modifier.testTag(toggleTag),
      ) {
        Text(
            stringResource(
                if (expanded) R.string.expandable_text_see_less
                else R.string.expandable_text_see_more
            )
        )
      }
    }
  }
}
