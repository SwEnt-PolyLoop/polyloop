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

    assertTrue(repository.propose("rental", "borrower", 40).isSuccess)

    assertEquals(listOf(proposal, Proposal("borrower", 40, now)), repository.current().proposals)
  }

  @Test
  fun acceptSettlesTheDispute() = runTest {
    val repository = repository()

    assertTrue(repository.accept("rental", "borrower", 70).isSuccess)

    assertEquals(now, repository.current().agreedAt)
  }

  @Test
  fun acceptFailsOnTheUsersOwnProposal() = runTest {
    val repository = repository()

    assertTrue(repository.accept("rental", "lender", 70).isFailure)
    assertNull(repository.current().agreedAt)
  }

  @Test
  fun acceptFailsAndSettlesNothingWhenTheProposalChanged() = runTest {
    val repository = repository()

    val result = repository.accept("rental", "borrower", 60)

    assertTrue(result.exceptionOrNull() is ProposalChangedException)
    assertNull(repository.current().agreedAt)
  }

  @Test
  fun acceptFailsWithoutAProposal() = runTest {
    assertTrue(repository(open.copy(proposals = emptyList())).accept("rental", "x", 70).isFailure)
  }

  @Test
  fun proposeFailsOnceSettled() = runTest {
    assertTrue(repository(open.copy(agreedAt = now)).propose("rental", "lender", 50).isFailure)
  }

  @Test
  fun callsFailForAMissingDispute() = runTest {
    assertTrue(repository().propose("other", "lender", 50).isFailure)
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
