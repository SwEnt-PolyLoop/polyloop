// Made with Claude.

package com.swent.polyloop.model.dispute

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DisputeTest {

  private val at = Instant.parse("2026-10-01T10:00:00Z")

  private fun dispute(proposals: List<Proposal>, agreedAt: Instant? = null) =
      Dispute("rental", DisputeStage.SETTLE, proposals, at.plusSeconds(3600), agreedAt)

  @Test
  fun latestProposalIsTheLastOneSent() {
    val first = Proposal("borrower", 40, at)
    val second = Proposal("lender", 70, at.plusSeconds(60))

    assertEquals(second, dispute(listOf(first, second)).latestProposal)
    assertNull(dispute(emptyList()).latestProposal)
  }

  @Test
  fun isSettledOnceAProposalIsAccepted() {
    assertFalse(dispute(emptyList()).isSettled)
    assertTrue(dispute(emptyList(), agreedAt = at).isSettled)
  }

  @Test(expected = IllegalArgumentException::class)
  fun proposalAboveAHundredPercentIsRejected() {
    Proposal("lender", 101, at)
  }

  @Test(expected = IllegalArgumentException::class)
  fun proposalBelowZeroPercentIsRejected() {
    Proposal("lender", -1, at)
  }
}
