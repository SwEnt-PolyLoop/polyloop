// Made with Claude.

package com.swent.polyloop.ui.dispute

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
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DisputeViewModelTest {

  private val now = Instant.parse("2026-10-03T10:00:00Z")
  private val me = AuthUser(uid = "me", email = "me@epfl.ch", name = "Me")
  private val ana = AuthUser(uid = "ana", email = "ana@epfl.ch", name = "Ana")

  // I borrowed Ana's bike pump; I proposed 40/60, she countered 70/30.
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
          createdAt = now.minus(Duration.ofDays(20)),
      )
  private val mine = Proposal("me", 40, now.minus(Duration.ofDays(2)))
  private val hers = Proposal("ana", 70, now.minus(Duration.ofDays(1)))
  private val open =
      Dispute(
          rentalId = "pump",
          stage = DisputeStage.SETTLE,
          proposals = listOf(mine, hers),
          escalatesAt = now + Duration.ofDays(27).plusHours(12),
      )

  private lateinit var disputes: FakeDisputeRepository
  private lateinit var rentals: FakeRentalRepository
  private lateinit var auth: FakeAuthRepository
  private lateinit var connectivity: FakeConnectivityRepository

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    disputes = FakeDisputeRepository(listOf(open), clock = { now })
    rentals = FakeRentalRepository(listOf(pump))
    auth = FakeAuthRepository(signedInUser = me)
    connectivity = FakeConnectivityRepository()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun viewModel(rentalId: String = "pump") =
      DisputeViewModel(rentalId, disputes, rentals, auth, connectivity, clock = { now })

  // ---------- loading ----------

  @Test
  fun borrowerSeesTheItemTheLenderAndTheirRole() {
    val state = viewModel().uiState.value

    assertFalse(state.isLoading)
    assertFalse(state.loadFailed)
    assertEquals("Bike pump", state.itemTitle)
    assertEquals("Ana", state.otherPersonName)
    assertFalse(state.isLender)
    assertEquals(DisputeStage.SETTLE, state.stage)
  }

  @Test
  fun lenderSeesTheBorrowerAndTheirRole() {
    auth.signedInUser = ana

    val state = viewModel().uiState.value

    assertEquals("Me", state.otherPersonName)
    assertTrue(state.isLender)
  }

  @Test
  fun historyIsNewestFirstAndMarksMyProposals() {
    assertEquals(
        listOf(
            ProposalRow(byMe = false, lenderPercent = 70, createdAt = hers.createdAt),
            ProposalRow(byMe = true, lenderPercent = 40, createdAt = mine.createdAt),
        ),
        viewModel().uiState.value.history,
    )
  }

  @Test
  fun daysUntilEscalationAreRoundedUpAndZeroOncePassed() {
    assertEquals(28, viewModel().uiState.value.daysUntilEscalation)

    disputes = FakeDisputeRepository(listOf(open.copy(escalatesAt = now.minusSeconds(1))))

    assertEquals(0, viewModel().uiState.value.daysUntilEscalation)
  }

  @Test
  fun goingOfflineIsShown() {
    val vm = viewModel()

    connectivity.setOnline(false)

    assertFalse(vm.uiState.value.isOnline)
  }

  @Test
  fun errorWhenNobodyIsSignedIn() {
    auth.signedInUser = null

    assertTrue(viewModel().uiState.value.loadFailed)
  }

  @Test
  fun errorWhenTheRentalIsMissingOrNotTheUsers() {
    assertTrue(viewModel(rentalId = "other").uiState.value.loadFailed)

    auth.signedInUser = AuthUser(uid = "eve", email = "eve@epfl.ch", name = "Eve")

    assertTrue(viewModel().uiState.value.loadFailed)
  }

  @Test
  fun errorWhenTheRentalHasNoDispute() {
    disputes = FakeDisputeRepository()

    assertTrue(viewModel().uiState.value.loadFailed)
  }

  @Test
  fun errorWhenTheRentalCannotBeRead() {
    rentals.failure = IOException("offline")

    val state = viewModel().uiState.value

    assertFalse(state.isLoading)
    assertTrue(state.loadFailed)
  }

  @Test
  fun errorWhenTheDisputeCannotBeRead() {
    disputes.failure = IOException("denied")

    assertTrue(viewModel().uiState.value.loadFailed)
  }

  @Test
  fun retryLoadsTheDisputeAfterAnError() {
    rentals.failure = IOException("offline")
    val vm = viewModel()

    rentals.failure = null
    vm.retry()

    assertFalse(vm.uiState.value.loadFailed)
    assertNotNull(vm.uiState.value.latestProposal)
  }

  @Test
  fun refreshFailureKeepsTheDisputeShown() {
    val vm = viewModel()

    disputes.failure = IOException("offline")
    disputes.put(open.copy(proposals = listOf(mine)))

    val state = vm.uiState.value
    assertTrue(state.refreshFailed)
    assertFalse(state.loadFailed)
    assertEquals(70, state.latestProposal?.lenderPercent)
  }

  @Test
  fun retryAfterARefreshFailureShowsTheNewDispute() {
    val vm = viewModel()
    disputes.failure = IOException("offline")
    disputes.put(open.copy(proposals = listOf(mine)))

    disputes.failure = null
    vm.retry()

    val state = vm.uiState.value
    assertFalse(state.refreshFailed)
    assertEquals(40, state.latestProposal?.lenderPercent)
  }

  // ---------- actions ----------

  /** Opens the editor of [vm], moves the draft to [lenderPercent] and sends it. */
  private fun propose(vm: DisputeViewModel, lenderPercent: Int) {
    vm.startEditing()
    vm.setDraft(lenderPercent)
    vm.sendDraft()
  }

  @Test
  fun editingStartsFromTheProposalOnTheTable() {
    val vm = viewModel()

    vm.startEditing()

    assertEquals(70, vm.uiState.value.draftLenderPercent)
  }

  @Test
  fun sendingTheDraftSendsMyProposalAndClosesTheEditor() {
    val vm = viewModel()

    propose(vm, 55)

    assertEquals(ProposalRow(true, 55, now), vm.uiState.value.latestProposal)
    assertFalse(vm.uiState.value.isEditing)
    assertFalse(vm.uiState.value.canAccept)
  }

  @Test
  fun theSplitOnTheTableIsNotSentAgainAndCancelClosesTheEditor() {
    val vm = viewModel()

    propose(vm, 70)
    assertEquals(2, disputes.disputes.single().proposals.size)
    assertTrue(vm.uiState.value.isEditing)

    vm.cancelEditing()
    assertFalse(vm.uiState.value.isEditing)
  }

  @Test
  fun draftStaysBetweenZeroAndAHundred() {
    val vm = viewModel()
    vm.startEditing()

    vm.setDraft(101)
    assertEquals(100, vm.uiState.value.draftLenderPercent)

    vm.setDraft(-1)
    assertEquals(0, vm.uiState.value.draftLenderPercent)
  }

  @Test
  fun anAgreementReachedMeanwhileEndsTheEditing() {
    disputes = FakeDisputeRepository(listOf(open.copy(proposals = listOf(hers, mine))))
    val vm = viewModel()
    vm.startEditing()

    runBlocking { disputes.accept("pump", "ana", 40) }

    assertFalse(vm.uiState.value.isEditing)
  }

  @Test
  fun acceptSettlesTheDispute() {
    val vm = viewModel()

    vm.accept()

    assertTrue(vm.uiState.value.isSettled)
    assertFalse(vm.uiState.value.canEscalate)
  }

  @Test
  fun acceptDoesNothingOnMyOwnProposal() {
    disputes = FakeDisputeRepository(listOf(open.copy(proposals = listOf(hers, mine))))
    val vm = viewModel()

    vm.accept()

    assertFalse(vm.uiState.value.isSettled)
    assertNull(vm.uiState.value.actionError)
  }

  @Test
  fun acceptOfAProposalThatChangedSettlesNothingAndSaysSo() {
    val vm = viewModel()
    // Ana's update reaches the server before the screen.
    disputes.gate = CompletableDeferred()
    vm.accept()
    disputes.put(open.copy(proposals = listOf(mine, hers, Proposal("ana", 90, now))))
    disputes.gate?.complete(Unit)

    assertFalse(vm.uiState.value.isSettled)
    assertEquals(DisputeActionError.PROPOSAL_CHANGED, vm.uiState.value.actionError)
  }

  @Test
  fun failedAcceptAndEscalateNameTheirAction() {
    val vm = viewModel()
    disputes.failure = IOException("offline")

    vm.accept()
    assertEquals(DisputeActionError.ACCEPT, vm.uiState.value.actionError)

    vm.escalate()
    assertEquals(DisputeActionError.ESCALATE, vm.uiState.value.actionError)
  }

  @Test
  fun escalateMovesToTheLlmStageAndRestartsTheCountdown() {
    val vm = viewModel()

    vm.escalate()

    assertEquals(DisputeStage.LLM_PROPOSAL, vm.uiState.value.stage)
    assertEquals(30, vm.uiState.value.daysUntilEscalation)
  }

  @Test
  fun escalatingAsksForAConfirmationNamingTheNextStage() {
    val vm = viewModel()

    vm.askEscalate()
    assertTrue(vm.uiState.value.isConfirmingEscalation)
    assertEquals(DisputeStage.LLM_PROPOSAL, vm.uiState.value.nextStage)

    vm.dismissEscalate()
    assertFalse(vm.uiState.value.isConfirmingEscalation)
    assertEquals(DisputeStage.SETTLE, vm.uiState.value.stage)
  }

  @Test
  fun confirmationClosesWhenTheDisputeMovesOnMeanwhile() {
    val vm = viewModel()
    vm.askEscalate()

    disputes.put(open.copy(stage = DisputeStage.LLM_PROPOSAL))

    assertFalse(vm.uiState.value.isConfirmingEscalation)
  }

  @Test
  fun escalateFromTheLlmStageMovesToCourt() {
    val vm = viewModel()

    vm.escalate()
    vm.escalate()

    assertEquals(DisputeStage.COURT, vm.uiState.value.stage)
    assertFalse(vm.uiState.value.canEscalate)
  }

  @Test
  fun actionsDoNothingOffline() {
    connectivity.setOnline(false)
    val vm = viewModel()

    propose(vm, 50)
    vm.accept()
    vm.escalate()

    assertEquals(open, disputes.disputes.single())
  }

  @Test
  fun noSecondActionWhileOneIsSending() {
    val gate = CompletableDeferred<Unit>()
    disputes.gate = gate
    val vm = viewModel()

    vm.escalate()
    assertTrue(vm.uiState.value.isSending)
    vm.escalate()
    gate.complete(Unit)

    assertFalse(vm.uiState.value.isSending)
    assertEquals(DisputeStage.LLM_PROPOSAL, vm.uiState.value.stage)
  }

  @Test
  fun borrowerCountersRetriesAfterAFailureAndSeesTheLenderAccept() {
    val vm = viewModel()

    disputes.failure = IOException("offline")
    propose(vm, 50)
    assertEquals(DisputeActionError.PROPOSE, vm.uiState.value.actionError)
    assertEquals(70, vm.uiState.value.latestProposal?.lenderPercent)
    assertFalse(vm.uiState.value.isEditing)

    disputes.failure = null
    propose(vm, 50)
    assertNull(vm.uiState.value.actionError)
    assertEquals(ProposalRow(true, 50, now), vm.uiState.value.latestProposal)

    runBlocking { disputes.accept("pump", "ana", 50) }

    assertTrue(vm.uiState.value.isSettled)
  }

  // ---------- what the other person did ----------

  @Test
  fun theirNewOfferIsToldUntilMyNextAction() {
    val vm = viewModel()
    assertFalse(vm.uiState.value.offerUpdated)

    disputes.put(open.copy(proposals = listOf(mine, hers, Proposal("ana", 80, now))))
    assertTrue(vm.uiState.value.showsOfferUpdated)

    vm.startEditing()
    assertFalse(vm.uiState.value.offerUpdated)
  }

  @Test
  fun myOwnNewOfferIsNotTold() {
    val vm = viewModel()

    propose(vm, 50)

    assertFalse(vm.uiState.value.offerUpdated)
  }

  @Test
  fun aFailedAcceptHidesTheNewOfferItFailedOn() {
    val vm = viewModel()
    disputes.gate = CompletableDeferred()
    vm.accept()
    disputes.put(open.copy(proposals = listOf(mine, hers, Proposal("ana", 90, now))))
    disputes.gate?.complete(Unit)

    assertTrue(vm.uiState.value.offerUpdated)
    assertFalse(vm.uiState.value.showsOfferUpdated)
  }

  @Test
  fun theirAcceptanceOfMyProposalIsTold() {
    disputes = FakeDisputeRepository(listOf(open.copy(proposals = listOf(hers, mine))))
    val vm = viewModel()

    runBlocking { disputes.accept("pump", "ana", 40) }

    assertTrue(vm.uiState.value.acceptedByOther)
  }

  @Test
  fun anEscalationTellsWhetherTheOtherPersonOrNobodyDidIt() {
    val vm = viewModel()

    disputes.put(open.copy(stage = DisputeStage.LLM_PROPOSAL, escalatedBy = "ana"))
    assertEquals(EscalationNotice.BY_OTHER, vm.uiState.value.escalationNotice)

    disputes.put(open.copy(stage = DisputeStage.COURT))
    assertEquals(EscalationNotice.AUTOMATIC, vm.uiState.value.escalationNotice)
  }

  @Test
  fun myOwnEscalationAndADisputeAlreadySettledAreNotTold() {
    disputes = FakeDisputeRepository(listOf(open.copy(agreedAt = now)))
    assertFalse(viewModel().uiState.value.acceptedByOther)

    disputes = FakeDisputeRepository(listOf(open), clock = { now })
    val vm = viewModel()
    vm.escalate()

    assertNull(vm.uiState.value.escalationNotice)
  }
}
