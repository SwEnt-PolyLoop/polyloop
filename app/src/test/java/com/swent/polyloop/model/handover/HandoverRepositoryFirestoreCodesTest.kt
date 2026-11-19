// Made with Claude.

package com.swent.polyloop.model.handover

import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.HttpsCallableReference
import com.google.firebase.functions.HttpsCallableResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The handover code methods of [HandoverRepositoryFirestore], with Firebase mocked: they read the
 * right document and field, and send a scan to `confirmHandover`, turning its refusals into the
 * exceptions the app expects.
 */
class HandoverRepositoryFirestoreCodesTest {

  private val codeSnapshot = mockk<DocumentSnapshot>()
  private val rentalSnapshot = mockk<DocumentSnapshot>()
  private val codes = mockk<CollectionReference>()
  private val rental = mockk<DocumentReference>()
  private val db = mockk<FirebaseFirestore>()
  private val auth = mockk<FirebaseAuth>()
  private val functions = mockk<FirebaseFunctions>()
  private val callable = mockk<HttpsCallableReference>()
  private val sent = slot<Any>()

  private val repository = HandoverRepositoryFirestore(db, auth, { functions }) { mockk() }

  @Before
  fun setUp() {
    every { auth.currentUser?.uid } returns "u1"
    every { db.collection("rentals") } returns mockk { every { document("r1") } returns rental }
    every { rental.collection("handoverCodes") } returns codes
    for (phase in HandoverPhase.entries) {
      every { codes.document(phase.name) } returns
          mockk { every { get() } returns Tasks.forResult(codeSnapshot) }
    }
    every { rental.get() } returns Tasks.forResult(rentalSnapshot)
    every { functions.getHttpsCallable(any()) } returns callable
    every { callable.call(capture(sent)) } returns Tasks.forResult(mockk<HttpsCallableResult>())
  }

  // ---------- the code to show ----------

  @Test
  fun theCodeIsReadFromTheDocumentOfThePhase() = runTest {
    every { codeSnapshot.get("code") } returns "secret"

    assertEquals("secret", repository.getCodeToShow("r1", HandoverPhase.PICKUP).getOrThrow())
    assertEquals("secret", repository.getCodeToShow("r1", HandoverPhase.RETURN).getOrThrow())
    verify { codes.document("PICKUP") }
    verify { codes.document("RETURN") }
  }

  @Test
  fun aMissingBlankOrNonTextCodeIsNull() = runTest {
    for (value in listOf(null, "", "   ", 42L)) {
      every { codeSnapshot.get("code") } returns value
      assertNull(repository.getCodeToShow("r1", HandoverPhase.PICKUP).getOrThrow())
    }
  }

  @Test
  fun aCodeThatCannotBeReadIsAFailure() = runTest {
    // e.g. offline with no copy on the phone, or the rules refuse it to the other person
    val error =
        FirebaseFirestoreException("unavailable", FirebaseFirestoreException.Code.UNAVAILABLE)
    every { codes.document("PICKUP") } returns
        mockk { every { get() } returns Tasks.forException(error) }

    assertSame(error, repository.getCodeToShow("r1", HandoverPhase.PICKUP).exceptionOrNull())
  }

  // ---------- the fingerprint ----------

  @Test
  fun theFingerprintIsReadFromTheRentalFieldOfThePhase() = runTest {
    every { rentalSnapshot.get("pickupCodeHash") } returns "pickup-hash"
    every { rentalSnapshot.get("returnCodeHash") } returns "return-hash"

    assertEquals(
        "pickup-hash",
        repository.getCodeFingerprint("r1", HandoverPhase.PICKUP).getOrThrow(),
    )
    assertEquals(
        "return-hash",
        repository.getCodeFingerprint("r1", HandoverPhase.RETURN).getOrThrow(),
    )
  }

  @Test
  fun aMissingFingerprintIsNull() = runTest {
    every { rentalSnapshot.get("pickupCodeHash") } returns null

    assertNull(repository.getCodeFingerprint("r1", HandoverPhase.PICKUP).getOrThrow())
  }

  // ---------- reads refused before Firestore ----------

