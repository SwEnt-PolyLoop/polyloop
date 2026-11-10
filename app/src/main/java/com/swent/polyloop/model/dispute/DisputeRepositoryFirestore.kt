// Made with Claude.

package com.swent.polyloop.model.dispute

import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.swent.polyloop.model.firestore.isValidDocumentId
import com.swent.polyloop.model.firestore.snapshotsAsFlow
import com.swent.polyloop.model.firestore.withFirebaseErrors
import com.swent.polyloop.model.rental.RentalRepositoryFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * [DisputeRepository] backed by Firestore: one document per dispute at `disputes/{rentalId}` (see
 * "Firestore schema" in docs/ARCHITECTURE.md). The app only reads it; proposals, agreements and
 * escalations go through the Cloud Functions `proposeSplit`, `acceptSplit` and `escalateDispute`,
 * which act as the signed-in user and are the only writers of disputes.
 *
 * @param db the Firestore database.
 * @param functions gives the Cloud Functions client. A provider, not an instance, so that Firebase
 *   is only touched when a Function is called.
 */
class DisputeRepositoryFirestore(
    private val db: FirebaseFirestore = Firebase.firestore,
    private val functions: () -> FirebaseFunctions = {
      FirebaseFunctions.getInstance(RentalRepositoryFirestore.REGION)
    },
) : DisputeRepository {

  /**
   * Follows `disputes/{rentalId}` with a plain snapshot listener, so the dispute stays readable
   * offline from the cache. An invalid [rentalId] (blank, or containing `/`) has no dispute, so it
   * emits null without touching Firestore. A document that breaks the schema closes the flow with
   * an [IllegalStateException] (see [disputeFromDocument]), like a read error. It is converted in
   * the flow, not in the snapshot listener, where a throw would not reach the collector.
   */
  override fun observeDispute(rentalId: String): Flow<Dispute?> {
    if (!isValidDocumentId(rentalId)) return flowOf(null)
    return db.collection(DisputeFields.COLLECTION)
        .document(rentalId)
        .snapshotsAsFlow { snapshot -> snapshot.getData(ServerTimestampBehavior.ESTIMATE) }
        .map { data -> data?.let { disputeFromDocument(rentalId, it) } }
        .distinctUntilChanged()
  }

  override suspend fun propose(rentalId: String, lenderPercent: Int): Result<Unit> =
      callFunction("proposeSplit", mapOf("rentalId" to rentalId, "lenderPercent" to lenderPercent))

  override suspend fun accept(rentalId: String, lenderPercent: Int): Result<Unit> =
      callFunction("acceptSplit", mapOf("rentalId" to rentalId, "lenderPercent" to lenderPercent))

  override suspend fun escalate(rentalId: String): Result<Unit> =
      callFunction("escalateDispute", mapOf("rentalId" to rentalId))

  /**
   * Calls the Cloud Function [name] with [data]. A refusal or a lost connection is a failure; a
   * request that lost a race fails with [ProposalChangedException] or [StageChangedException], read
   * from the refusal's `details.reason`, never from its message.
   */
  private suspend fun callFunction(name: String, data: Map<String, Any>): Result<Unit> {
    val result = withFirebaseErrors {
      functions().getHttpsCallable(name).call(data).await()
      Result.success(Unit)
    }
    return result.exceptionOrNull()?.let(::lostRace)?.let { Result.failure(it) } ?: result
  }

  /** The exception the app expects when [error] says the request lost a race, or null. */
  private fun lostRace(error: Throwable): Exception? {
    val refusal = error as? FirebaseFunctionsException ?: return null
    if (refusal.code != FirebaseFunctionsException.Code.FAILED_PRECONDITION) return null
    return when ((refusal.details as? Map<*, *>)?.get(REASON)) {
      PROPOSAL_CHANGED -> ProposalChangedException()
      STAGE_CHANGED -> StageChangedException()
      else -> null
    }
  }

  private companion object {
    /** The key and values of a refusal's `details` (see `disputeRules.ts` in functions/src). */
    const val REASON = "reason"
    const val PROPOSAL_CHANGED = "PROPOSAL_CHANGED"
    const val STAGE_CHANGED = "STAGE_CHANGED"
  }
}
