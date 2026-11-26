// Made with Claude.

package com.swent.polyloop.model.handover

import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker.Result
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.swent.polyloop.model.RepositoryProvider
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Runs [HandoverScanWorker] against a [FakeHandoverRepository]. */
@RunWith(RobolectricTestRunner::class)
class HandoverScanWorkerTest {

  private val repository = FakeHandoverRepository(code = CODE)
  private val scannedAt = Instant.parse("2026-12-01T09:00:00Z")

  @Before
  fun setUp() {
    RepositoryProvider.handoverRepository = repository
  }

  @After
  fun tearDown() {
    RepositoryProvider.reset()
  }

  private fun runWorker(code: String? = CODE): Result = runBlocking {
    val input =
        workDataOf(
            HandoverScanWorker.RENTAL_ID to "r1",
            HandoverScanWorker.PHASE to HandoverPhase.PICKUP.name,
            HandoverScanWorker.CODE to code,
            HandoverScanWorker.SCANNED_AT to scannedAt.toEpochMilli(),
        )
    TestListenableWorkerBuilder<HandoverScanWorker>(ApplicationProvider.getApplicationContext())
        .setInputData(input)
        .build()
        .doWork()
  }

  private fun refused(reason: String) =
      Result.failure(workDataOf(HandoverScanWorker.REFUSAL to reason))

  @Test
  fun aQueuedScanIsSentWithTheTimeItWasMade() {
    assertEquals(Result.success(), runWorker())
    assertEquals(listOf(scannedAt), repository.scanTimes)
  }

  @Test
  fun aScanThatCouldNotBeSentIsRetried() {
    repository.scanFailure = IOException("no answer")

    assertEquals(Result.retry(), runWorker())
  }

  @Test
  fun aScanTheServerRefusesStopsWithItsReason() {
    assertEquals(refused("WRONG_CODE"), runWorker(code = "not-the-code"))
    repository.scanFailure = RentalChangedException()
    assertEquals(refused("RENTAL_CHANGED"), runWorker())
    repository.scanFailure = ScanRefusedException()
    assertEquals(refused("REFUSED"), runWorker())
  }

  @Test
  fun aJobWithNothingToSendStopsWithoutCallingTheServer() {
    assertEquals(refused("REFUSED"), runWorker(code = null))
    assertEquals(emptyList<String>(), repository.scannedCodes)
  }

  private companion object {
    const val CODE = "secret-code"
  }
}
