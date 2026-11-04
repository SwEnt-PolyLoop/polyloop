// Made with Claude.

package com.swent.polyloop.ui.dispute

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DisputeUiStateTest {

  private val at = Instant.parse("2026-10-02T10:00:00Z")
  private val theirs = ProposalRow(byMe = false, lenderPercent = 70, createdAt = at)
  private val open = DisputeUiState(deposit = 60, history = listOf(theirs), isLoading = false)

  @Test
  fun borrowerGetsBackTheirShareOfTheDeposit() {
    assertEquals(180, open.youGetBackTenths)
  }

  @Test
  fun lenderGetsTheRestOfTheDeposit() {
    assertEquals(420, open.copy(isLender = true).youGetBackTenths)
  }

  @Test
  fun borrowerShareIsRoundedDownToATenthAndTheLenderGetsTheRest() {
    // 67% of 55 PP is 36.85 PP.
    assertEquals(368, shareInTenths(deposit = 55, lenderPercent = 33, toLender = false))
    assertEquals(182, shareInTenths(deposit = 55, lenderPercent = 33, toLender = true))
  }

  @Test
  fun nothingToGetBackWithoutAProposal() {
    assertNull(open.copy(history = emptyList()).youGetBackTenths)
  }
}
