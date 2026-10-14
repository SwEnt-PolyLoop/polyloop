// Made with Claude.

package com.swent.polyloop.model.rental

import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.model.listing.ListingStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RentalRepositoryFirestoreTest {

  private val db = mockk<FirebaseFirestore>()
  private val collection = mockk<CollectionReference>()
  private val repository = RentalRepositoryFirestore(db)

  private val firestoreError =
      FirebaseFirestoreException("unavailable", FirebaseFirestoreException.Code.UNAVAILABLE)

  private val listing =
      Listing(
          id = "listing-1",
          ownerId = "owner",
          title = "Camping tent",
          description = "Light 2-person tent.",
          category = ListingCategory.SPORTS_OUTDOOR,
          photoUrls = List(3) { "https://example.com/$it.jpg" },
          pricePerDay = 5,
          itemValue = 120,
          availableFrom = LocalDate.of(2026, 10, 1),
          availableTo = LocalDate.of(2026, 12, 31),
          status = ListingStatus.PUBLISHED,
          pickupArea = "Ecublens",
          dropOffArea = "Renens",
      )

  private val borrower = AuthUser(uid = "borrower", email = "loca@epfl.ch", name = "Loca")
  private val start = LocalDate.of(2026, 10, 20)
  private val end = LocalDate.of(2026, 10, 22)

  /** The fields of a stored rental, as Firestore returns them, created at [createdAt]. */
  private fun storedData(createdAt: Instant, status: String = "REQUESTED"): Map<String, Any> =
      mapOf(
          RentalFields.LISTING_ID to "listing-1",
          RentalFields.LISTING_TITLE to "Camping tent",
          RentalFields.OWNER_ID to "owner",
          RentalFields.OWNER_NAME to "Vida",
          RentalFields.BORROWER_ID to "borrower",
          RentalFields.BORROWER_NAME to "Loca",
          RentalFields.START_DATE to "2026-10-20",
          RentalFields.END_DATE to "2026-10-22",
          RentalFields.STATUS to status,
          RentalFields.PRICE_PER_DAY to 5L,
          RentalFields.TOTAL_PRICE to 15L,
          RentalFields.PICKUP_AREA to "Ecublens",
          RentalFields.DROP_OFF_AREA to "Renens",
          RentalFields.CREATED_AT to Timestamp(createdAt),
          RentalFields.LAST_MESSAGE_AT to Timestamp(createdAt),
      )

  private fun expectedRental(id: String, createdAt: Instant) =
      Rental(
          id = id,
          listingId = "listing-1",
          listingTitle = "Camping tent",
          ownerId = "owner",
          ownerName = "Vida",
          borrowerId = "borrower",
          borrowerName = "Loca",
          startDate = start,
          endDate = end,
          status = RentalStatus.REQUESTED,
          pricePerDay = 5,
          totalPrice = 15,
          pickupArea = "Ecublens",
          dropOffArea = "Renens",
          createdAt = createdAt,
      )

  /** A document snapshot of [id] holding [data] (null: the document does not exist). */
  private fun snapshot(id: String, data: Map<String, Any>?): DocumentSnapshot = mockk {
    every { this@mockk.id } returns id
    every { getData(ServerTimestampBehavior.ESTIMATE) } returns data
  }

  private val createdAt = Instant.parse("2026-10-14T10:00:00Z")

  @Before
  fun setUp() {
    every { db.collection(RentalFields.COLLECTION) } returns collection
  }

  // ---------- requestRental ----------

  private val newDocument = mockk<DocumentReference>()
  private val written = slot<Any>()

  private fun newDocumentIsWritten() {
    every { collection.document() } returns newDocument
    every { newDocument.id } returns "new-id"
    every { newDocument.set(capture(written)) } returns Tasks.forResult(null)
  }

  @Test
  fun requestRentalWritesTheRequestAndReturnsItAsStored() = runTest {
    newDocumentIsWritten()
    every { newDocument.get() } returns Tasks.forResult(snapshot("new-id", storedData(createdAt)))

    val rental = repository.requestRental(listing, "Vida", borrower, start, end).getOrThrow()

    assertEquals(expectedRental("new-id", createdAt), rental)
    @Suppress("UNCHECKED_CAST") val fields = written.captured as Map<String, Any>
    assertEquals(rentalRequestDocument(listing, "Vida", borrower, start, end).keys, fields.keys)
    assertEquals("REQUESTED", fields[RentalFields.STATUS])
    assertEquals("Vida", fields[RentalFields.OWNER_NAME])
  }

  @Test
  fun requestRentalRejectsReversedDatesWithoutWriting() = runTest {
    val result = repository.requestRental(listing, "Vida", borrower, end, start)

    assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    verify(exactly = 0) { db.collection(any()) }
  }

  @Test
  fun requestRentalFailsWhenTheWriteFails() = runTest {
    every { collection.document() } returns newDocument
    every { newDocument.set(any()) } returns Tasks.forException(firestoreError)

    val result = repository.requestRental(listing, "Vida", borrower, start, end)

    assertSame(firestoreError, result.exceptionOrNull())
  }

  /** The server never confirms the write: it stays queued in Firestore, which sends it later. */
  private fun newDocumentIsQueued() {
    every { collection.document() } returns newDocument
    every { newDocument.id } returns "new-id"
    every { newDocument.set(any()) } returns TaskCompletionSource<Void>().task
  }

  @Test
  fun requestRentalReturnsTheQueuedRentalFromTheCacheWhenTheServerIsTooSlow() = runTest {
    newDocumentIsQueued()
    every { newDocument.get(Source.CACHE) } returns
        Tasks.forResult(snapshot("new-id", storedData(createdAt)))

    val rental = repository.requestRental(listing, "Vida", borrower, start, end).getOrThrow()

    // Not a failure, so the user is not led to request again and write a second rental.
    assertEquals(expectedRental("new-id", createdAt), rental)
    verify(exactly = 0) { newDocument.get() }
  }

  @Test
  fun requestRentalFailsWhenTheQueuedRentalIsNotInTheCache() = runTest {
    newDocumentIsQueued()
    every { newDocument.get(Source.CACHE) } returns Tasks.forException(firestoreError)

    val result = repository.requestRental(listing, "Vida", borrower, start, end)

    assertSame(firestoreError, result.exceptionOrNull())
  }

  @Test
  fun requestRentalFailsWhenTheStoredRentalCannotBeRead() = runTest {
    newDocumentIsWritten()
    every { newDocument.get() } returns Tasks.forResult(snapshot("new-id", null))

    val result = repository.requestRental(listing, "Vida", borrower, start, end)

    assertTrue(result.exceptionOrNull() is IllegalStateException)
  }

  // ---------- getRental ----------

  private val rentalDocument = mockk<DocumentReference>()

  @Test
  fun getRentalReadsTheRental() = runTest {
    every { collection.document("rental-1") } returns rentalDocument
    every { rentalDocument.get() } returns
        Tasks.forResult(snapshot("rental-1", storedData(createdAt)))

    assertEquals(
        expectedRental("rental-1", createdAt),
        repository.getRental("rental-1").getOrThrow(),
    )
  }

  @Test
  fun getRentalReturnsNullForAMissingOrMalformedRental() = runTest {
    every { collection.document("missing") } returns rentalDocument
    every { rentalDocument.get() } returns Tasks.forResult(snapshot("missing", null))
    val malformed = mockk<DocumentReference>()
    every { collection.document("malformed") } returns malformed
    every { malformed.get() } returns
        Tasks.forResult(snapshot("malformed", storedData(createdAt, status = "UNKNOWN")))

    assertNull(repository.getRental("missing").getOrThrow())
    assertNull(repository.getRental("malformed").getOrThrow())
  }

  @Test
  fun getRentalReturnsNullForAnInvalidIdWithoutReading() = runTest {
    assertNull(repository.getRental("").getOrThrow())
    assertNull(repository.getRental("rentals/1").getOrThrow())
    verify(exactly = 0) { db.collection(any()) }
  }

  @Test
  fun getRentalFailsWhenTheReadFails() = runTest {
    every { collection.document("rental-1") } returns rentalDocument
    every { rentalDocument.get() } returns Tasks.forException(firestoreError)

    assertSame(firestoreError, repository.getRental("rental-1").exceptionOrNull())
  }

  // ---------- observeRentalsAsBorrower / AsLender ----------

  private val query = mockk<Query>()
  private val registration = mockk<ListenerRegistration>(relaxed = true)
  private val listener = slot<EventListener<QuerySnapshot>>()

  private fun queryOn(field: String, uid: String) {
    every { collection.whereEqualTo(field, uid) } returns query
    every { query.addSnapshotListener(capture(listener)) } returns registration
  }

  private fun querySnapshot(vararg documents: DocumentSnapshot): QuerySnapshot = mockk {
    every { this@mockk.documents } returns documents.toList()
  }

  @Test
  fun observeRentalsAsBorrowerEmitsTheBorrowersRentalsNewestFirst() = runTest {
    queryOn(RentalFields.BORROWER_ID, "borrower")
    val older = Instant.parse("2026-10-13T10:00:00Z")

    val rentals = async { repository.observeRentalsAsBorrower("borrower").first() }
    runCurrent()
    listener.captured.onEvent(
        querySnapshot(
            snapshot("old", storedData(older)),
            snapshot("broken", storedData(older, status = "UNKNOWN")),
            snapshot("new", storedData(createdAt)),
        ),
        null,
    )

    assertEquals(
        listOf(expectedRental("new", createdAt), expectedRental("old", older)),
        rentals.await(),
    )
  }

  @Test
  fun observeRentalsAsLenderQueriesByOwnerAndEmitsEachChange() = runTest {
    queryOn(RentalFields.OWNER_ID, "owner")

    val emissions = async { repository.observeRentalsAsLender("owner").take(2).toList() }
    runCurrent()
    listener.captured.onEvent(querySnapshot(), null)
    listener.captured.onEvent(querySnapshot(snapshot("r1", storedData(createdAt))), null)

    assertEquals(listOf(emptyList(), listOf(expectedRental("r1", createdAt))), emissions.await())
  }

  @Test
  fun observeThrowsReadErrorsToTheCollector() = runTest {
    queryOn(RentalFields.BORROWER_ID, "borrower")

    val error = async {
      var caught: Throwable? = null
      repository.observeRentalsAsBorrower("borrower").catch { caught = it }.toList()
      caught
    }
    runCurrent()
    listener.captured.onEvent(null, firestoreError)

    assertSame(firestoreError, error.await())
  }
}
