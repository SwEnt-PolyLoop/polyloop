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
    assertNull(state.errorMsg)
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

    assertEquals("", viewModel().uiState.value.errorMsg)
  }

  @Test
  fun errorWhenTheRentalIsMissingOrNotTheUsers() {
    assertEquals("", viewModel(rentalId = "other").uiState.value.errorMsg)

    auth.signedInUser = AuthUser(uid = "eve", email = "eve@epfl.ch", name = "Eve")

    assertEquals("", viewModel().uiState.value.errorMsg)
  }

  @Test
  fun errorWhenTheRentalHasNoDispute() {
    disputes = FakeDisputeRepository()

    assertEquals("", viewModel().uiState.value.errorMsg)
  }

  @Test
  fun errorShowsTheCauseWhenTheRentalCannotBeRead() {
    rentals.failure = IOException("offline")

    val state = viewModel().uiState.value

    assertFalse(state.isLoading)
    assertEquals("offline", state.errorMsg)
  }

  @Test
  fun errorShowsTheCauseWhenTheDisputeCannotBeRead() {
    disputes.failure = IOException("denied")

    assertEquals("denied", viewModel().uiState.value.errorMsg)
  }

  @Test
  fun retryLoadsTheDisputeAfterAnError() {
    rentals.failure = IOException("offline")
    val vm = viewModel()

    rentals.failure = null
    vm.retry()

    assertNull(vm.uiState.value.errorMsg)
    assertNotNull(vm.uiState.value.latestProposal)
  }

  // ---------- actions ----------

  @Test
  fun counterSendsMyProposal() {
    val vm = viewModel()

    vm.counter(55)

    assertEquals(ProposalRow(true, 55, now), vm.uiState.value.latestProposal)
    assertFalse(vm.uiState.value.canAccept)
  }

  @Test
  fun counterOutsideZeroToAHundredSendsNothing() {
    val vm = viewModel()

    vm.counter(101)
    vm.counter(-1)

    assertEquals(2, disputes.disputes.single().proposals.size)
    assertFalse(vm.uiState.value.actionFailed)
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
    assertFalse(vm.uiState.value.actionFailed)
  }

  @Test
  fun escalateMovesToTheLlmStageAndRestartsTheCountdown() {
    val vm = viewModel()

    vm.escalate()

    assertEquals(DisputeStage.LLM_PROPOSAL, vm.uiState.value.stage)
    assertEquals(30, vm.uiState.value.daysUntilEscalation)
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

    vm.counter(50)
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
    vm.counter(50)
    assertTrue(vm.uiState.value.actionFailed)
    assertEquals(70, vm.uiState.value.latestProposal?.lenderPercent)

    disputes.failure = null
    vm.counter(50)
    assertFalse(vm.uiState.value.actionFailed)
    assertEquals(ProposalRow(true, 50, now), vm.uiState.value.latestProposal)

    runBlocking { disputes.accept("pump", "ana") }

    assertTrue(vm.uiState.value.isSettled)
  }
}
