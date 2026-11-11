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

  /** The user each call acts as; null when nobody is signed in. */
  private var uid: String? = "borrower"

  private fun repository(dispute: Dispute = open) =
      FakeDisputeRepository(listOf(dispute), currentUid = { uid }, clock = { now })

  private suspend fun FakeDisputeRepository.current() = observeDispute("rental").first()!!

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
  fun proposeAddsTheLatestProposal() = runTest {
    val repository = repository()

    assertTrue(repository.propose("rental", 40).isSuccess)

    assertEquals(listOf(proposal, Proposal("borrower", 40, now)), repository.current().proposals)
  }

  @Test
  fun acceptSettlesTheDispute() = runTest {
    val repository = repository()

    assertTrue(repository.accept("rental", 70).isSuccess)

    assertEquals(now, repository.current().agreedAt)
  }

  @Test
  fun acceptFailsOnTheUsersOwnProposal() = runTest {
    val repository = repository()
    uid = "lender"

    assertTrue(repository.accept("rental", 70).isFailure)
    assertNull(repository.current().agreedAt)
  }

  @Test
  fun acceptFailsAndSettlesNothingWhenTheProposalChanged() = runTest {
    val repository = repository()

    val result = repository.accept("rental", 60)

    assertTrue(result.exceptionOrNull() is ProposalChangedException)
    assertNull(repository.current().agreedAt)
  }

  @Test
  fun acceptingASplitTheUserReplacedWithTheirOwnIsAProposalChange() = runTest {
    val repository = repository()
    repository.propose("rental", 60)

    val result = repository.accept("rental", 70)

    assertTrue(result.exceptionOrNull() is ProposalChangedException)
    assertNull(repository.current().agreedAt)
  }

  @Test
  fun acceptFailsWithoutAProposal() = runTest {
    assertTrue(repository(open.copy(proposals = emptyList())).accept("rental", 70).isFailure)
  }

  @Test
  fun proposeFailsOnTheSplitAlreadyOnTheTable() = runTest {
    val repository = repository()

    assertTrue(repository.propose("rental", 70).isFailure)
    assertEquals(listOf(proposal), repository.current().proposals)
  }

  @Test
  fun proposeOnceSettledIsAStageChange() = runTest {
    val result = repository(open.copy(agreedAt = now)).propose("rental", 50)

    assertTrue(result.exceptionOrNull() is StageChangedException)
  }

  @Test
  fun aCallPastEscalatesAtMovesTheDisputeOnByItselfAndIsAStageChange() = runTest {
    val repository = repository(open.copy(escalatesAt = now))

    val result = repository.accept("rental", 70)

    assertTrue(result.exceptionOrNull() is StageChangedException)
    val dispute = repository.current()
    assertEquals(DisputeStage.LLM_PROPOSAL, dispute.stage)
    assertNull(dispute.escalatedBy)
    assertNull(dispute.agreedAt)
  }

  @Test
  fun callsFailWhenNobodyIsSignedIn() = runTest {
    val repository = repository()
    uid = null

    assertTrue(repository.propose("rental", 40).isFailure)
    assertEquals(listOf(proposal), repository.current().proposals)
  }

  @Test
  fun escalateMovesOnToTheLlmProposalAndRecordsWhoEscalated() = runTest {
    val repository = repository()
    uid = "lender"

    assertTrue(repository.escalate("rental").isSuccess)

    val dispute = repository.current()
    assertEquals(DisputeStage.LLM_PROPOSAL, dispute.stage)
    assertEquals("lender", dispute.escalatedBy)
  }

  @Test
  fun escalateOutsideDirectSettlementIsAStageChangeAndChangesNothing() = runTest {
    val repository = repository(open.copy(stage = DisputeStage.LLM_PROPOSAL))

    val result = repository.escalate("rental")

    assertTrue(result.exceptionOrNull() is StageChangedException)
    assertEquals(DisputeStage.LLM_PROPOSAL, repository.current().stage)
  }

  @Test
  fun callsFailForAMissingDispute() = runTest {
    assertTrue(repository().propose("other", 50).isFailure)
  }

  @Test
  fun failureMakesCallsAndObserversFail() = runTest {
    val repository = repository()
    repository.failure = IOException("offline")

    assertTrue(repository.escalate("rental").isFailure)
    try {
      repository.observeDispute("rental").first()
      fail("The observer should throw the failure")
    } catch (e: IOException) {
      assertEquals("offline", e.message)
    }
  }
}
