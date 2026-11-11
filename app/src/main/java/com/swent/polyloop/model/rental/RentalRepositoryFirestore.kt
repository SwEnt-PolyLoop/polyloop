// Made with Claude.

package com.swent.polyloop.model.rental

import com.google.firebase.Firebase
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.FirebaseFunctions
import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.firestore.confirmWrite
import com.swent.polyloop.model.firestore.isValidDocumentId
import com.swent.polyloop.model.firestore.snapshotsAsFlow
import com.swent.polyloop.model.firestore.withFirebaseErrors
import com.swent.polyloop.model.listing.Listing
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

/**
 * [RentalRepository] backed by Firestore, one document per rental at `rentals/{id}`. The deposit
 * negotiation goes through the Cloud Functions in [REGION], the only writers of those fields, and
 * so does reading the server's time.
 *
 * @param db the Firestore database.
 * @param functions gives the Cloud Functions client. A provider, not an instance, so that Firebase
 *   is only touched when a Function is called.
 */
class RentalRepositoryFirestore(
    private val db: FirebaseFirestore = Firebase.firestore,
    private val functions: () -> FirebaseFunctions = { FirebaseFunctions.getInstance(REGION) },
) : RentalRepository {

  private val rentals: CollectionReference
    get() = db.collection(RentalFields.COLLECTION)

  override suspend fun requestRental(
      listing: Listing,
      ownerName: String,
      borrower: AuthUser,
      startDate: LocalDate,
      endDate: LocalDate,
  ): Result<Rental> {
    if (endDate.isBefore(startDate)) {
      return Result.failure(
          IllegalArgumentException("endDate $endDate is before startDate $startDate")
      )
    }
    return withFirebaseErrors {
      val document = rentals.document()
      val fields = rentalRequestDocument(listing, ownerName, borrower, startDate, endDate)
      val confirmed = confirmWrite { document.set(fields) }
      // Confirmed: read back to get createdAt as the server set it. Not confirmed in time: the
      // write is still queued and Firestore sends it later, so it is not a failure; reporting one
      // would make the user request again and write a second rental. Read it from the local cache,
      // where it already is (with an estimated createdAt).
      val snapshot = (if (confirmed) document.get() else document.get(Source.CACHE)).await()
      toRental(snapshot)?.let { Result.success(it) }
          ?: Result.failure(IllegalStateException("Rental ${document.id} cannot be read"))
    }
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
      withFirebaseErrors {
        functions().getHttpsCallable(name).call(data).await()
        Result.success(Unit)
      }

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

  companion object {
    /** Region of the Cloud Functions, next to the Firestore database (see docs/ARCHITECTURE.md). */
    const val REGION = "europe-west1"

    private const val SERVER_TODAY = "serverToday"
  }
}
