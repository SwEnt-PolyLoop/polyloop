// Made with Claude.

package com.swent.polyloop.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.swent.polyloop.model.rental.RentalStatus

/**
 * Background and text colours of a rental status badge: waiting, in progress, a problem, or
 * finished. Shared by the My rentals badges and the My listings pills.
 */
@Composable
fun RentalStatus.badgeColors(): Pair<Color, Color> {
  val colors = MaterialTheme.colorScheme
  return when (this) {
    RentalStatus.REQUESTED -> colors.secondaryContainer to colors.onSecondaryContainer
    RentalStatus.ACCEPTED,
    RentalStatus.ACTIVE -> colors.primaryContainer to colors.onPrimaryContainer
    RentalStatus.OVERDUE,
    RentalStatus.LOST,
    RentalStatus.DISPUTED,
    RentalStatus.FROZEN -> colors.errorContainer to colors.onErrorContainer
    RentalStatus.RETURNED,
    RentalStatus.CLOSED,
    RentalStatus.DECLINED,
    RentalStatus.CANCELLED -> colors.surfaceVariant to colors.onSurfaceVariant
  }
}
