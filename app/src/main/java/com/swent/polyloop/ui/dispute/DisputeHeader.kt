// Made with Claude.

package com.swent.polyloop.ui.dispute

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.swent.polyloop.R
import com.swent.polyloop.model.dispute.DisputeStage
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.theme.LeafGreen
import com.swent.polyloop.ui.theme.MintGreen

/**
 * The top of the Dispute screen: the title with a "Settled" pill once both parties agreed, the
 * user's role on the right, the item and the deposit, then the stepper showing the current stage.
 */
@Composable
internal fun DisputeHeader(uiState: DisputeUiState) {
  Column(
      verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.dispute_header_spacing))
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
          text = stringResource(R.string.dispute_title),
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.Bold,
      )
      if (uiState.isSettled) {
        Pill(
            text = stringResource(R.string.dispute_settled),
            background = MintGreen,
            content = LeafGreen,
            modifier =
                Modifier.padding(start = dimensionResource(R.dimen.dispute_pill_gap))
                    .testTag(C.Tag.dispute_settled_pill),
        )
      }
      Box(modifier = Modifier.weight(1f))
      Pill(
          text =
              stringResource(
                  if (uiState.isLender) R.string.dispute_role_lender
                  else R.string.dispute_role_borrower
              ),
          background = MaterialTheme.colorScheme.primaryContainer,
          content = MaterialTheme.colorScheme.primary,
          modifier = Modifier.testTag(C.Tag.dispute_role_pill),
      )
    }
    Text(
        text = stringResource(R.string.dispute_item_deposit, uiState.itemTitle, uiState.deposit),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag(C.Tag.dispute_item_deposit),
    )
    DisputeStepper(
        stage = uiState.stage,
        modifier = Modifier.padding(top = dimensionResource(R.dimen.dispute_header_spacing)),
    )
  }
}

/**
 * The three stages side by side, each under a bar: the current one has a bar in the primary colour
 * and its name in bold, and is marked as selected for accessibility services and tests.
 */
@Composable
private fun DisputeStepper(stage: DisputeStage, modifier: Modifier = Modifier) {
  Row(
      modifier = modifier.fillMaxWidth(),
      horizontalArrangement =
          Arrangement.spacedBy(dimensionResource(R.dimen.dispute_header_spacing)),
  ) {
    DisputeStage.entries.forEach { step ->
      val current = step == stage
      Column(
          modifier =
              Modifier.weight(1f)
                  .semantics { selected = current }
                  .testTag(C.Tag.dispute_step_ + step.name),
          verticalArrangement =
              Arrangement.spacedBy(dimensionResource(R.dimen.dispute_step_spacing)),
      ) {
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .height(dimensionResource(R.dimen.dispute_step_bar_height))
                    .background(
                        if (current) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(dimensionResource(R.dimen.dispute_step_bar_corner)),
                    )
        )
        Text(
            text = stringResource(step.label),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
            color =
                if (current) MaterialTheme.colorScheme.onBackground
                else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

/** A rounded label. */
@Composable
private fun Pill(
    text: String,
    background: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
  Text(
      text = text,
      style = MaterialTheme.typography.labelLarge,
      color = content,
      modifier =
          modifier
              .background(background, RoundedCornerShape(50))
              .padding(
                  horizontal = dimensionResource(R.dimen.dispute_pill_padding_horizontal),
                  vertical = dimensionResource(R.dimen.dispute_pill_padding_vertical),
              ),
  )
}

/** The name of a stage, in the stepper. */
private val DisputeStage.label: Int
  get() =
      when (this) {
        DisputeStage.SETTLE -> R.string.dispute_step_settle
        DisputeStage.LLM_PROPOSAL -> R.string.dispute_step_llm
        DisputeStage.COURT -> R.string.dispute_step_court
      }