  @Test
  fun readsFailWithAnInvalidRentalIdOrNobodySignedIn() = runTest {
    for (id in listOf("", "a/b")) {
      assertTrue(
          repository.getCodeToShow(id, HandoverPhase.PICKUP).exceptionOrNull()
              is IllegalArgumentException
      )
      assertTrue(
          repository.getCodeFingerprint(id, HandoverPhase.PICKUP).exceptionOrNull()
              is IllegalArgumentException
      )
    }
    every { auth.currentUser } returns null
    assertTrue(
        repository.getCodeToShow("r1", HandoverPhase.PICKUP).exceptionOrNull()
            is IllegalStateException
    )
    verify(exactly = 0) { db.collection(any()) }
  }

  // ---------- sending a scan ----------

  @Test
  fun aScanIsSentToConfirmHandover() = runTest {
    assertTrue(repository.confirmScan("r1", HandoverPhase.PICKUP, "secret").isSuccess)

    verify { functions.getHttpsCallable("confirmHandover") }
    assertEquals(
        mapOf("rentalId" to "r1", "phase" to "PICKUP", "code" to "secret"),
        sent.captured,
    )
  }

  /** Makes the next call refused with [code] and [details], as an HttpsError from a Function. */
  private fun refusedWith(
      details: Any?,
      code: FirebaseFunctionsException.Code = FirebaseFunctionsException.Code.FAILED_PRECONDITION,
  ): FirebaseFunctionsException {
    // Its constructor is internal to the SDK, so the error is a mock of the same class.
    val refusal = mockk<FirebaseFunctionsException>(relaxed = true)
    every { refusal.code } returns code
    every { refusal.details } returns details
    every { callable.call(any()) } returns Tasks.forException(refusal)
    return refusal
  }

  private suspend fun scanError() =
      repository.confirmScan("r1", HandoverPhase.PICKUP, "secret").exceptionOrNull()

  @Test
  fun theRefusalReasonGivesTheAppsException() = runTest {
    refusedWith(mapOf("reason" to "WRONG_CODE"))
    assertTrue(scanError() is WrongCodeException)

    refusedWith(mapOf("reason" to "RENTAL_CHANGED"))
    assertTrue(scanError() is RentalChangedException)
  }

  @Test
  fun otherRefusalsAreRefusedScans() = runTest {
    // E.g. not the pickup day or the price cannot be paid (no reason), or not the borrower.
    for (refusal in
        listOf(
            refusedWith(details = null),
            refusedWith(mapOf("reason" to "SOMETHING_ELSE")),
            refusedWith(details = "WRONG_CODE"),
            refusedWith(
                mapOf("reason" to "WRONG_CODE"),
                code = FirebaseFunctionsException.Code.PERMISSION_DENIED,
            ),
            refusedWith(null, code = FirebaseFunctionsException.Code.NOT_FOUND),
            refusedWith(null, code = FirebaseFunctionsException.Code.INVALID_ARGUMENT),
            refusedWith(null, code = FirebaseFunctionsException.Code.UNAUTHENTICATED),
        )) {
      every { callable.call(any()) } returns Tasks.forException(refusal)
      assertTrue(scanError() is ScanRefusedException)
    }
  }

  @Test
  fun aScanWithoutAnAnswerIsKeptAsItIs() = runTest {
    // The connection dropped or the server failed: sending it again may work.
    for (refusal in
        listOf(
            refusedWith(null, code = FirebaseFunctionsException.Code.UNAVAILABLE),
            refusedWith(null, code = FirebaseFunctionsException.Code.DEADLINE_EXCEEDED),
            refusedWith(null, code = FirebaseFunctionsException.Code.INTERNAL),
        )) {
      every { callable.call(any()) } returns Tasks.forException(refusal)
      assertSame(refusal, scanError())
    }
  }

  @Test
  fun aScanWithoutAnAnswerIn30SecondsIsNotSent() = runTest {
    // A stalled connection: the call never ends. The virtual clock skips the 30 s wait.
    every { callable.call(any()) } returns TaskCompletionSource<HttpsCallableResult>().task
    val started = testScheduler.currentTime

    assertTrue(scanError() is TimeoutException)
    assertEquals(30_000L, testScheduler.currentTime - started)
  }
}
