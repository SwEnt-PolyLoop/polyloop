// Made with Claude.

package com.swent.polyloop.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.swent.polyloop.R

/**
 * A person's rating on a card: a star and "4.6 · 5 reviews", or "No reviews yet" when [reviewCount]
 * is 0. [modifier] goes on the text, so that a test tag finds the node holding it.
 */
@Composable
fun RatingLine(averageRating: Double, reviewCount: Int, modifier: Modifier = Modifier) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    if (reviewCount > 0) {
      Icon(
          Icons.Default.Star,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.tertiary,
          modifier = Modifier.size(14.dp),
      )
      Spacer(Modifier.width(4.dp))
    }
    Text(
        text =
            if (reviewCount > 0)
                pluralStringResource(
                    R.plurals.profile_rating,
                    reviewCount,
                    averageRating,
                    reviewCount,
                )
            else stringResource(R.string.profile_no_reviews),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
  }
}
