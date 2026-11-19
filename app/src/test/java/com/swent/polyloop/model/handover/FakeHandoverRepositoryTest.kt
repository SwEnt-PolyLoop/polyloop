// Made with Claude.

package com.swent.polyloop.model.handover

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeHandoverRepositoryTest {

  private val phase = HandoverPhase.PICKUP

  @Test
  fun theCodeIsShownAsItIs() = runTest {
    assertEquals("secret-code", FakeHandoverRepository().getCodeToShow("r1", phase).getOrThrow())
  }

  @Test
  fun theFingerprintMatchesTheCodeShown() = runTest {
    val fingerprint = FakeHandoverRepository().getCodeFingerprint("r1", phase).getOrThrow()!!
    assertTrue(HandoverCode.matches("secret-code", fingerprint))
  }

  @Test
  fun withoutACodeBothReadsGiveNull() = runTest {
    val repository = FakeHandoverRepository(code = null)
    assertNull(repository.getCodeToShow("r1", phase).getOrThrow())
    assertNull(repository.getCodeFingerprint("r1", phase).getOrThrow())
  }

  @Test
  fun readsFailWithTheCodeFailure() = runTest {
    val error = IOException("offline")
    val repository = FakeHandoverRepository(codeFailure = error)
    assertEquals(error, repository.getCodeToShow("r1", phase).exceptionOrNull())
    assertEquals(error, repository.getCodeFingerprint("r1", phase).exceptionOrNull())
  }

  @Test
  fun readsFailWhenNobodyIsSignedIn() = runTest {
    val repository = FakeHandoverRepository(currentUid = null)
    assertTrue(repository.getCodeToShow("r1", phase).exceptionOrNull() is IllegalStateException)
    assertTrue(
        repository.getCodeFingerprint("r1", phase).exceptionOrNull() is IllegalStateException
    )
  }

  @Test
  fun theRightCodeIsConfirmedAndRecorded() = runTest {
    val repository = FakeHandoverRepository()
    assertTrue(repository.confirmScan("r1", phase, "secret-code").isSuccess)
    assertEquals(listOf("secret-code"), repository.scannedCodes)
  }

  @Test
  fun aWrongCodeFailsButIsRecorded() = runTest {
    val repository = FakeHandoverRepository()
    val result = repository.confirmScan("r1", phase, "other")
    assertTrue(result.exceptionOrNull() is WrongCodeException)
    assertEquals(listOf("other"), repository.scannedCodes)
  }

  @Test
  fun anyCodeIsWrongWhenThereIsNone() = runTest {
    val result = FakeHandoverRepository(code = null).confirmScan("r1", phase, "secret-code")
    assertTrue(result.exceptionOrNull() is WrongCodeException)
  }

  @Test
  fun theRightCodeFailsWithTheScanFailure() = runTest {
    val repository = FakeHandoverRepository(scanFailure = RentalChangedException())
    val result = repository.confirmScan("r1", phase, "secret-code")
    assertTrue(result.exceptionOrNull() is RentalChangedException)
  }

  @Test
  fun aWrongCodeIsReportedBeforeTheScanFailure() = runTest {
    val repository = FakeHandoverRepository(scanFailure = IOException("offline"))
    val result = repository.confirmScan("r1", phase, "other")
    assertTrue(result.exceptionOrNull() is WrongCodeException)
  }

  @Test
  fun scanningFailsWhenNobodyIsSignedIn() = runTest {
    val result = FakeHandoverRepository(currentUid = null).confirmScan("r1", phase, "secret-code")
    assertTrue(result.exceptionOrNull() is IllegalStateException)
  }

  @Test
  fun theScanWaitsForTheGate() = runTest {
    val gate = CompletableDeferred<Unit>()
    val repository = FakeHandoverRepository(gate = gate)
    var done = false
    launch {
      repository.confirmScan("r1", phase, "secret-code")
      done = true
    }
    runCurrent()
    assertFalse(done)
    assertEquals(1, repository.scannedCodes.size) // recorded as soon as it is sent

    gate.complete(Unit)
    runCurrent()
    assertTrue(done)
  }

  @Test
  fun confirmingAReturnRecordsItsRental() = runTest {
    val repository = FakeHandoverRepository()
    assertTrue(repository.confirmReturnOk("r1").isSuccess)
    assertEquals(listOf("r1"), repository.confirmedReturns)
  }

  @Test
  fun confirmingAReturnFailsWithTheConfirmFailureOrWhenNobodyIsSignedIn() = runTest {
    val refused = FakeHandoverRepository(confirmFailure = IOException("offline"))
    assertTrue(refused.confirmReturnOk("r1").exceptionOrNull() is IOException)
    val signedOut = FakeHandoverRepository(currentUid = null)
    assertTrue(signedOut.confirmReturnOk("r1").exceptionOrNull() is IllegalStateException)
    assertEquals(emptyList<String>(), refused.confirmedReturns + signedOut.confirmedReturns)
  }
}
