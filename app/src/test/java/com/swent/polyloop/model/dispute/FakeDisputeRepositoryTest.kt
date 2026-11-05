// Made with Claude.

package com.swent.polyloop.model.dispute

import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class FakeDisputeRepositoryTest {

  private val now = Instant.parse("2026-10-02T10:00:00Z")
  private val proposal = Proposal("lender", 70, now.minusSeconds(3600))
  private val open =
      Dispute("rental", DisputeStage.SETTLE, listOf(proposal), now.plusSeconds(86_400))

  private fun repository() = FakeDisputeRepository(listOf(open))

  @Test
  fun observeDisputeGivesNullForARentalWithoutDispute() = runTest {
    assertNull(repository().observeDispute("other").first())
  }

  @Test
  fun observersSeeWhatIsPutAndRemoved() = runTest {
    val repository = repository()
    val settled = open.copy(agreedAt = now)

    repository.put(settled)
    assertEquals(settled, repository.observeDispute("rental").first())

    repository.remove("rental")
    assertNull(repository.observeDispute("rental").first())
  }

  @Test
  fun failureMakesObserversFail() = runTest {
    val repository = repository()
    repository.failure = IOException("offline")

    try {
      repository.observeDispute("rental").first()
      fail("The observer should throw the failure")
    } catch (e: IOException) {
      assertEquals("offline", e.message)
    }
  }
}
