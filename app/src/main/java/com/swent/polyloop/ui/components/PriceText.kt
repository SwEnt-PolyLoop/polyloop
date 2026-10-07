// Made with Claude.

package com.swent.polyloop.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.swent.polyloop.R

/** "15 PP" in bold followed by "/ day" in a lighter style. */
@Composable
fun PriceText(pricePerDay: Int, style: TextStyle, modifier: Modifier = Modifier) {
  val price = stringResource(R.string.listing_card_price, pricePerDay)
  val perDay = stringResource(R.string.listing_card_per_day)
  val priceColor = MaterialTheme.colorScheme.primary
  val perDayColor = MaterialTheme.colorScheme.onSurfaceVariant
  Text(
      text =
          buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = priceColor)) { append(price) }
            append(" ")
            withStyle(SpanStyle(color = perDayColor)) { append(perDay) }
          },
      style = style,
      modifier = modifier,
  )
}
