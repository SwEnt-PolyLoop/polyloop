// Made with Claude.

package com.swent.polyloop.model.rental

import com.google.firebase.Firebase
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.swent.polyloop.model.firestore.FUNCTIONS_REGION
import com.swent.polyloop.model.firestore.callCloudFunction
import com.swent.polyloop.model.firestore.isValidDocumentId
import com.swent.polyloop.model.firestore.snapshotsAsFlow
import com.swent.polyloop.model.firestore.withFirebaseErrors
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

/**
 * [RentalRepository] backed by Firestore, one document per rental at `rentals/{id}`. Creating a
 * rental, the deposit negotiation and reading the server's time go through the Cloud Functions in
 * [FUNCTIONS_REGION], the only writers of rentals.
 *
 * @param db the Firestore database.
 * @param functions gives the Cloud Functions client. A provider, not an instance, so that Firebase
 *   is only touched when a Function is called.
 */
class RentalRepositoryFirestore(
    private val db: FirebaseFirestore = Firebase.firestore,
    private val functions: () -> FirebaseFunctions = {
      FirebaseFunctions.getInstance(FUNCTIONS_REGION)
    },
) : RentalRepository {

  private val rentals: CollectionReference
    get() = db.collection(RentalFields.COLLECTION)

  override suspend fun requestRental(
      listingId: String,
      startDate: LocalDate,
      endDate: LocalDate,
  ): Result<String> {
    if (endDate.isBefore(startDate)) {
      return Result.failure(
          IllegalArgumentException("endDate $endDate is before startDate $startDate")
      )
    }
    val rentalId = UUID.randomUUID().toString()
    val result =
        callFunction(
            "requestRental",
            mapOf(
                "rentalId" to rentalId,
                "listingId" to listingId,
                "startDate" to startDate.toString(),
                "endDate" to endDate.toString(),
            ),
        )
    return result.fold(
        onSuccess = { Result.success(rentalId) },
        onFailure = { Result.failure(insufficientFunds(it) ?: it) },
    )
  }

  override fun observeRentalsAsBorrower(uid: String): Flow<List<Rental>> =
      observeWhere(RentalFields.BORROWER_ID, uid)

  override fun observeRentalsAsLender(uid: String): Flow<List<Rental>> =
      observeWhere(RentalFields.OWNER_ID, uid)

  override suspend fun getRental(id: String): Result<Rental?> {
    if (!isValidDocumentId(id)) return Result.success(null)
    return withFirebaseErrors { Result.success(readRental(rentals.document(id))) }
  }

  override fun observeRental(id: String): Flow<Rental?> =
      if (isValidDocumentId(id)) {
        rentals.document(id).snapshotsAsFlow(::toRental).distinctUntilChanged()
      } else {
        flowOf(null)
      }

  override suspend fun makeOffer(
      rentalId: String,
      deposit: Int,
      pickupAddress: String,
      returnAddress: String,
  ): Result<Unit> =
      callFunction(
          "makeOffer",
          mapOf(
              "rentalId" to rentalId,
              "deposit" to deposit,
              "pickupAddress" to pickupAddress,
              "returnAddress" to returnAddress,
          ),
      )

  override suspend fun acceptOffer(rentalId: String): Result<Unit> =
      callFunction("respondToOffer", mapOf("rentalId" to rentalId, "action" to "ACCEPT"))

  override suspend fun declineOffer(rentalId: String): Result<Unit> =
      callFunction("respondToOffer", mapOf("rentalId" to rentalId, "action" to "DECLINE"))

  override suspend fun counterOffer(rentalId: String, deposit: Int): Result<Unit> =
      callFunction(
          "respondToOffer",
          mapOf("rentalId" to rentalId, "action" to "COUNTER", "deposit" to deposit),
      )

  override suspend fun declineRequest(rentalId: String): Result<Unit> =
      callFunction("declineRequest", mapOf("rentalId" to rentalId))

  override suspend fun serverNow(): Result<Instant> = withFirebaseErrors {
    val data = functions().getHttpsCallable(SERVER_TODAY).call().await().data
    // Callable results come back as JSON numbers, decoded as Integer, Long or Double.
    val now = (data as? Map<*, *>)?.get("now") as? Number
    if (now != null) {
      Result.success(Instant.ofEpochMilli(now.toLong()))
    } else {
      Result.failure(IllegalStateException("$SERVER_TODAY returned no time"))
    }
  }

  /** Calls the Cloud Function [name] with [data]; a refusal or a lost connection is a failure. */
  private suspend fun callFunction(name: String, data: Map<String, Any>): Result<Unit> =
      callCloudFunction(functions(), name, data)

  /**
   * The rentals whose [field] is [uid], newest first. Sorted here rather than by the query, so no
   * composite index is needed (see "Firestore schema" in docs/ARCHITECTURE.md).
   */
  private fun observeWhere(field: String, uid: String): Flow<List<Rental>> =
      rentals
          .whereEqualTo(field, uid)
          .snapshotsAsFlow { snapshot ->
            snapshot.documents.mapNotNull(::toRental).sortedByDescending { it.createdAt }
          }
          .distinctUntilChanged()

  private suspend fun readRental(document: DocumentReference): Rental? =
      toRental(document.get().await())

  /** Null if the rental does not exist or is malformed. */
  private fun toRental(snapshot: DocumentSnapshot): Rental? =
      snapshot.getData(ServerTimestampBehavior.ESTIMATE)?.let {
        rentalFromDocument(snapshot.id, it)
      }

  /** [InsufficientFundsException] if [error] is the refusal for a balance too low, else null. */
  private fun insufficientFunds(error: Throwable): InsufficientFundsException? {
    val refusal = error as? FirebaseFunctionsException ?: return null
    if (refusal.code != FirebaseFunctionsException.Code.FAILED_PRECONDITION) return null
    val reason = (refusal.details as? Map<*, *>)?.get(REASON)
    return if (reason == INSUFFICIENT_FUNDS) InsufficientFundsException() else null
  }

  companion object {
    private const val REASON = "reason"
    private const val INSUFFICIENT_FUNDS = "INSUFFICIENT_FUNDS"
    private const val SERVER_TODAY = "serverToday"
  }
}
