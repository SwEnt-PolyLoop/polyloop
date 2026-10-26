// Made with Claude.

package com.swent.polyloop.model.rental

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.model.listing.ListingStatus
import com.swent.polyloop.model.listing.listingToDocument
import com.swent.polyloop.utils.FirebaseEmulator
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the real [RentalRepositoryFirestore] against the Firestore emulator (not mocks), so these
 * tests see how Firestore really behaves, e.g. server timestamps. Needs `firebase emulators:start`.
 */
@RunWith(AndroidJUnit4::class)
class RentalRepositoryFirestoreEmulatorTest {

  // Created here (not in setUp): creating it does not contact Firestore yet, so connect() can still
  // switch it to the emulator.
  private val repository = RentalRepositoryFirestore()

  private val start = LocalDate.of(2026, 10, 20)
  private val end = LocalDate.of(2026, 10, 22)

  // New accounts for each test, so rentals left by earlier runs never get in the way. The borrower
  // is signed in after setUp: the rules let only them send their requests.
  private val ownerEmail = "owner-${UUID.randomUUID()}@epfl.ch"
  private lateinit var ownerId: String
  private lateinit var borrower: AuthUser
  private lateinit var listing: Listing

  @Before
  fun setUp() {
    FirebaseEmulator.connect()
    runBlocking {
      ownerId = FirebaseEmulator.signUpVerified(ownerEmail, PASSWORD)
      val borrowerEmail = "borrower-${UUID.randomUUID()}@epfl.ch"
      val borrowerId = FirebaseEmulator.signUpVerified(borrowerEmail, PASSWORD)
      borrower = AuthUser(borrowerId, borrowerEmail, "Loca")
    }
    listing =
        Listing(
            id = "listing-${UUID.randomUUID()}",
            ownerId = ownerId,
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
    // The rules check each request against its listing, which only the owner may write.
    FirebaseEmulator.createDocumentAsAdmin("listings/${listing.id}", listingToDocument(listing))
  }

  @After
  fun tearDown() {
    Firebase.auth.signOut()
  }

  @Test
  fun requestRentalStoresARequestThatGetRentalReadsBack() {
    runBlocking {
      val rental = repository.requestRental(listing, "Vida", borrower, start, end).getOrThrow()

      assertEquals(RentalStatus.REQUESTED, rental.status)
      assertEquals("Vida", rental.ownerName)
      assertEquals("Loca", rental.borrowerName)
      assertEquals(15, rental.totalPrice)
      assertEquals(rental.createdAt, rental.lastMessageAt)
      assertEquals(rental, repository.getRental(rental.id).getOrThrow())
    }
  }

  @Test
  fun requestRentalStoresTheParticipantsAndServerTimestamps() {
    runBlocking {
      val rental = repository.requestRental(listing, "Vida", borrower, start, end).getOrThrow()

      val stored = Firebase.firestore.collection("rentals").document(rental.id).get().await()
      assertEquals(listOf(ownerId, borrower.uid), stored.get("participants"))
      assertTrue(stored.get("createdAt") is Timestamp)
      assertEquals("2026-10-20", stored.getString("startDate"))
    }
  }

  @Test
  fun eachSideSeesItsRentalsNewestFirst() {
    runBlocking {
      val first = repository.requestRental(listing, "Vida", borrower, start, end).getOrThrow()
      val second = repository.requestRental(listing, "Vida", borrower, start, start).getOrThrow()

      val borrowed =
          withTimeout(TIMEOUT_MS) {
            repository.observeRentalsAsBorrower(borrower.uid).first { it.size == 2 }
          }
      // The rules let each side query only its own rentals.
      Firebase.auth.signInWithEmailAndPassword(ownerEmail, PASSWORD).await()
      val lent =
          withTimeout(TIMEOUT_MS) {
            repository.observeRentalsAsLender(ownerId).first { it.size == 2 }
          }

      assertEquals(listOf(second, first), borrowed)
      assertEquals(listOf(second, first), lent)
      assertEquals(
          emptyList<Rental>(),
          repository.observeRentalsAsBorrower(ownerId).first(),
      )
    }
  }

  @Test
  fun observeSkipsMalformedRentals() {
    runBlocking {
      FirebaseEmulator.createDocumentAsAdmin(
          "rentals/broken-${UUID.randomUUID()}",
          mapOf("borrowerId" to borrower.uid, "status" to "UNKNOWN"),
      )
      val rental = repository.requestRental(listing, "Vida", borrower, start, end).getOrThrow()

      val borrowed =
          withTimeout(TIMEOUT_MS) {
            repository.observeRentalsAsBorrower(borrower.uid).first { it.isNotEmpty() }
          }

      assertEquals(listOf(rental), borrowed)
    }
  }

  @Test
  fun getRentalReturnsNullWhenThereIsNoRental() {
    runBlocking { assertNull(repository.getRental("missing-${UUID.randomUUID()}").getOrThrow()) }
  }

  private companion object {
    const val PASSWORD = "password123"
    const val TIMEOUT_MS = 10_000L
  }
}
