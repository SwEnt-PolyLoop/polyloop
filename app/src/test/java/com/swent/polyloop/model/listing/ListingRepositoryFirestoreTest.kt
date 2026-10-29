// Made with Claude.

package com.swent.polyloop.model.listing

import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ListingRepositoryFirestoreTest {

  private val db = mockk<FirebaseFirestore>()
  private val collection = mockk<CollectionReference>()
  private val publishedQuery = mockk<Query>()
  private val documentRef = mockk<DocumentReference>()
  private val repository = ListingRepositoryFirestore(db, storageProvider = { mockk() })

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
  fun getListingReturnsFailureWhenFirebaseCancelsTheRead() = runTest {
    every { collection.document("listing1") } returns documentRef
    every { documentRef.get() } returns Tasks.forCanceled()
    assertTrue(repository.getListing("listing1").isFailure)
  }

  @Test
  fun getAllListingsReturnsFailureWhenFirebaseCancelsTheRead() = runTest {
    every { publishedQuery.get() } returns Tasks.forCanceled()
    assertTrue(repository.getAllListings().isFailure)
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

  private fun range(start: String, end: String) =
      mapOf(ListingFields.RANGE_START to start, ListingFields.RANGE_END to end)

  @Test
  fun getListingReadsBookedRanges() = runTest {
    val ranges = listOf(range("2026-10-06", "2026-10-08"), range("2026-10-12", "2026-10-12"))
    stubDocument("listing1", validData + (ListingFields.BOOKED_RANGES to ranges))

    val result = repository.getListing("listing1")

    assertEquals(
        listOf(
            BookedRange(LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 8)),
            BookedRange(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 12)),
        ),
        result.getOrThrow()?.bookedRanges,
    )
  }

  @Test
  fun getListingSkipsMalformedBookedRangesAndKeepsTheListing() = runTest {
    val ranges =
        listOf(
            range("2026-10-06", "2026-10-08"),
            range("hello", "2026-10-08"), // malformed date
            range("2026-10-10", "2026-10-09"), // ends before it starts
            mapOf(ListingFields.RANGE_START to "2026-10-12"), // no end
            "2026-10-14", // not a map
        )
    stubDocument("listing1", validData + (ListingFields.BOOKED_RANGES to ranges))

    val result = repository.getListing("listing1")

    assertEquals(
        listOf(BookedRange(LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 8))),
        result.getOrThrow()?.bookedRanges,
    )
  }

  @Test
  fun getBookedRangesReadsTheServerNotTheCache() = runTest {
    val ranges = listOf(range("2026-10-06", "2026-10-08"))
    every { collection.document("listing1") } returns documentRef
    every { documentRef.get(Source.SERVER) } returns
        Tasks.forResult(document("listing1", validData + (ListingFields.BOOKED_RANGES to ranges)))

    val result = repository.getBookedRanges("listing1")

    assertEquals(
        listOf(BookedRange(LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 8))),
        result.getOrThrow(),
    )
  }

  @Test
  fun getBookedRangesReturnsFailureOnFirestoreError() = runTest {
    every { collection.document("listing1") } returns documentRef
    every { documentRef.get(Source.SERVER) } returns Tasks.forException(firestoreError)

    assertSame(firestoreError, repository.getBookedRanges("listing1").exceptionOrNull())
  }

  @Test
  fun getBookedRangesReturnsFailureWhenFirebaseCancelsTheRead() = runTest {
    every { collection.document("listing1") } returns documentRef
    every { documentRef.get(Source.SERVER) } returns Tasks.forCanceled()

    assertTrue(repository.getBookedRanges("listing1").isFailure)
  }

  @Test
  fun getBookedRangesIsNullForMissingListingOrInvalidId() = runTest {
    every { collection.document("missing") } returns documentRef
    every { documentRef.get(Source.SERVER) } returns Tasks.forResult(document("missing", null))

    assertNull(repository.getBookedRanges("missing").getOrThrow())
    assertNull(repository.getBookedRanges("listings/listing1").getOrThrow())
  }

  @Test
  fun getBookedRangesIsNullForDraftListing() = runTest {
    val draft = validData + (ListingFields.STATUS to "DRAFT")
    every { collection.document("listing1") } returns documentRef
    every { documentRef.get(Source.SERVER) } returns Tasks.forResult(document("listing1", draft))

    assertNull(repository.getBookedRanges("listing1").getOrThrow())
  }

  @Test
  fun getBookedRangesIgnoresOtherMalformedFields() = runTest {
    val ranges = listOf(range("2026-10-06", "2026-10-08"))
    every { collection.document("listing1") } returns documentRef
    every { documentRef.get(Source.SERVER) } returns
        Tasks.forResult(document("listing1", brokenData + (ListingFields.BOOKED_RANGES to ranges)))

    assertEquals(
        listOf(BookedRange(LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 8))),
        repository.getBookedRanges("listing1").getOrThrow(),
    )
  }

  @Test
  fun createListingRejectsInvalidListingWithoutCallingFirebase() = runTest {
    var storageCalls = 0
    val repository =
        ListingRepositoryFirestore(
            db,
            storageProvider = {
              storageCalls++
              mockk()
            },
        )
    val addresses = ListingAddresses(pickup = "Ecublens", dropOff = "Ecublens")

    // Two photos, one fewer than Listing.MIN_PHOTOS.
    val result = repository.createListing(expectedListing(""), addresses, List(2) { mockk<Uri>() })

    assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    assertEquals(0, storageCalls)
    verify(exactly = 0) { db.collection(any()) }
    verify(exactly = 0) { db.batch() }
  }

  // ---------- getListingsByOwner ----------

  private val ownerQuery = mockk<Query>()

  private fun stubOwnerQuery(vararg documents: DocumentSnapshot) {
    val snapshot =
        mockk<QuerySnapshot> { every { this@mockk.documents } returns documents.toList() }
    every { collection.whereEqualTo(ListingFields.OWNER_ID, "user1") } returns ownerQuery
    every { ownerQuery.get() } returns Tasks.forResult(snapshot)
  }

  @Test
  fun getListingsByOwnerReturnsTheOwnersListingsWhateverTheirStatus() = runTest {
    stubOwnerQuery(
        document("listing1", validData),
        document("draft1", validData + (ListingFields.STATUS to "DRAFT")),
    )

    val result = repository.getListingsByOwner("user1")

    assertEquals(
        listOf(
            expectedListing("listing1"),
            expectedListing("draft1").copy(status = ListingStatus.DRAFT),
        ),
        result.getOrThrow(),
    )
    verify(exactly = 0) { collection.whereEqualTo(ListingFields.STATUS, "PUBLISHED") }
  }

  @Test
  fun getListingsByOwnerSkipsBrokenDocument() = runTest {
    stubOwnerQuery(document("listing1", validData), document("broken", brokenData))

    assertEquals(
        listOf(expectedListing("listing1")),
        repository.getListingsByOwner("user1").getOrThrow(),
    )
  }

  @Test
  fun getListingsByOwnerReturnsFailureOnFirestoreError() = runTest {
    stubOwnerQuery()
    every { ownerQuery.get() } returns Tasks.forException(firestoreError)

    assertSame(firestoreError, repository.getListingsByOwner("user1").exceptionOrNull())
  }

  @Test
  fun getListingsByOwnerReturnsNothingForBlankIdWithoutCallingFirestore() = runTest {
    assertEquals(emptyList<Listing>(), repository.getListingsByOwner(" ").getOrThrow())
    verify(exactly = 0) { db.collection(any()) }
  }
}
