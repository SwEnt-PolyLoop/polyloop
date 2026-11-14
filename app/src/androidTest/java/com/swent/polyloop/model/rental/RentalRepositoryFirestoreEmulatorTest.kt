// Made with Claude.

package com.swent.polyloop.model.rental

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.swent.polyloop.utils.FirebaseEmulator
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the real [RentalRepositoryFirestore] against the Firestore emulator (not mocks), so these
 * tests see how Firestore and its rules really behave. Needs `firebase emulators:start`.
 *
 * The CI starts no Functions emulator, so the requests are written as an admin, as the
 * `requestRental` Cloud Function writes them; the Function itself is tested in functions/src, and
 * the call to it in RentalRepositoryFirestoreTest.
 */
@RunWith(AndroidJUnit4::class)
class RentalRepositoryFirestoreEmulatorTest {

  // Created here (not in setUp): creating it does not contact Firestore yet, so connect() can still
  // switch it to the emulator.
  private val repository = RentalRepositoryFirestore()

  private val start = LocalDate.of(2026, 10, 20)
  private val end = LocalDate.of(2026, 10, 22)
  private val createdAt = Instant.parse("2026-10-14T10:00:00Z")

  // New accounts for each test, so rentals left by earlier runs never get in the way. The borrower
  // is signed in after setUp: the rules let each side read only its own rentals.
  private val ownerEmail = "owner-${UUID.randomUUID()}@epfl.ch"
  private lateinit var ownerId: String
  private lateinit var borrowerId: String

  @Before
  fun setUp() {
    FirebaseEmulator.connect()
    runBlocking {
      ownerId = FirebaseEmulator.signUpVerified(ownerEmail, PASSWORD)
      borrowerId =
          FirebaseEmulator.signUpVerified("borrower-${UUID.randomUUID()}@epfl.ch", PASSWORD)
    }
  }

  @After
  fun tearDown() {
    Firebase.auth.signOut()
  }

  /**
   * Writes a request from the borrower ending on [endDate], created [minutesLater] after the first.
   */
  private fun seedRequest(endDate: LocalDate = end, minutesLater: Long = 0): Rental {
    val id = "rental-${UUID.randomUUID()}"
    val time = Timestamp(createdAt.plusSeconds(minutesLater * 60).epochSecond, 0)
    FirebaseEmulator.createDocumentAsAdmin(
        "${RentalFields.COLLECTION}/$id",
        mapOf(
            RentalFields.LISTING_ID to "listing-1",
            RentalFields.LISTING_TITLE to "Camping tent",
            RentalFields.LISTING_PHOTO_URL to "",
            RentalFields.OWNER_ID to ownerId,
            RentalFields.OWNER_NAME to "Vida",
            RentalFields.BORROWER_ID to borrowerId,
            RentalFields.BORROWER_NAME to "Loca",
            RentalFields.PARTICIPANTS to listOf(ownerId, borrowerId),
            RentalFields.START_DATE to start.toString(),
            RentalFields.END_DATE to endDate.toString(),
            RentalFields.STATUS to "REQUESTED",
            RentalFields.PRICE_PER_DAY to 5,
            RentalFields.TOTAL_PRICE to 15,
            RentalFields.PICKUP_AREA to "Ecublens",
            RentalFields.DROP_OFF_AREA to "Renens",
            RentalFields.PICKUP_ADDRESS to "",
            RentalFields.RETURN_ADDRESS to "",
            RentalFields.CREATED_AT to time,
            RentalFields.LAST_MESSAGE to "",
            RentalFields.LAST_MESSAGE_AT to time,
        ),
    )
    return Rental(
        id = id,
        listingId = "listing-1",
        listingTitle = "Camping tent",
        ownerId = ownerId,
        ownerName = "Vida",
        borrowerId = borrowerId,
        borrowerName = "Loca",
        startDate = start,
        endDate = endDate,
        status = RentalStatus.REQUESTED,
        pricePerDay = 5,
        totalPrice = 15,
        pickupArea = "Ecublens",
        dropOffArea = "Renens",
        createdAt = time.toDate().toInstant(),
    )
  }

  @Test
  fun getRentalReadsARequestBackForItsBorrower() {
    runBlocking {
      val rental = seedRequest()

      assertEquals(rental, repository.getRental(rental.id).getOrThrow())
    }
  }

  @Test
  fun eachSideSeesItsRentalsNewestFirst() {
    runBlocking {
      val first = seedRequest()
      val second = seedRequest(endDate = start, minutesLater = 1)

      val borrowed =
          withTimeout(TIMEOUT_MS) {
            repository.observeRentalsAsBorrower(borrowerId).first { it.size == 2 }
          }
      // The rules let each side query only its own rentals.
      Firebase.auth.signInWithEmailAndPassword(ownerEmail, PASSWORD).await()
      val lent =
          withTimeout(TIMEOUT_MS) {
            repository.observeRentalsAsLender(ownerId).first { it.size == 2 }
          }

      assertEquals(listOf(second, first), borrowed)
      assertEquals(listOf(second, first), lent)
      assertEquals(emptyList<Rental>(), repository.observeRentalsAsBorrower(ownerId).first())
    }
  }

  @Test
  fun observeSkipsMalformedRentals() {
    runBlocking {
      FirebaseEmulator.createDocumentAsAdmin(
          "${RentalFields.COLLECTION}/broken-${UUID.randomUUID()}",
          mapOf(RentalFields.BORROWER_ID to borrowerId, RentalFields.STATUS to "UNKNOWN"),
      )
      val rental = seedRequest()

      val borrowed =
          withTimeout(TIMEOUT_MS) {
            repository.observeRentalsAsBorrower(borrowerId).first { it.isNotEmpty() }
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
