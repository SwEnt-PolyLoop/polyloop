// Made with Claude.

package com.swent.polyloop.model.handover

import com.swent.polyloop.model.rental.RentalStatus
import java.time.Instant

/** When a condition photo is taken: at pickup or at return. */
enum class HandoverPhase {
  PICKUP,
  RETURN;

  companion object {
    /**
     * The phase photos are taken in for a rental in [status], or null if none can be taken: the
     * pickup while it is accepted, the return while the item is out, late or not.
     */
    fun fromStatus(status: RentalStatus): HandoverPhase? =
        when (status) {
          RentalStatus.ACCEPTED -> PICKUP
          RentalStatus.ACTIVE,
          RentalStatus.OVERDUE -> RETURN
          else -> null
        }
  }
}

/**
 * A condition photo, stored at `rentals/{rentalId}/photos/{id}`; [url] is its download URL, or the
 * phone's copy while it waits in the upload queue. [sync] tells where it stands.
 */
data class HandoverPhoto(
    val id: String,
    val phase: HandoverPhase,
    val takenAt: Instant,
    val url: String,
    val sync: PhotoSync = PhotoSync.SENT,
) {
  companion object {
    /** Each person takes 3 to 20 photos per phase. */
    const val MIN_PHOTOS = 3
    const val MAX_PHOTOS = 20
  }
}

/** Where a condition photo stands: on the server, waiting in the upload queue, or refused. */
enum class PhotoSync {
  SENT,
  WAITING,
  REFUSED,
}
