// Made with Claude.

package com.swent.polyloop.model.dispute

import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FakeDisputeRepositoryTest {

  private val now = Instant.parse("2026-10-02T10:00:00Z")
  private val proposal = Proposal("lender", 70, now.minusSeconds(3600))
  private val open =
      Dispute("rental", DisputeStage.SETTLE, listOf(proposal), now.plusSeconds(86_400))

  private fun repository(dispute: Dispute = open) =
      FakeDisputeRepository(listOf(dispute), clock = { now })

  private suspend fun FakeDisputeRepository.current() = observeDispute("rental").first()!!

  @Test
  fun observeDisputeGivesNullForARentalWithoutDispute() = runTest {
    assertNull(repository().observeDispute("other").first())
  }

  @Test
  fun proposeAddsTheLatestProposal() = runTest {
    val repository = repository()

    assertTrue(repository.propose("rental", "borrower", 40).isSuccess)

    assertEquals(listOf(proposal, Proposal("borrower", 40, now)), repository.current().proposals)
  }

  @Test
  fun acceptSettlesTheDispute() = runTest {
    val repository = repository()

    assertTrue(repository.accept("rental", "borrower").isSuccess)

    assertEquals(now, repository.current().agreedAt)
  }

  @Test
  fun acceptFailsOnTheUsersOwnProposal() = runTest {
    val repository = repository()

    assertTrue(repository.accept("rental", "lender").isFailure)
    assertNull(repository.current().agreedAt)
  }

  @Test
  fun acceptFailsWithoutAProposal() = runTest {
    assertTrue(repository(open.copy(proposals = emptyList())).accept("rental", "x").isFailure)
  }

  @Test
  fun proposeFailsOnceSettled() = runTest {
    assertTrue(repository(open.copy(agreedAt = now)).propose("rental", "lender", 50).isFailure)
  }

  @Test
  fun escalateMovesOnRestartsTheDeadlineAndRecordsWhoEscalated() = runTest {
    val repository = repository()

    assertTrue(repository.escalate("rental", "lender").isSuccess)

    val dispute = repository.current()
    assertEquals(DisputeStage.LLM_PROPOSAL, dispute.stage)
    assertEquals(now + FakeDisputeRepository.ESCALATION_PERIOD, dispute.escalatesAt)
    assertEquals("lender", dispute.escalatedBy)
  }

  @Test
  fun escalateFailsInCourt() = runTest {
    assertTrue(
        repository(open.copy(stage = DisputeStage.COURT)).escalate("rental", "lender").isFailure
    )
  }

  @Test
  fun callsFailForAMissingDispute() = runTest {
    assertTrue(repository().propose("other", "lender", 50).isFailure)
  }

  @Test
  fun failureMakesCallsAndObserversFail() = runTest {
    val repository = repository()
    repository.failure = IOException("offline")

    assertTrue(repository.escalate("rental", "lender").isFailure)
    try {
      repository.observeDispute("rental").first()
      fail("The observer should throw the failure")
    } catch (e: IOException) {
      assertEquals("offline", e.message)
    }
  }
}
