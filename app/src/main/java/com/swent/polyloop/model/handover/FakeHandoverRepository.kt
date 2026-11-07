// Made with Claude.

package com.swent.polyloop.model.handover

import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [HandoverRepository] for tests and previews; the app itself never uses it. Tests set
 * [photos] to make [observeMyPhotos] emit, like Firestore would.
 */
class FakeHandoverRepository(
    // The signed-in user; null when nobody is signed in.
    var currentUid: String? = "u1",
    // When set, the flow and addPhoto fail with this error.
    var failure: Throwable? = null,
    // When set, addPhoto waits until it is completed, so tests can see the "uploading" state.
    var gate: CompletableDeferred<Unit>? = null,
    // The time given to the next photo added.
    var now: Instant = Instant.parse("2026-10-13T15:00:00Z"),
) : HandoverRepository {

  /** A photo of the rental [rentalId], taken by [takenBy]. */
  data class StoredPhoto(val rentalId: String, val takenBy: String, val photo: HandoverPhoto)

  private val stored = MutableStateFlow<List<StoredPhoto>>(emptyList())

  // Every photo of every rental. Setting it makes observeMyPhotos emit.
  var photos: List<StoredPhoto>
    get() = stored.value
    set(value) {
      stored.value = value
    }

  // How many times addPhoto was called, including the calls that failed.
  var addPhotoCalls = 0
    private set

  override fun observeMyPhotos(rentalId: String, phase: HandoverPhase): Flow<List<HandoverPhoto>> {
    val error =
        failure ?: IllegalStateException("Nobody is signed in").takeIf { currentUid == null }
    if (error != null) return flow { throw error }
    return stored.map { all ->
      all.filter { it.rentalId == rentalId && it.takenBy == currentUid && it.photo.phase == phase }
          .map { it.photo }
          .sortedBy { it.takenAt }
    }
  }

  override suspend fun addPhoto(
      rentalId: String,
      phase: HandoverPhase,
      photoUri: String,
  ): Result<Unit> {
    addPhotoCalls++
    gate?.await()
    failure?.let {
      return Result.failure(it)
    }
    val uid = currentUid ?: return Result.failure(IllegalStateException("Nobody is signed in"))
    stored.update { all ->
      all + StoredPhoto(rentalId, uid, HandoverPhoto("p${all.size + 1}", phase, now, photoUri))
    }
    return Result.success(Unit)
  }
}
