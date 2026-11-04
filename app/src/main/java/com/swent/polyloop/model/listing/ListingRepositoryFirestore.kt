// Made with Claude.

package com.swent.polyloop.model.listing

import android.net.Uri
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storageMetadata
import com.swent.polyloop.model.RepositoryProvider
import com.swent.polyloop.model.firestore.isValidDocumentId
import com.swent.polyloop.model.firestore.withFirebaseErrors
import kotlinx.coroutines.tasks.await

/**
 * [ListingRepository] backed by the Firestore [ListingFields.COLLECTION] collection, with the
 * photos in Cloud Storage under listings/{listingId}/.
 */
class ListingRepositoryFirestore(
    private val db: FirebaseFirestore = Firebase.firestore,
    storageProvider: () -> FirebaseStorage = { RepositoryProvider.storage },
) : ListingRepository {

  // Lazy, so creating the repository does not touch Storage; only createListing's uploads do.
  private val storage by lazy(storageProvider)

  override suspend fun getAllListings(): Result<List<Listing>> = withFirebaseErrors {
    val snapshot =
        db.collection(ListingFields.COLLECTION)
            .whereEqualTo(ListingFields.STATUS, ListingStatus.PUBLISHED.name)
            .get()
            .await()
    Result.success(
        snapshot.documents.mapNotNull { doc -> doc.data?.let { listingFromDocument(doc.id, it) } }
    )
  }

  override suspend fun getListing(id: String): Result<Listing?> {
    if (!isValidDocumentId(id)) return Result.success(null)
    return withFirebaseErrors {
      val doc = db.collection(ListingFields.COLLECTION).document(id).get().await()
      Result.success(doc.data?.let { listingFromDocument(id, it) })
    }
  }

  override suspend fun getBookedRanges(id: String): Result<List<BookedRange>?> {
    if (!isValidDocumentId(id)) return Result.success(null)
    return withFirebaseErrors {
      val doc = db.collection(ListingFields.COLLECTION).document(id).get(Source.SERVER).await()
      Result.success(doc.data?.let(::publishedBookedRangesOrNull))
    }
  }

  override suspend fun createListing(
      listing: Listing,
      addresses: ListingAddresses,
      photos: List<Uri>,
  ): Result<String> {
    // canPublish only counts the photos, so the local URIs stand in for the URLs not uploaded yet.
    if (!listing.copy(photoUrls = photos.map(Uri::toString)).canPublish(addresses)) {
      return Result.failure(IllegalArgumentException("This listing cannot be published"))
    }
    return withFirebaseErrors {
      val listingRef = db.collection(ListingFields.COLLECTION).document()
      val photoUrls = photos.mapIndexed { index, uri ->
        uploadPhoto(listingRef.id, index, uri, listing.ownerId)
      }
      val saved = listing.copy(id = listingRef.id, photoUrls = photoUrls)
      db.batch()
          .set(listingRef, listingToDocument(saved))
          .set(
              listingRef
                  .collection(ListingAddressFields.COLLECTION)
                  .document(ListingAddressFields.DOCUMENT),
              addressesToDocument(addresses),
          )
          .commit()
          .await()
      Result.success(listingRef.id)
    }
  }

  override suspend fun getListingsByOwner(ownerId: String): Result<List<Listing>> {
    if (ownerId.isBlank()) return Result.success(emptyList())
    return withFirebaseErrors {
      val snapshot =
          db.collection(ListingFields.COLLECTION)
              .whereEqualTo(ListingFields.OWNER_ID, ownerId)
              .get()
              .await()
      Result.success(
          snapshot.documents.mapNotNull { doc -> doc.data?.let { listingFromDocument(doc.id, it) } }
      )
    }
  }

  /**
   * Uploads the photo at [uri] as listings/{listingId}/{index}.jpg and returns its URL. The photo
   * is tagged with the uid of its [uploader], the listing's owner (the signed-in user): the Storage
   * rules refuse it otherwise, and let only that user change it.
   */
  private suspend fun uploadPhoto(
      listingId: String,
      index: Int,
      uri: Uri,
      uploader: String,
  ): String {
    val ref = storage.reference.child("$PHOTOS_FOLDER/$listingId/$index.jpg")
    val metadata = storageMetadata {
      contentType = PHOTO_CONTENT_TYPE
      setCustomMetadata(UPLOADER_METADATA, uploader)
    }
    ref.putFile(uri, metadata).await()
    return ref.downloadUrl.await().toString()
  }

  private companion object {
    // The Storage folder of listing photos, listings/{listingId}/ (see ARCHITECTURE.md).
    const val PHOTOS_FOLDER = "listings"
    const val PHOTO_CONTENT_TYPE = "image/jpeg"

    // The custom metadata holding the uid of the user who uploaded a photo (see storage.rules).
    const val UPLOADER_METADATA = "uploader"
  }
}
