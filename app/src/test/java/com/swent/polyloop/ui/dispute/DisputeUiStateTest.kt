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

  // The same cases as disputeRules.test.ts in functions/: the Cloud Function that applies a split
  // must give the amounts the app shows.

  /** The lender's and the borrower's shares of [deposit] when the lender gets [lenderPercent]. */
  private fun shares(deposit: Int, lenderPercent: Int) =
      splitDeposit(deposit, lenderPercent, toLender = true) to
          splitDeposit(deposit, lenderPercent, toLender = false)

  @Test
  fun borrowerShareIsRoundedDownAndTheLenderGetsTheRest() {
    // Half of 33 PP is 16.5 PP.
    assertEquals(17 to 16, shares(deposit = 33, lenderPercent = 50))
    assertEquals(19 to 36, shares(deposit = 55, lenderPercent = 33))
    assertEquals(1 to 0, shares(deposit = 1, lenderPercent = 50))
  }

  @Test
  fun wholeDepositGoesToOneSideAtZeroOrHundredPercent() {
    assertEquals(0 to 60, shares(deposit = 60, lenderPercent = 0))
    assertEquals(60 to 0, shares(deposit = 60, lenderPercent = 100))
  }

  @Test
  fun nothingToShareWithoutADeposit() {
    assertEquals(0 to 0, shares(deposit = 0, lenderPercent = 30))
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
