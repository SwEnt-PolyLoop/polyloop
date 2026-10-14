// Made with Claude.

package com.swent.polyloop.model.rental

import com.google.firebase.Firebase
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.firestore.confirmWrite
import com.swent.polyloop.model.firestore.isValidDocumentId
import com.swent.polyloop.model.firestore.snapshotsAsFlow
import com.swent.polyloop.model.firestore.withFirebaseErrors
import com.swent.polyloop.model.listing.Listing
import java.time.LocalDate
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await

/** [RentalRepository] backed by Firestore, one document per rental at `rentals/{id}`. */
class RentalRepositoryFirestore(private val db: FirebaseFirestore = Firebase.firestore) :
    RentalRepository {

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
      when {
        !confirmWrite { document.set(fields) } ->
            Result.failure(TimeoutException("Rental request not confirmed in time"))
        // Read back to get createdAt as the server set it.
        else ->
            readRental(document)?.let { Result.success(it) }
                ?: Result.failure(IllegalStateException("Rental ${document.id} cannot be read"))
      }
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
}
