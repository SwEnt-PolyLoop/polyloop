// Made with Claude.

package com.swent.polyloop.model.handover

import kotlinx.coroutines.flow.Flow

/** The condition photos of rentals, taken with the in-app camera. */
interface HandoverRepository {

  /**
   * The photos the signed-in user took of the rental [rentalId] in [phase], oldest first. Emits
   * again when one is added. The flow fails with an [IllegalStateException] if nobody is signed in,
   * or with the cause if they cannot be loaded.
   */
  fun observeMyPhotos(rentalId: String, phase: HandoverPhase): Flow<List<HandoverPhoto>>

  /**
   * Uploads the photo file at [photoUri] as the signed-in user's photo of [rentalId] in [phase].
   * Fails with an [IllegalArgumentException] if [rentalId] is invalid, an [IllegalStateException]
   * if nobody is signed in, or the cause if it cannot be saved (e.g. offline, or the rental is not
   * Accepted).
   */
  suspend fun addPhoto(rentalId: String, phase: HandoverPhase, photoUri: String): Result<Unit>
}
