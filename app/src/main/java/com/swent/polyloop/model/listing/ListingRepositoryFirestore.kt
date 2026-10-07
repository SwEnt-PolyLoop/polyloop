// Made with Claude.

package com.swent.polyloop.model.listing

import com.google.firebase.Firebase
import com.google.firebase.FirebaseException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await

/** [ListingRepository] backed by the Firestore [ListingFields.COLLECTION] collection. */
class ListingRepositoryFirestore(private val db: FirebaseFirestore = Firebase.firestore) :
    ListingRepository {

  override suspend fun getAllListings(): Result<List<Listing>> =
      try {
        val snapshot =
            db.collection(ListingFields.COLLECTION)
                .whereEqualTo(ListingFields.STATUS, ListingStatus.PUBLISHED.name)
                .get()
                .await()
        Result.success(
            snapshot.documents.mapNotNull { doc ->
              doc.data?.let { listingFromDocument(doc.id, it) }
            }
        )
      } catch (e: FirebaseException) {
        Result.failure(e)
      }

  override suspend fun getListing(id: String): Result<Listing?> {
    // Firestore throws on these ids instead of failing the task, and no listing can have them.
    if (id.isBlank() || id.contains('/')) return Result.success(null)
    return try {
      val doc = db.collection(ListingFields.COLLECTION).document(id).get().await()
      Result.success(doc.data?.let { listingFromDocument(id, it) })
    } catch (e: FirebaseException) {
      Result.failure(e)
    }
  }
}
