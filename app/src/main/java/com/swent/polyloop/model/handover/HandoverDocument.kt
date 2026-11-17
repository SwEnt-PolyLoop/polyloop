// Made with Claude.

package com.swent.polyloop.model.handover

import com.swent.polyloop.model.firestore.enumOrNull
import com.swent.polyloop.model.firestore.instantOrNull

/** Firestore and Storage names for condition photos: `rentals/{rentalId}/photos/{photoId}`. */
internal object HandoverFields {
  const val RENTALS = "rentals"
  const val PHOTOS = "photos"
  const val TAKEN_BY = "takenBy"
  const val PHASE = "phase"
  const val TAKEN_AT = "takenAt"
  const val URL = "url"

  /** The secret codes: `rentals/{rentalId}/handoverCodes/{PICKUP or RETURN}`, field [CODE]. */
  const val CODES = "handoverCodes"
  const val CODE = "code"

  /** The field of the rental holding the fingerprint of the code scanned in [phase]. */
  fun fingerprintField(phase: HandoverPhase): String =
      when (phase) {
        HandoverPhase.PICKUP -> "pickupCodeHash"
        HandoverPhase.RETURN -> "returnCodeHash"
      }
}

/** Builds a [HandoverPhoto] from a Firestore document, or returns null if a field is wrong. */
internal fun photoFromDocument(id: String, data: Map<String, Any?>): HandoverPhoto? =
    HandoverPhoto(
        id = id,
        phase = enumOrNull<HandoverPhase>(data[HandoverFields.PHASE]) ?: return null,
        takenAt = instantOrNull(data[HandoverFields.TAKEN_AT]) ?: return null,
        url = (data[HandoverFields.URL] as? String)?.takeIf { it.isNotBlank() } ?: return null,
    )
