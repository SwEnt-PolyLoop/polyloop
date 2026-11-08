// Made with Claude.

package com.swent.polyloop.ui.dispute

import com.swent.polyloop.model.dispute.DisputeStage
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DisputeUiStateTest {

  private val at = Instant.parse("2026-10-02T10:00:00Z")
  private val theirs = ProposalRow(byMe = false, lenderPercent = 70, createdAt = at)
  private val open = DisputeUiState(deposit = 60, history = listOf(theirs), isLoading = false)

  @Test
  fun borrowerGetsBackTheirShareOfTheDeposit() {
    assertEquals(18, open.youGetBack)
  }

  @Test
  fun lenderGetsTheRestOfTheDeposit() {
    assertEquals(42, open.copy(isLender = true).youGetBack)
  }

  @Test
  fun borrowerShareIsRoundedDownAndTheLenderGetsTheRest() {
    // Half of 33 PP is 16.5 PP.
    assertEquals(16, splitDeposit(deposit = 33, lenderPercent = 50, toLender = false))
    assertEquals(17, splitDeposit(deposit = 33, lenderPercent = 50, toLender = true))
  }

  @Test
  fun draftShareFollowsTheDraftSplit() {
    assertNull(open.draftGetBack)
    assertEquals(24, open.copy(draftLenderPercent = 60).draftGetBack)
  }

  @Test
  fun nothingToGetBackWithoutAProposal() {
    assertNull(open.copy(history = emptyList()).youGetBack)
  }

  @Test
  fun userCanAcceptOnlyTheOtherPersonsProposal() {
    assertTrue(open.canAccept)
    assertFalse(open.copy(history = listOf(theirs.copy(byMe = true))).canAccept)
    assertFalse(open.copy(history = emptyList()).canAccept)
  }

  @Test
  fun settledDisputeAllowsNoAction() {
    val settled = open.copy(isSettled = true)

    assertFalse(settled.canPropose)
    assertFalse(settled.canAccept)
    assertFalse(settled.canEscalate)
  }

  @Test
  fun onlyDirectSettlementCanBeEscalated() {
    val llm = open.copy(stage = DisputeStage.LLM_PROPOSAL)

    assertTrue(open.canEscalate)
    assertFalse(llm.canPropose)
    assertFalse(llm.canEscalate)
    assertFalse(open.copy(stage = DisputeStage.COURT).canEscalate)
  }

  @Test
  fun loadingOrAFailedLoadAllowsNoAction() {
    for (state in listOf(open.copy(isLoading = true), open.copy(loadFailed = true))) {
      assertFalse(state.canPropose)
      assertFalse(state.canEscalate)
    }
  }

  @Test
  fun offlineOrSendingAllowsNoAction() {
    for (state in listOf(open.copy(isOnline = false), open.copy(isSending = true))) {
      assertFalse(state.canPropose)
      assertFalse(state.canEscalate)
    }
  }
}
