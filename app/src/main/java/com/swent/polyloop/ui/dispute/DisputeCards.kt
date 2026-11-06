// Made with Claude.

package com.swent.polyloop.ui.dispute

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.swent.polyloop.R
import com.swent.polyloop.resources.C

/** A white card with a bold [title] and its [content] under it. */
@Composable
private fun DisputeCard(
    title: String,
    modifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {},
) {
  OutlinedCard(modifier = modifier.fillMaxWidth()) {
    Column(
        modifier = Modifier.padding(dimensionResource(R.dimen.dispute_card_padding)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.dispute_card_spacing)),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = titleModifier.weight(1f),
        )
        trailing()
      }
      content()
    }
  }
}

/** The evidence card; it stays empty until the pickup and return photos exist (Handover). */
@Composable
internal fun EvidenceCard() {
  DisputeCard(
      title = stringResource(R.string.dispute_evidence),
      modifier = Modifier.testTag(C.Tag.dispute_evidence_card),
      trailing = {
        Text(
            text = stringResource(R.string.dispute_evidence_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      },
  )
}

/** [text] in the error colour. */
@Composable
internal fun ErrorText(text: String, modifier: Modifier = Modifier) {
  Text(
      text = text,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.error,
      modifier = modifier,
  )
}

/** What the LLM proposal and court stages show until they are built. */
@Composable
internal fun ComingSoonCard() {
  DisputeCard(
      title = stringResource(R.string.dispute_coming_soon),
      modifier = Modifier.testTag(C.Tag.dispute_coming_soon),
  )
}
