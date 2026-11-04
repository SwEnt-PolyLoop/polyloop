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
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    disputes = FakeDisputeRepository(listOf(open))
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
  fun goingOfflineIsShown() {
    val vm = viewModel()

    connectivity.setOnline(false)

    assertFalse(vm.uiState.value.isOnline)
  }
}
