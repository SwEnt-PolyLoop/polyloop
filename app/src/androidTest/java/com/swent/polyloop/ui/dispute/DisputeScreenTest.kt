// Made with Claude.

package com.swent.polyloop.ui.dispute

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.swent.polyloop.R
import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.auth.FakeAuthRepository
import com.swent.polyloop.model.connectivity.FakeConnectivityRepository
import com.swent.polyloop.model.dispute.Dispute
import com.swent.polyloop.model.dispute.DisputeStage
import com.swent.polyloop.model.dispute.FakeDisputeRepository
import com.swent.polyloop.model.dispute.Proposal
import com.swent.polyloop.model.rental.FakeRentalRepository
import com.swent.polyloop.model.rental.Rental
import com.swent.polyloop.model.rental.RentalStatus
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.theme.PolyLoopTheme
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DisputeScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  private val at = Instant.parse("2026-10-02T12:00:00Z")
  private val theirs = ProposalRow(byMe = false, lenderPercent = 70, createdAt = at)
  private val mine =
      ProposalRow(byMe = true, lenderPercent = 40, createdAt = at.minusSeconds(86_400))

  // I borrow Ana's bike pump with a 60 PP deposit; she proposes 70/30 after my 40/60.
  private val open =
      DisputeUiState(
          itemTitle = "Bike pump",
          otherPersonName = "Ana",
          deposit = 60,
          history = listOf(theirs, mine),
          daysUntilEscalation = 28,
          isLoading = false,
      )

  /** Every callback called, by name. */
  private val calls = mutableListOf<String>()

  private fun setContent(uiState: DisputeUiState) {
    composeTestRule.setContent {
      PolyLoopTheme {
        DisputeContent(
            uiState = uiState,
            actions =
                DisputeActions(
                    onAskEscalate = { calls += "askEscalate" },
                    onDismissEscalate = { calls += "dismissEscalate" },
                    onEscalate = { calls += "escalate" },
                    onRetry = { calls += "retry" },
                ),
        )
      }
    }
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag)

  private fun textIn(tag: String, text: String, substring: Boolean = false) =
      composeTestRule.onNode(hasText(text, substring) and hasAnyAncestor(hasTestTag(tag)))

  // ---------- header and stages ----------

  @Test
  fun headerShowsTheItemTheDepositAndMyRole() {
    setContent(open)

    node(C.Tag.dispute_role_pill)
        .assertTextEquals(context.getString(R.string.dispute_role_borrower))
    node(C.Tag.dispute_item_deposit).assertTextEquals("Bike pump · deposit 60 PP")
  }

  @Test
  fun settledDisputeShowsThePillAndTheFooterWithoutEscalation() {
    setContent(open.copy(isSettled = true))

    node(C.Tag.dispute_settled_pill).assertIsDisplayed()
    node(C.Tag.dispute_escalate_button).assertDoesNotExist()
    node(C.Tag.dispute_settled_footer).performScrollTo().assertIsDisplayed()
  }


  @Test
  fun escalateNowShowsTheDaysLeftAndAsksToConfirm() {
    setContent(open)

    node(C.Tag.dispute_escalation).performScrollTo().assertTextEquals("Auto-escalates in 28 days")
    node(C.Tag.dispute_escalate_button).performClick()
    assertEquals(listOf("askEscalate"), calls)
  }

  @Test
  fun confirmationNamesTheLlmStageAndEscalatesOrDismisses() {
    setContent(open.copy(isConfirmingEscalation = true))

    textIn(C.Tag.dispute_escalate_dialog, context.getString(R.string.dispute_escalate_text))
        .assertIsDisplayed()
    node(C.Tag.dispute_escalate_confirm).performClick()
    node(C.Tag.dispute_escalate_dismiss).performClick()
    assertEquals(listOf("escalate", "dismissEscalate"), calls)
  }

  @Test
  fun stepperSelectsOnlyTheCurrentStage() {
    setContent(open.copy(stage = DisputeStage.LLM_PROPOSAL))

    node(C.Tag.dispute_step_ + DisputeStage.SETTLE.name).assertIsNotSelected()
    node(C.Tag.dispute_step_ + DisputeStage.LLM_PROPOSAL.name).assertIsSelected()
    node(C.Tag.dispute_step_ + DisputeStage.COURT.name).assertIsNotSelected()
  }

  @Test
  fun llmStageShowsComingSoonWithoutCountdownOrEscalation() {
    setContent(open.copy(stage = DisputeStage.LLM_PROPOSAL))

    node(C.Tag.dispute_coming_soon).assertIsDisplayed()
    node(C.Tag.dispute_evidence_card).assertDoesNotExist()
    node(C.Tag.dispute_escalation).assertDoesNotExist()
    node(C.Tag.dispute_escalate_button).assertDoesNotExist()
  }

  // ---------- offline and errors ----------

  @Test
  fun offlineShowsTheBannerAndDisablesTheActions() {
    setContent(open.copy(isOnline = false))

    node(C.Tag.offline_banner).assertIsDisplayed()
    node(C.Tag.dispute_escalate_button).performScrollTo().assertIsNotEnabled()
  }

  @Test
  fun loadingShowsOnlyTheSpinner() {
    setContent(DisputeUiState(isLoading = true))

    node(C.Tag.dispute_loading).assertIsDisplayed()
    node(C.Tag.dispute_evidence_card).assertDoesNotExist()
    node(C.Tag.dispute_error).assertDoesNotExist()
  }

  @Test
  fun loadErrorShowsTheGenericTextAndRetries() {
    setContent(DisputeUiState(isLoading = false, loadFailed = true))

    node(C.Tag.dispute_error).assertTextEquals(context.getString(R.string.dispute_error_fallback))
    node(C.Tag.dispute_retry_button).performClick()
    assertEquals(listOf("retry"), calls)
  }

  @Test
  fun failedRefreshKeepsTheDisputeWithANoteAndRetry() {
    setContent(open.copy(refreshFailed = true))

    node(C.Tag.dispute_refresh_failed).assertIsDisplayed()
    node(C.Tag.dispute_evidence_card).assertIsDisplayed()
    node(C.Tag.dispute_retry_button).performClick()
    assertEquals(listOf("retry"), calls)
  }

  // ---------- what the other person did ----------

  @Test
  fun theirEscalationIsToldUnderComingSoon() {
    setContent(
        open.copy(
            stage = DisputeStage.LLM_PROPOSAL,
            escalationNotice = EscalationNotice.BY_OTHER,
        )
    )

    node(C.Tag.dispute_escalation_notice).assertTextEquals("Ana escalated the dispute.")
  }

  // ---------- whole paths ----------

  private val pump =
      Rental(
          id = "pump",
          listingId = "listing",
          listingTitle = "Bike pump",
          ownerId = "ana",
          ownerName = "Ana",
          borrowerId = "me",
          borrowerName = "Me",
          startDate = LocalDate.of(2026, 9, 20),
          endDate = LocalDate.of(2026, 9, 25),
          status = RentalStatus.DISPUTED,
          pricePerDay = 2,
          totalPrice = 12,
          pickupArea = "Ecublens",
          dropOffArea = "Ecublens",
          createdAt = at,
      )

  /** Shows the real screen for me, the borrower, with Ana's 70/30 on the table in [disputes]. */
  private fun setScreen(): FakeDisputeRepository {
    val disputes =
        FakeDisputeRepository(
            listOf(
                Dispute(
                    rentalId = "pump",
                    stage = DisputeStage.SETTLE,
                    proposals = listOf(Proposal("ana", 70, at)),
                    escalatesAt = Instant.now().plus(Duration.ofDays(10)),
                )
            )
        )
    val viewModel =
        DisputeViewModel(
            "pump",
            disputes,
            FakeRentalRepository(listOf(pump)),
            FakeAuthRepository(signedInUser = AuthUser("me", "me@epfl.ch", "Me")),
            FakeConnectivityRepository(),
        )
    composeTestRule.setContent { PolyLoopTheme { DisputeScreen(viewModel) } }
    return disputes
  }

  @Test
  fun borrowerConfirmsAnEscalationThatFailsThenRetries() {
    val disputes = setScreen()
    fun confirmEscalation() {
      node(C.Tag.dispute_escalate_button).performScrollTo().performClick()
      node(C.Tag.dispute_escalate_confirm).performClick()
    }

    disputes.failure = IOException("offline")
    confirmEscalation()
    node(C.Tag.dispute_escalate_dialog).assertDoesNotExist()
    node(C.Tag.dispute_escalate_error).performScrollTo().assertIsDisplayed()

    disputes.failure = null
    confirmEscalation()

    node(C.Tag.dispute_escalate_error).assertDoesNotExist()
    node(C.Tag.dispute_coming_soon).assertIsDisplayed()
    node(C.Tag.dispute_escalation_notice).assertDoesNotExist()
  }
}
