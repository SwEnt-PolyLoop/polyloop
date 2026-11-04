// Made with Claude.

package com.swent.polyloop.model.handover

import com.swent.polyloop.model.rental.RentalStatus
import java.time.Instant

/** When a condition photo is taken: at pickup or at return. */
enum class HandoverPhase {
  PICKUP,
  RETURN;

  companion object {
    /** The phase photos are taken in for a rental in [status], or null if none can be taken. */
    fun fromStatus(status: RentalStatus): HandoverPhase? =
        if (status == RentalStatus.ACCEPTED) PICKUP else null
  }
}

/** A condition photo, stored at `rentals/{rentalId}/photos/{id}`; [url] is its download URL. */
data class HandoverPhoto(
    val id: String,
    val phase: HandoverPhase,
    val takenAt: Instant,
    val url: String,
) {
  companion object {
    /** Each person takes 3 to 20 photos per phase. */
    const val MIN_PHOTOS = 3
    const val MAX_PHOTOS = 20
  }
}
