// Made with Claude.

package com.swent.polyloop.model.listing

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

class ListingRepositoryFirestoreTest {

  private val db = mockk<FirebaseFirestore>()
  private val collection = mockk<CollectionReference>()
  private val publishedQuery = mockk<Query>()
  private val documentRef = mockk<DocumentReference>()
  private val repository = ListingRepositoryFirestore(db)

  private val photoUrls = List(3) { "https://example.com/photo$it.jpg" }

  private val validData: Map<String, Any> =
      mapOf(
          ListingFields.OWNER_ID to "user1",
          ListingFields.TITLE to "Camping tent, 2 people",
          ListingFields.DESCRIPTION to "Light 2-person tent, used three times.",
          ListingFields.CATEGORY to "SPORTS_OUTDOOR",
          ListingFields.PHOTO_URLS to photoUrls,
          ListingFields.PRICE_PER_DAY to 15L,
          ListingFields.ITEM_VALUE to 180L,
          ListingFields.AVAILABLE_FROM to "2026-10-06",
          ListingFields.AVAILABLE_TO to "2026-10-20",
          ListingFields.STATUS to "PUBLISHED",
          ListingFields.PICKUP_AREA to "Ecublens",
          ListingFields.DROP_OFF_AREA to "Ecublens",
      )

  private val brokenData: Map<String, Any> = validData - ListingFields.TITLE

  private val firestoreError =
      FirebaseFirestoreException("unavailable", FirebaseFirestoreException.Code.UNAVAILABLE)

  private fun expectedListing(id: String) =
      Listing(
          id = id,
          ownerId = "user1",
          title = "Camping tent, 2 people",
          description = "Light 2-person tent, used three times.",
          category = ListingCategory.SPORTS_OUTDOOR,
          photoUrls = photoUrls,
          pricePerDay = 15,
          itemValue = 180,
          availableFrom = LocalDate.of(2026, 10, 6),
          availableTo = LocalDate.of(2026, 10, 20),
          status = ListingStatus.PUBLISHED,
          pickupArea = "Ecublens",
          dropOffArea = "Ecublens",
      )

  private fun document(docId: String, data: Map<String, Any>?): DocumentSnapshot = mockk {
    every { id } returns docId
    every { this@mockk.data } returns data
  }

  private fun stubPublishedQuery(vararg documents: DocumentSnapshot) {
    val snapshot =
        mockk<QuerySnapshot> { every { this@mockk.documents } returns documents.toList() }
    every { publishedQuery.get() } returns Tasks.forResult(snapshot)
  }

  private fun stubDocument(id: String, data: Map<String, Any>?) {
    every { collection.document(id) } returns documentRef
    every { documentRef.get() } returns Tasks.forResult(document(id, data))
  }

  @Before
  fun setUp() {
    every { db.collection(ListingFields.COLLECTION) } returns collection
    every { collection.whereEqualTo(ListingFields.STATUS, ListingStatus.PUBLISHED.name) } returns
        publishedQuery
  }

  @Test
  fun getAllListingsReturnsMappedListings() = runTest {
    stubPublishedQuery(document("listing1", validData), document("listing2", validData))

    val result = repository.getAllListings()

    assertEquals(
        listOf(expectedListing("listing1"), expectedListing("listing2")),
        result.getOrThrow(),
    )
  }

  @Test
  fun getAllListingsSkipsBrokenDocument() = runTest {
    stubPublishedQuery(document("listing1", validData), document("broken", brokenData))

    val result = repository.getAllListings()

    assertEquals(listOf(expectedListing("listing1")), result.getOrThrow())
  }

  @Test
  fun getAllListingsReturnsFailureOnFirestoreError() = runTest {
    every { publishedQuery.get() } returns Tasks.forException(firestoreError)

    val result = repository.getAllListings()

    assertSame(firestoreError, result.exceptionOrNull())
  }

  @Test
  fun getAllListingsQueriesOnlyPublishedListings() = runTest {
    stubPublishedQuery()

    repository.getAllListings()

    verify(exactly = 1) { collection.whereEqualTo(ListingFields.STATUS, "PUBLISHED") }
  }

  @Test
  fun getListingReturnsListingWhenFound() = runTest {
    stubDocument("listing1", validData)

    val result = repository.getListing("listing1")

    assertEquals(expectedListing("listing1"), result.getOrThrow())
  }

  @Test
  fun getListingReturnsNullWhenNotFound() = runTest {
    stubDocument("missing", null)

    val result = repository.getListing("missing")

    assertNull(result.getOrThrow())
  }

  @Test
  fun getListingReturnsNullForUnmappableDocument() = runTest {
    stubDocument("broken", brokenData)

    val result = repository.getListing("broken")

    assertNull(result.getOrThrow())
  }

  @Test
  fun getListingReturnsFailureOnFirestoreError() = runTest {
    every { collection.document("listing1") } returns documentRef
    every { documentRef.get() } returns Tasks.forException(firestoreError)

    val result = repository.getListing("listing1")

    assertSame(firestoreError, result.exceptionOrNull())
  }

  @Test
  fun getListingReturnsNullForBlankIdWithoutCallingFirestore() = runTest {
    val result = repository.getListing("  ")

    assertNull(result.getOrThrow())
    verify(exactly = 0) { db.collection(any()) }
  }

  @Test
  fun getListingReturnsNullForIdWithSlashWithoutCallingFirestore() = runTest {
    val result = repository.getListing("listings/listing1")

    assertNull(result.getOrThrow())
    verify(exactly = 0) { db.collection(any()) }
  }

  @Test
  fun getListingReturnsDraftListing() = runTest {
    stubDocument("draft1", validData + (ListingFields.STATUS to "DRAFT"))

    val result = repository.getListing("draft1")

    assertEquals(
        expectedListing("draft1").copy(status = ListingStatus.DRAFT),
        result.getOrThrow(),
    )
  }
}
