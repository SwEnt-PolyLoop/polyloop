// Made with Claude.

package com.swent.polyloop.ui.rentaldetail

import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.auth.FakeAuthRepository
import com.swent.polyloop.model.connectivity.FakeConnectivityRepository
import com.swent.polyloop.model.profile.FakeProfileRepository
import com.swent.polyloop.model.rental.FakeRentalRepository
import com.swent.polyloop.model.rental.InMemoryServerClockStore
import com.swent.polyloop.model.rental.Rental
import com.swent.polyloop.model.rental.RentalRepository
import com.swent.polyloop.model.rental.RentalStatus
import com.swent.polyloop.model.rental.ServerClock
import com.swent.polyloop.ui.listingdetail.FakeListingRepository
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Cancelling from Rental detail: a request at any time, an accepted rental until the day before
 * pickup by the server's day, when the button is enabled, and [RentalDetailViewModel.cancel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RentalDetailViewModelCancelTest {

  private val now = Instant.parse("2026-11-04T10:00:00Z")
  private val pickupDay = LocalDate.of(2026, 12, 10)
  private val lastCancelDay = pickupDay.minusDays(1)

  private val borrower = AuthUser("borrower", "l@epfl.ch", "Loca")
  private val lender = AuthUser("owner", "m@epfl.ch", "Mara")

  private val request =
      Rental(
          id = "r1",
          listingId = "l1",
          listingTitle = "Camping tent",
          ownerId = "owner",
          ownerName = "Mara",
          borrowerId = "borrower",
          borrowerName = "Loca",
          startDate = pickupDay,
          endDate = pickupDay.plusDays(2),
          status = RentalStatus.REQUESTED,
          pricePerDay = 5,
          totalPrice = 15,
          pickupArea = "Ecublens",
          dropOffArea = "Renens",
          createdAt = now,
      )

  private val accepted = request.copy(status = RentalStatus.ACCEPTED, deposit = 60)

  private lateinit var rentals: FakeRentalRepository
  private var cancelCalls = 0

  // Also runs the server clock's reads, so they finish before each assertion.
  private val testDispatcher = UnconfinedTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun noonOn(day: LocalDate): Instant =
      day.atTime(12, 0).atZone(ZoneId.of("Europe/Zurich")).toInstant()

  /**
   * The ViewModel for [rental] as seen by [user], with the server on [serverDay] (unknown when
   * null). [cancelOutcome], when set, replaces the fake's cancel; [serverGate] holds the server
   * read; [live], when set, drives the followed rental by hand. Cancels are counted in
   * [cancelCalls].
   */
  private fun viewModel(
      rental: Rental,
      serverDay: LocalDate? = lastCancelDay,
      user: AuthUser = borrower,
      connectivity: FakeConnectivityRepository = FakeConnectivityRepository(),
      cancelOutcome: (suspend () -> Result<Unit>)? = null,
      serverGate: CompletableDeferred<Unit>? = null,
      live: MutableStateFlow<Rental?>? = null,
  ): RentalDetailViewModel {
    rentals = FakeRentalRepository(rentals = listOf(rental), clock = { now })
    serverDay?.let { rentals.serverNow = noonOn(it) }
    val fake = rentals
    val repository =
        object : RentalRepository by fake {
          override suspend fun serverNow(): Result<Instant> {
            serverGate?.await()
            return if (serverDay == null) Result.failure(IOException("offline"))
            else fake.serverNow()
          }

          override suspend fun cancelRental(rentalId: String): Result<Unit> {
            cancelCalls++
            return cancelOutcome?.invoke() ?: fake.cancelRental(rentalId)
          }

          override fun observeRental(id: String): Flow<Rental?> = live ?: fake.observeRental(id)
        }
    return RentalDetailViewModel(
        rental.id,
        repository,
        FakeListingRepository(),
        FakeProfileRepository(),
        FakeAuthRepository(signedInUser = user),
        connectivity,
        ServerClock(
            repository,
            InMemoryServerClockStore(),
            elapsedRealtime = { 0L },
            bootCount = { 0 },
            ioDispatcher = testDispatcher,
        ),
    )
  }

  // ---------- The server's day ----------

  @Test
  fun aRequestDoesNotReadTheServersDay() {
    val vm = viewModel(request)

    assertNull(vm.uiState.value.serverToday)
    assertEquals(0, rentals.serverNowCalls)
  }

  @Test
  fun comingBackToARequestReadsNothing() {
    val vm = viewModel(request)

    vm.onScreenResumed()

    assertEquals(0, rentals.serverNowCalls)
  }

  @Test
  fun aRequestAcceptedWhileTheScreenIsOpenReadsTheDayOnce() {
    val live = MutableStateFlow<Rental?>(request)
    val vm = viewModel(request, live = live)

    live.value = accepted

    assertEquals(RentalStatus.ACCEPTED, vm.uiState.value.status)
    assertEquals(lastCancelDay, vm.uiState.value.serverToday)
    assertEquals(1, rentals.serverNowCalls)
  }

  // ---------- cancelUntil ----------

  @Test
  fun cancelUntilIsTheDayBeforePickupForAnAcceptedRental() {
    assertEquals(lastCancelDay, viewModel(accepted).uiState.value.cancelUntil)
  }

  @Test
  fun cancelUntilIsNullForARequestAndOtherStatuses() {
    assertNull(viewModel(request).uiState.value.cancelUntil)
    for (status in listOf(RentalStatus.ACTIVE, RentalStatus.DISPUTED, RentalStatus.CANCELLED)) {
      assertNull(RentalDetailUiState(status = status, startDate = pickupDay).cancelUntil)
    }
    assertNull(RentalDetailUiState(status = RentalStatus.ACCEPTED).cancelUntil)
  }

  // ---------- canCancel ----------

  @Test
  fun anAcceptedRentalCanBeCancelledOnItsLastDayEvenWhenThePhoneSaysItIsLater() {
    // The phone's date is a week after the pickup day; only the server's counts.
    val start = LocalDate.now().minusWeeks(1)
    val rental = accepted.copy(startDate = start, endDate = start.plusDays(2))

    val state = viewModel(rental, serverDay = start.minusDays(1)).uiState.value

    assertTrue(state.canCancel)
  }

  @Test
  fun anAcceptedRentalCannotBeCancelledFromItsPickupDayEvenWhenThePhoneSaysItIsEarlier() {
    // The phone's date is a week before the pickup day; only the server's counts.
    val start = LocalDate.now().plusWeeks(1)
    val rental = accepted.copy(startDate = start, endDate = start.plusDays(2))

    assertFalse(viewModel(rental, serverDay = start).uiState.value.canCancel)
    assertFalse(viewModel(rental, serverDay = start.plusDays(3)).uiState.value.canCancel)
  }

  @Test
  fun aRequestCanStillBeCancelledAfterItsPickupDay() {
    // Both its dates are past by the phone's date, and the screen state holds a later server day.
    val start = LocalDate.now().minusWeeks(1)
    val past = request.copy(startDate = start, endDate = start.plusDays(2))

    assertTrue(viewModel(past).uiState.value.canCancel)
    assertTrue(
        RentalDetailUiState(
                status = RentalStatus.REQUESTED,
                startDate = pickupDay,
                serverToday = pickupDay.plusDays(5),
            )
            .canCancel
    )
  }

  @Test
  fun aRequestCanBeCancelledWhenTheServersDayIsUnknown() {
    val state = viewModel(request, serverDay = null).uiState.value

    assertNull(state.serverToday)
    assertTrue(state.canCancel)
  }

  @Test
  fun cannotCancelWhileTheServersDayIsRead() {
    val gate = CompletableDeferred<Unit>()
    val vm = viewModel(accepted, serverGate = gate)
    assertTrue(vm.uiState.value.isReadingServerToday)
    assertFalse(vm.uiState.value.canCancel)

    gate.complete(Unit)

    assertTrue(vm.uiState.value.canCancel)
  }

  @Test
  fun anAcceptedRentalCannotBeCancelledWhenTheServersDayIsUnknown() {
    val state = viewModel(accepted, serverDay = null).uiState.value

    assertNull(state.serverToday)
    assertFalse(state.canCancel)
  }

  @Test
  fun cannotCancelOffline() {
    for (rental in listOf(request, accepted)) {
      val vm = viewModel(rental, connectivity = FakeConnectivityRepository(online = false))

      assertFalse(vm.uiState.value.canCancel)
    }
  }

  @Test
  fun theLenderAndTheBorrowerCanBothCancelARequestAndAnAcceptedRental() {
    for (rental in listOf(request, accepted)) {
      for (user in listOf(borrower, lender)) {
        assertTrue(viewModel(rental, user = user).uiState.value.canCancel)
      }
    }
  }

  // ---------- cancel() ----------

  @Test
  fun cancelCallsTheRepositoryOnceAndTheLiveRentalShowsThePlaceholder() {
    val vm = viewModel(request)
    val gate = CompletableDeferred<Unit>()
    rentals.gate = gate

    vm.cancel()
    vm.cancel() // ignored: a cancel is already running

    assertTrue(vm.uiState.value.isCancelling)
    assertFalse(vm.uiState.value.canCancel)
    assertEquals(1, cancelCalls)

    gate.complete(Unit)

    val state = vm.uiState.value
    assertEquals(RentalStatus.CANCELLED, rentals.rentals.single().status)
    assertFalse(state.isSupported)
    assertFalse(state.isCancelling)
    assertNull(state.cancelError)
  }

  @Test
  fun anAcceptedRentalCanBeCancelled() {
    val vm = viewModel(accepted, user = lender)

    vm.cancel()

    assertEquals(RentalStatus.CANCELLED, rentals.rentals.single().status)
    assertFalse(vm.uiState.value.isSupported)
  }

  @Test
  fun aFailedCancelSetsItsMessageAndKeepsTheRental() {
    val vm = viewModel(request, cancelOutcome = { Result.failure(IOException("down")) })

    vm.cancel()

    val state = vm.uiState.value
    assertEquals("down", state.cancelError)
    assertFalse(state.isCancelling)
    assertEquals(RentalStatus.REQUESTED, state.status)
    assertTrue(state.canCancel)
  }

  @Test
  fun aFailedCancelWithoutAMessageSetsABlankError() {
    val vm = viewModel(request, cancelOutcome = { Result.failure(IOException()) })

    vm.cancel()

    assertEquals("", vm.uiState.value.cancelError)
  }

  @Test
  fun aNewCancelClearsThePreviousError() {
    val gate = CompletableDeferred<Unit>()
    var attempts = 0
    val vm =
        viewModel(
            request,
            cancelOutcome = {
              attempts++
              if (attempts > 1) gate.await()
              Result.failure(IOException("down"))
            },
        )
    vm.cancel()
    assertEquals("down", vm.uiState.value.cancelError)

    vm.cancel()

    assertNull(vm.uiState.value.cancelError)
    assertTrue(vm.uiState.value.isCancelling)
    gate.complete(Unit)
    assertEquals("down", vm.uiState.value.cancelError)
  }

  @Test
  fun cancelDoesNothingWhenTheRentalCannotBeCancelled() {
    viewModel(accepted, serverDay = pickupDay).cancel()
    viewModel(request, connectivity = FakeConnectivityRepository(online = false)).cancel()
    viewModel(accepted, serverDay = null).cancel()

    assertEquals(0, cancelCalls)
  }
}
