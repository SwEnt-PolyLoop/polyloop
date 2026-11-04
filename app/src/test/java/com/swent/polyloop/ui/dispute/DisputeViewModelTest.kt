// Made with Claude.

package com.swent.polyloop.ui.dispute

import androidx.lifecycle.ViewModel
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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

  private val dispatcher = UnconfinedTestDispatcher()
  private lateinit var disputes: FakeDisputeRepository
  private lateinit var rentals: FakeRentalRepository
  private lateinit var auth: FakeAuthRepository
  private lateinit var connectivity: FakeConnectivityRepository

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    disputes = FakeDisputeRepository(listOf(open), clock = { now })
    rentals = FakeRentalRepository(listOf(pump))
    auth = FakeAuthRepository(signedInUser = me)
    connectivity = FakeConnectivityRepository()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun viewModel(rentalId: String = "pump", clock: () -> Instant = { now }) =
      DisputeViewModel(rentalId, disputes, rentals, auth, connectivity, clock)

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
  fun depositIsAHundredPolyPointsUntilRentalsHaveOne() {
    assertEquals(100, viewModel().uiState.value.deposit)
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

  /**
   * Collects the state of [vm], as the screen does while it is shown, until the job is cancelled.
   */
  private fun show(vm: DisputeViewModel): Job =
      CoroutineScope(dispatcher).launch { vm.uiState.collect {} }

  /** Moves the clock read through [time] and the test dispatcher on by [duration]. */
  private fun elapse(duration: Duration) {
    time += duration
    dispatcher.scheduler.advanceTimeBy(duration.toMillis())
    dispatcher.scheduler.runCurrent()
  }

  private var time = now

  @Test
  fun daysUntilEscalationDropWhileTheScreenIsShown() {
    val vm = viewModel(clock = { time })
    val screen = show(vm)
    assertEquals(28, vm.uiState.value.daysUntilEscalation)

    // 27 days and 12 hours were left: 12 hours later, 27 days are.
    elapse(Duration.ofHours(12))

    assertEquals(27, vm.uiState.value.daysUntilEscalation)
    screen.cancel()
  }

  @Test
  fun countdownPausesWhileTheScreenIsHiddenAndCatchesUpWhenShown() {
    val vm = viewModel(clock = { time })

    elapse(Duration.ofHours(12))
    assertEquals(28, vm.uiState.value.daysUntilEscalation)

    val screen = show(vm)
    assertEquals(27, vm.uiState.value.daysUntilEscalation)
    screen.cancel()
  }

  @Test
  fun countdownReachesZeroWhenTheDeadlinePassesWhileTheScreenIsShown() {
    disputes = FakeDisputeRepository(listOf(open.copy(escalatesAt = now + Duration.ofHours(12))))
    val vm = viewModel(clock = { time })
    val screen = show(vm)
    assertEquals(1, vm.uiState.value.daysUntilEscalation)

    elapse(Duration.ofHours(13))

    assertEquals(0, vm.uiState.value.daysUntilEscalation)
    screen.cancel()
  }

  @Test
  fun aSettledDisputeStopsTheCountdown() {
    val vm = viewModel(clock = { time })
    val screen = show(vm)

    disputes.put(open.copy(agreedAt = now))
    elapse(Duration.ofHours(12))

    assertEquals(0, vm.uiState.value.daysUntilEscalation)
    screen.cancel()
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

  @Test
  fun aDisputeGoneAfterItWasShownIsARefreshFailure() {
    val vm = viewModel()

    disputes.remove("pump")

    val state = vm.uiState.value
    assertTrue(state.refreshFailed)
    assertFalse(state.loadFailed)
    assertEquals(70, state.latestProposal?.lenderPercent)
  }

  // ---------- actions ----------

  /** Asks [vm] to escalate and confirms it, as the user does on the screen. */
  private fun confirmEscalate(vm: DisputeViewModel) {
    vm.askEscalate()
    vm.escalate()
  }

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
  fun editingStartsFromAnEvenSplitWithoutAProposal() {
    disputes = FakeDisputeRepository(listOf(open.copy(proposals = emptyList())))
    val vm = viewModel()

    vm.startEditing()

    assertEquals(50, vm.uiState.value.draftLenderPercent)
  }

  @Test
  fun theirNewOfferKeepsMyDraftButANewStageEndsTheEditing() {
    val vm = viewModel()
    vm.startEditing()
    vm.setDraft(55)

    disputes.put(open.copy(proposals = listOf(mine, hers, Proposal("ana", 80, now))))
    assertEquals(55, vm.uiState.value.draftLenderPercent)

    disputes.put(open.copy(stage = DisputeStage.LLM_PROPOSAL))
    assertFalse(vm.uiState.value.isEditing)
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
  fun theDraftDoesNotMoveWhileTheEditorIsClosed() {
    val vm = viewModel()

    vm.setDraft(50)

    assertNull(vm.uiState.value.draftLenderPercent)
  }

  // ---------- factory ----------

  @Test
  fun factoryCreatesTheViewModel() {
    val vm =
        DisputeViewModel.Factory("pump", disputes, rentals, auth, connectivity)
            .create(DisputeViewModel::class.java)

    assertEquals("Bike pump", vm.uiState.value.itemTitle)
  }

  @Test(expected = IllegalArgumentException::class)
  fun factoryRejectsOtherViewModels() {
    DisputeViewModel.Factory("pump", disputes, rentals, auth, connectivity)
        .create(OtherViewModel::class.java)
  }

  private class OtherViewModel : ViewModel()
}
