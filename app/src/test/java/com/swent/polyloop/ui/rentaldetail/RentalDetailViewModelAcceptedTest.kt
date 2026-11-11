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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The Accepted state of Rental detail, and the server's day it reads for Start Pickup. */
@OptIn(ExperimentalCoroutinesApi::class)
class RentalDetailViewModelAcceptedTest {

  private val now = Instant.parse("2026-11-04T10:00:00Z")

  private val request =
      Rental(
          id = "r1",
          listingId = "l1",
          listingTitle = "Camping tent",
          ownerId = "owner",
          ownerName = "Mara",
          borrowerId = "borrower",
          borrowerName = "Loca",
          startDate = LocalDate.of(2026, 12, 10),
          endDate = LocalDate.of(2026, 12, 12),
          status = RentalStatus.REQUESTED,
          pricePerDay = 5,
          totalPrice = 15,
          pickupArea = "Ecublens",
          dropOffArea = "Renens",
          createdAt = now,
      )

  private val accepted =
      request.copy(
          status = RentalStatus.ACCEPTED,
          deposit = 60,
          pickupAddress = "Route Cantonale 1",
          returnAddress = "Rue de Lausanne 10",
      )

  private lateinit var rentals: FakeRentalRepository

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  // A week after the phone's date, so a day read from the phone would not match.
  private val serverDay = LocalDate.now().plusWeeks(1)

  /**
   * The ViewModel for [rental], with [wrap] around the fake repository to change one of its calls.
   */
  private fun viewModel(
      rental: Rental,
      wrap: (FakeRentalRepository) -> RentalRepository = { it },
  ): RentalDetailViewModel {
    rentals = FakeRentalRepository(rentals = listOf(rental), clock = { now })
    rentals.serverNow = serverDay.atTime(12, 0).atZone(ZoneId.of("Europe/Zurich")).toInstant()
    val repository = wrap(rentals)
    return RentalDetailViewModel(
        rental.id,
        repository,
        FakeListingRepository(),
        FakeProfileRepository(),
        FakeAuthRepository(signedInUser = AuthUser("borrower", "l@epfl.ch", "Loca")),
        FakeConnectivityRepository(),
        ServerClock(
            repository,
            InMemoryServerClockStore(),
            elapsedRealtime = { 0L },
            bootCount = { 0 },
        ),
    )
  }

  @Test
  fun anAcceptedRentalGetsTheServersDay() {
    val state = viewModel(accepted).uiState.value

    assertEquals(serverDay, state.serverToday)
  }

  @Test
  fun anAcceptedRentalIsStillLoadingWhileTheServerIsRead() {
    val gate = CompletableDeferred<Unit>()
    val vm =
        viewModel(accepted) { fake ->
          object : RentalRepository by fake {
            override suspend fun serverNow(): Result<Instant> {
              gate.await()
              return fake.serverNow()
            }
          }
        }
    assertTrue(vm.uiState.value.isLoading)

    gate.complete(Unit)

    assertFalse(vm.uiState.value.isLoading)
    assertEquals(serverDay, vm.uiState.value.serverToday)
  }

  @Test
  fun aRequestDoesNotReadTheServersDay() {
    val state = viewModel(request).uiState.value

    assertNull(state.serverToday)
    assertEquals(0, rentals.serverNowCalls)
  }

  @Test
  fun aRequestAcceptedWhileTheScreenIsOpenReadsTheServersDayOnce() = runTest {
    val vm = viewModel(request)
    rentals.makeOffer("r1", 180, "Route Cantonale 1", "Rue de Lausanne 10").getOrThrow()

    rentals.acceptOffer("r1").getOrThrow()

    assertEquals(serverDay, vm.uiState.value.serverToday)
    assertEquals(1, rentals.serverNowCalls)
  }

  @Test
  fun anUnknownServerDayLeavesItNullWithoutAnError() {
    val vm =
        viewModel(accepted) { fake ->
          object : RentalRepository by fake {
            override suspend fun serverNow(): Result<Instant> =
                Result.failure(IOException("offline"))
          }
        }

    val state = vm.uiState.value
    assertFalse(state.isLoading)
    assertNull(state.serverToday)
    assertNull(state.errorMsg)
    assertEquals(RentalStatus.ACCEPTED, state.status)
  }

  @Test
  fun anAcceptedRentalGivesItsDepositAndAddresses() {
    val state = viewModel(accepted).uiState.value

    assertTrue(state.isSupported)
    assertEquals(RentalStatus.ACCEPTED, state.status)
    assertEquals(60, state.deposit)
    assertEquals("Route Cantonale 1", state.pickupAddress)
    assertEquals("Rue de Lausanne 10", state.returnAddress)
    assertNull(state.offer)
  }

  @Test
  fun aRequestHasNoDepositOrAddressesYet() {
    val state = viewModel(request).uiState.value

    assertEquals(0, state.deposit)
    assertEquals("", state.pickupAddress)
  }

  @Test
  fun anOfferAcceptedWhileTheScreenIsOpenShowsTheAcceptedState() = runTest {
    val vm = viewModel(request)
    rentals.makeOffer("r1", 180, "Route Cantonale 1", "Rue de Lausanne 10").getOrThrow()

    rentals.acceptOffer("r1").getOrThrow()

    val state = vm.uiState.value
    assertTrue(state.isSupported)
    assertEquals(RentalStatus.ACCEPTED, state.status)
    assertEquals(180, state.deposit)
    assertEquals("Route Cantonale 1", state.pickupAddress)
    assertEquals("Rue de Lausanne 10", state.returnAddress)
    assertNull(state.offer)
  }
}
