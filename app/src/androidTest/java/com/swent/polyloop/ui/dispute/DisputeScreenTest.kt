// Made with Claude.

package com.swent.polyloop.ui.dispute

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onChildAt
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
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
import com.swent.polyloop.ui.components.formatDay
import com.swent.polyloop.ui.theme.PolyLoopTheme
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DisputeScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  // Noon UTC, so the day is the same in every time zone the emulator may use.
  private val at = Instant.parse("2026-10-02T12:00:00Z")
  private val today = LocalDate.of(2026, 10, 31)
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
                    onStartEditing = { calls += "startEditing" },
                    onDraft = { calls += "draft $it" },
                    onCancelEditing = { calls += "cancel" },
                    onSendDraft = { calls += "send" },
                    onAccept = { calls += "accept" },
                    onAskEscalate = { calls += "askEscalate" },
                    onDismissEscalate = { calls += "dismissEscalate" },
                    onEscalate = { calls += "escalate" },
                    onRetry = { calls += "retry" },
                ),
            today = today,
        )
      }
    }
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag)

  private fun textIn(tag: String, text: String, substring: Boolean = false) =
      composeTestRule.onNode(hasText(text, substring) and hasAnyAncestor(hasTestTag(tag)))

  // ---------- proposal card ----------

  @Test
  fun theirProposalShowsTheSplitWhatIGetBackCounterAndAccept() {
    setContent(open)

    node(C.Tag.dispute_role_pill)
        .assertTextEquals(context.getString(R.string.dispute_role_borrower))
    node(C.Tag.dispute_item_deposit).assertTextEquals("Bike pump · deposit 60 PP")
    node(C.Tag.dispute_proposal_title).assertTextEquals("Ana proposes")
    textIn(C.Tag.dispute_proposal_card, "70").assertIsDisplayed()
    textIn(C.Tag.dispute_proposal_card, "30").assertIsDisplayed()
    textIn(C.Tag.dispute_get_back, "18 PP").assertIsDisplayed()
    node(C.Tag.dispute_counter_button).assertTextEquals("Counter").performClick()
    node(C.Tag.dispute_accept_button).performClick()
    assertEquals(listOf("startEditing", "accept"), calls)
  }

  @Test
  fun myProposalShowsUpdateAndWhoItWaitsForWithoutAccept() {
    setContent(open.copy(history = listOf(mine)))

    node(C.Tag.dispute_proposal_title).assertTextEquals("You propose")
    node(C.Tag.dispute_counter_button).assertTextEquals("Update")
    node(C.Tag.dispute_waiting).assertTextEquals("Waiting for Ana")
    node(C.Tag.dispute_accept_button).assertDoesNotExist()
  }

  @Test
  fun noProposalOffersToProposeASplitWithoutHistory() {
    setContent(open.copy(history = emptyList()))

    node(C.Tag.dispute_proposal_title).assertTextEquals("No proposal yet")
    node(C.Tag.dispute_counter_button).assertTextEquals("Propose a split")
    node(C.Tag.dispute_history_card).assertDoesNotExist()
  }

  @Test
  fun settledDisputeShowsThePillTheAgreedSplitAndNoAction() {
    setContent(open.copy(isSettled = true))

    node(C.Tag.dispute_settled_pill).assertIsDisplayed()
    node(C.Tag.dispute_proposal_title).assertTextEquals("Agreed split")
    textIn(C.Tag.dispute_get_back, "You get back").assertIsDisplayed()
    node(C.Tag.dispute_counter_button).assertDoesNotExist()
    node(C.Tag.dispute_accept_button).assertDoesNotExist()
    node(C.Tag.dispute_escalate_button).assertDoesNotExist()
    node(C.Tag.dispute_settled_footer).performScrollTo().assertIsDisplayed()
  }

  @Test
  fun historyIsNewestFirstWithAuthorDayAndSplit() {
    setContent(open)

    // The day follows the phone's language: "2 Oct" in English, "2 oct." in French.
    val rows = composeTestRule.onAllNodesWithTag(C.Tag.dispute_history_row)
    rows[0].onChildAt(0).assertTextEquals("Ana · ${formatDay(LocalDate.of(2026, 10, 2))}")
    rows[0].onChildAt(1).assertTextEquals("70% / 30%")
    rows[1].onChildAt(0).assertTextEquals("You · ${formatDay(LocalDate.of(2026, 10, 1))}")
  }

  // ---------- editing the split ----------

  @Test
  fun editorShowsTheDraftAndPassesOnTypingTappingSendAndCancel() {
    setContent(open.copy(draftLenderPercent = 25))

    node(C.Tag.dispute_proposal_title).assertTextEquals("Your proposal")
    node(C.Tag.dispute_split_lender_field)
        .assertTextEquals("25")
        .assert(hasContentDescription(context.getString(R.string.dispute_lender_share_description)))
    node(C.Tag.dispute_split_borrower_field)
        .assertTextEquals("75")
        .assert(
            hasContentDescription(context.getString(R.string.dispute_borrower_share_description))
        )
    textIn(C.Tag.dispute_get_back, "45 PP").assertIsDisplayed()
    node(C.Tag.dispute_split_borrower_field).performTextClearance()
    node(C.Tag.dispute_split_borrower_field).performTextInput("40")
    node(C.Tag.dispute_split_slider).performTouchInput { click(center) }
    node(C.Tag.dispute_split_send).performClick()
    node(C.Tag.dispute_split_cancel).performClick()

    assertEquals(listOf("draft 60", "draft 50", "send", "cancel"), calls)
  }

  @Test
  fun emptiedFieldDisablesSendUntilTheUserLeavesIt() {
    setContent(open.copy(draftLenderPercent = 60))
    node(C.Tag.dispute_split_send).assertIsEnabled()

    node(C.Tag.dispute_split_lender_field).performTextClearance()

    node(C.Tag.dispute_split_send).assertIsNotEnabled()
    node(C.Tag.dispute_split_lender_field).assertTextEquals("")
  }

  // ---------- stages and escalation ----------

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
    node(C.Tag.dispute_proposal_card).assertDoesNotExist()
    node(C.Tag.dispute_escalation).assertDoesNotExist()
    node(C.Tag.dispute_escalate_button).assertDoesNotExist()
  }

  // ---------- offline and errors ----------

  @Test
  fun offlineShowsTheBannerAndDisablesTheActions() {
    setContent(open.copy(isOnline = false))

    node(C.Tag.offline_banner).assertIsDisplayed()
    node(C.Tag.dispute_accept_button).assertIsNotEnabled()
    node(C.Tag.dispute_counter_button).assertIsNotEnabled()
    node(C.Tag.dispute_escalate_button).performScrollTo().assertIsNotEnabled()
  }

  @Test
  fun aProposalThatChangedIsToldInTheProposalCard() {
    setContent(open.copy(actionError = DisputeActionError.PROPOSAL_CHANGED))

    textIn(C.Tag.dispute_proposal_card, context.getString(R.string.dispute_proposal_changed))
        .assertIsDisplayed()
    node(C.Tag.dispute_escalate_error).assertDoesNotExist()
  }

  @Test
  fun loadingShowsOnlyTheSpinner() {
    setContent(DisputeUiState(isLoading = true))

    node(C.Tag.dispute_loading).assertIsDisplayed()
    node(C.Tag.dispute_proposal_card).assertDoesNotExist()
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
    node(C.Tag.dispute_proposal_card).assertIsDisplayed()
    node(C.Tag.dispute_retry_button).performClick()
    assertEquals(listOf("retry"), calls)
  }

  // ---------- what the other person did ----------

  @Test
  fun theirNewOfferIsToldWithItsSplitWhileEditing() {
    setContent(open.copy(offerUpdated = true, draftLenderPercent = 50))

    node(C.Tag.dispute_proposal_notice)
        .assertTextEquals("Ana just changed their offer to 70% / 30%.")
  }

  @Test
  fun theirAcceptanceIsToldInTheCard() {
    setContent(open.copy(history = listOf(mine), isSettled = true, acceptedByOther = true))

    node(C.Tag.dispute_proposal_notice).assertTextEquals("Ana accepted your proposal.")
  }

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
  fun borrowerCountersRetriesAfterAFailureSeesTheNewOfferAndAcceptsIt() {
    val disputes = setScreen()
    fun counterWithHalf() {
      node(C.Tag.dispute_counter_button).performClick()
      node(C.Tag.dispute_split_lender_field).performTextClearance()
      node(C.Tag.dispute_split_lender_field).performTextInput("50")
      node(C.Tag.dispute_split_send).performClick()
    }

    disputes.failure = IOException("offline")
    val gate = CompletableDeferred<Unit>()
    disputes.gate = gate
    counterWithHalf()
    // Cancel waits for the answer, so a failure is never left on a closed editor.
    node(C.Tag.dispute_split_cancel).assertIsNotEnabled()
    gate.complete(Unit)
    node(C.Tag.dispute_proposal_error)
        .assertTextEquals(context.getString(R.string.dispute_propose_failed))
    // The editor stays open on the draft, so the first tap on Send never lands on Accept.
    node(C.Tag.dispute_split_lender_field).assertTextEquals("50")

    disputes.failure = null
    node(C.Tag.dispute_split_send).performClick()
    node(C.Tag.dispute_proposal_error).assertDoesNotExist()
    node(C.Tag.dispute_waiting).assertTextEquals("Waiting for Ana")

    runBlocking { disputes.propose("pump", "ana", 60) }
    node(C.Tag.dispute_proposal_notice).assertTextEquals("Ana just updated their offer.")
    node(C.Tag.dispute_accept_button).performClick()

    node(C.Tag.dispute_settled_pill).assertIsDisplayed()
    node(C.Tag.dispute_proposal_notice).assertDoesNotExist()
    textIn(C.Tag.dispute_proposal_card, "60").assertIsDisplayed()
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
