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
    // The secret code of every rental and phase: shown as it is, scanned against its fingerprint.
    var code: String? = "secret-code",
    // When set, getCodeToShow and getCodeFingerprint fail with this error.
    var codeFailure: Throwable? = null,
    // When set, confirmScan fails with this error once the code is right.
    var scanFailure: Throwable? = null,
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
    // As the real repository: the id is the lowest free slot of the user, and nothing is added
    // when all are used. A photo with another id, such as a random one, uses no slot.
    val slot =
        lowestFreeSlot(
            uid,
            phase,
            stored.value
                .filter { it.rentalId == rentalId && it.takenBy == uid && it.photo.phase == phase }
                .map { it.photo.id },
        )
            ?: return Result.failure(
                IllegalStateException(
                    "All ${HandoverPhoto.MAX_PHOTOS} ${phase.name} photos are already taken"
                )
            )
    stored.update { all ->
      all +
          StoredPhoto(
              rentalId,
              uid,
              HandoverPhoto(photoSlotId(uid, phase, slot), phase, now, photoUri),
          )
    }
    return Result.success(Unit)
  }

  // The codes sent to confirmScan, in order, including the wrong ones.
  val scannedCodes = mutableListOf<String>()

  override suspend fun getCodeToShow(rentalId: String, phase: HandoverPhase): Result<String?> =
      readCode {
        code
      }

  override suspend fun getCodeFingerprint(
      rentalId: String,
      phase: HandoverPhase,
  ): Result<String?> = readCode { code?.let(HandoverCode::fingerprint) }

  override suspend fun confirmScan(
      rentalId: String,
      phase: HandoverPhase,
      code: String,
  ): Result<Unit> {
    scannedCodes += code
    gate?.await()
    return when {
      currentUid == null -> Result.failure(IllegalStateException("Nobody is signed in"))
      code != this.code -> Result.failure(WrongCodeException())
      else -> scanFailure?.let { Result.failure(it) } ?: Result.success(Unit)
    }
  }

  private fun readCode(read: () -> String?): Result<String?> {
    codeFailure?.let {
      return Result.failure(it)
    }
    if (currentUid == null) return Result.failure(IllegalStateException("Nobody is signed in"))
    return Result.success(read())
  }
}
