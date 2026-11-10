// Made with Claude.

package com.swent.polyloop.model.dispute

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.swent.polyloop.utils.FirebaseEmulator
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the real [DisputeRepositoryFirestore] against the Firestore emulator (not mocks), read by a
 * participant of the rental, so the Security Rules apply. Clients cannot write disputes, so the
 * data is seeded as an admin, as Cloud Functions would. The calls to the Cloud Functions are tested
 * in `DisputeRepositoryFirestoreTest`: CI starts no Functions emulator. Needs `firebase
 * emulators:start`.
 */
@RunWith(AndroidJUnit4::class)
class DisputeRepositoryFirestoreEmulatorTest {

  private val repository = DisputeRepositoryFirestore()

  private lateinit var uid: String
  private val rentalId = "rental-${UUID.randomUUID()}"
  private val at = Instant.parse("2026-10-14T10:00:00Z")

  @Before
  fun setUp() {
    FirebaseEmulator.connect()
    runBlocking {
      uid = FirebaseEmulator.signUpVerified("dispute-${UUID.randomUUID()}@epfl.ch", PASSWORD)
    }
    // The rules read the participants of the dispute's rental.
    FirebaseEmulator.createDocumentAsAdmin(
        "rentals/$rentalId",
        mapOf("ownerId" to "lender", "borrowerId" to uid, "participants" to listOf("lender", uid)),
    )
  }

  @After
  fun tearDown() {
    FirebaseEmulator.deleteDocumentAsAdmin("disputes/$rentalId")
    FirebaseEmulator.deleteDocumentAsAdmin("rentals/$rentalId")
    Firebase.auth.signOut()
  }

  private fun seedDispute(vararg lenderPercents: Int) =
      FirebaseEmulator.createDocumentAsAdmin(
          "disputes/$rentalId",
          mapOf(
              "stage" to "SETTLE",
              "proposals" to
                  lenderPercents.map {
                    mapOf(
                        "authorId" to "lender",
                        "lenderPercent" to it,
                        "createdAt" to Timestamp(at),
                    )
                  },
              "escalatesAt" to Timestamp(at.plusSeconds(86_400)),
              "escalatedBy" to null,
          ),
      )

  @Test
  fun observeDisputeReadsTheStoredDisputeAndFollowsItsChanges() {
    seedDispute(70)

    runBlocking {
      assertEquals(
          Dispute(
              rentalId = rentalId,
              stage = DisputeStage.SETTLE,
              proposals = listOf(Proposal("lender", 70, at)),
              escalatesAt = at.plusSeconds(86_400),
          ),
          withTimeout(TIMEOUT_MS) { repository.observeDispute(rentalId).first() },
      )
      FirebaseEmulator.deleteDocumentAsAdmin("disputes/$rentalId")
      seedDispute(70, 60)
      val updated =
          withTimeout(TIMEOUT_MS) {
            repository.observeDispute(rentalId).first { it?.proposals?.size == 2 }
          }
      assertEquals(60, updated?.latestProposal?.lenderPercent)
    }
  }

  @Test
  fun observeDisputeGivesNullForARentalWithoutDispute() {
    runBlocking {
      assertNull(withTimeout(TIMEOUT_MS) { repository.observeDispute(rentalId).first() })
    }
  }

  private companion object {
    const val PASSWORD = "password123"
    const val TIMEOUT_MS = 10_000L
  }
}
