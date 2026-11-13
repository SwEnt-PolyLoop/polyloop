// Made with Claude.

package com.swent.polyloop.ui.rentaldetail

import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.auth.FakeAuthRepository
import com.swent.polyloop.model.connectivity.FakeConnectivityRepository
import com.swent.polyloop.model.profile.FakeProfileRepository
import com.swent.polyloop.model.profile.UserProfile
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

/** The Active state of Rental detail, and the server's day it reads for Start Return. */
@OptIn(ExperimentalCoroutinesApi::class)
class RentalDetailViewModelActiveTest {

  private val now = Instant.parse("2026-11-04T10:00:00Z")

  private val request =
      Rental(
          id = "r1",
          listingId = "l1",
          listingTitle = "Ski helmet",
          ownerId = "owner",
          ownerName = "Mara",
          borrowerId = "borrower",
          borrowerName = "Loca",
          startDate = LocalDate.of(2026, 10, 1),
          endDate = LocalDate.of(2026, 10, 8),
          status = RentalStatus.REQUESTED,
          pricePerDay = 15,
          totalPrice = 120,
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

  private val active = accepted.copy(status = RentalStatus.ACTIVE)

  private val profiles =
      listOf(
          UserProfile("owner", "Mara Rossi", "m@epfl.ch", averageRating = 4.6, reviewCount = 5),
          UserProfile("borrower", "Loca Meier", "l@epfl.ch", averageRating = 3.0, reviewCount = 2),
      )

  private lateinit var rentals: FakeRentalRepository

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

  // A week after the phone's date, so a day read from the phone would not match.
  private val serverDay = LocalDate.now().plusWeeks(1)

  /**
   * The ViewModel for [rental] as [uid], with [wrap] around the fake repository to change one of
   * its calls.
   */
  private fun viewModel(
      rental: Rental,
      uid: String = "borrower",
      connectivity: FakeConnectivityRepository = FakeConnectivityRepository(),
      wrap: (FakeRentalRepository) -> RentalRepository = { it },
  ): RentalDetailViewModel {
    rentals = FakeRentalRepository(rentals = listOf(rental), clock = { now })
    rentals.serverNow = serverDay.atTime(12, 0).atZone(ZoneId.of("Europe/Zurich")).toInstant()
    val repository = wrap(rentals)
    return RentalDetailViewModel(
        rental.id,
        repository,
        FakeListingRepository(),
        FakeProfileRepository(profiles),
        FakeAuthRepository(signedInUser = AuthUser(uid, "$uid@epfl.ch", uid)),
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

  /** Asserts what both roles see of [active], apart from the other person. */
  private fun assertActive(state: RentalDetailUiState) {
    assertTrue(state.isSupported)
    assertFalse(state.isLoading)
    assertNull(state.errorMsg)
    assertEquals(RentalStatus.ACTIVE, state.status)
    assertEquals("Ski helmet", state.itemTitle)
    assertEquals(LocalDate.of(2026, 10, 1), state.startDate)
    assertEquals(LocalDate.of(2026, 10, 8), state.endDate)
    assertEquals(120, state.rentalPrice)
    assertEquals(60, state.deposit)
    assertEquals("Rue de Lausanne 10", state.returnAddress)
  }

  @Test
  fun anActiveRentalIsSupportedForTheBorrower() {
    val state = viewModel(active).uiState.value

    assertActive(state)
    assertFalse(state.isLender)
    assertEquals(
        OtherPerson(uid = "owner", name = "Mara Rossi", averageRating = 4.6, reviewCount = 5),
        state.otherPerson,
    )
  }

  @Test
  fun anActiveRentalIsSupportedForTheLender() {
    val state = viewModel(active, uid = "owner").uiState.value

    assertActive(state)
    assertTrue(state.isLender)
    assertEquals(
        OtherPerson(uid = "borrower", name = "Loca Meier", averageRating = 3.0, reviewCount = 2),
        state.otherPerson,
    )
  }

  @Test
  fun anActiveRentalGetsTheServersDay() {
    val state = viewModel(active).uiState.value

    assertEquals(serverDay, state.serverToday)
    assertFalse(state.isReadingServerToday)
    assertEquals(1, rentals.serverNowCalls)
  }

  @Test
  fun anActiveRentalShowsWhileTheServersDayIsRead() {
    val gate = CompletableDeferred<Unit>()
    val vm =
        viewModel(active) { fake ->
          object : RentalRepository by fake {
            override suspend fun serverNow(): Result<Instant> {
              gate.await()
              return fake.serverNow()
            }
          }
        }
    val reading = vm.uiState.value
    assertFalse(reading.isLoading)
    assertEquals(RentalStatus.ACTIVE, reading.status)
    assertNull(reading.serverToday)
    assertTrue(reading.isReadingServerToday)

    gate.complete(Unit)

    val read = vm.uiState.value
    assertEquals(serverDay, read.serverToday)
    assertFalse(read.isReadingServerToday)
  }

  @Test
  fun aPickupWhileTheScreenIsOpenShowsTheActiveStateWithoutReadingTheDayAgain() {
    // The live rental is driven by hand, so it can move from Accepted to Active.
    val live = MutableStateFlow<Rental?>(accepted)
    val vm =
        viewModel(accepted) { fake ->
          object : RentalRepository by fake {
            override fun observeRental(id: String): Flow<Rental?> = live
          }
        }
    assertEquals(1, rentals.serverNowCalls)

    live.value = active

    val state = vm.uiState.value
    assertTrue(state.isSupported)
    assertEquals(RentalStatus.ACTIVE, state.status)
    assertEquals(serverDay, state.serverToday)
    assertEquals(1, rentals.serverNowCalls)
  }

  @Test
  fun aRequestPickedUpWhileTheScreenIsOpenReadsTheServersDayOnce() {
    val live = MutableStateFlow<Rental?>(request)
    val vm =
        viewModel(request) { fake ->
          object : RentalRepository by fake {
            override fun observeRental(id: String): Flow<Rental?> = live
          }
        }
    assertEquals(0, rentals.serverNowCalls)

    live.value = active
    live.value = active.copy(deposit = 70)

    assertEquals(serverDay, vm.uiState.value.serverToday)
    assertEquals(70, vm.uiState.value.deposit)
    assertEquals(1, rentals.serverNowCalls)
  }

  @Test
  fun anUnknownServerDayLeavesItNullWithoutAnError() {
    val vm =
        viewModel(active) { fake ->
          object : RentalRepository by fake {
            override suspend fun serverNow(): Result<Instant> =
                Result.failure(IOException("offline"))
          }
        }

    val state = vm.uiState.value
    assertFalse(state.isLoading)
    assertFalse(state.isReadingServerToday)
    assertNull(state.serverToday)
    assertNull(state.errorMsg)
    assertEquals(RentalStatus.ACTIVE, state.status)
  }

  @Test
  fun anUnknownServerDayIsReadAgainWhenThePhoneComesBackOnline() {
    val connectivity = FakeConnectivityRepository()
    var reachable = false
    val vm =
        viewModel(active, connectivity = connectivity) { fake ->
          object : RentalRepository by fake {
            override suspend fun serverNow(): Result<Instant> =
                if (reachable) fake.serverNow() else Result.failure(IOException("offline"))
          }
        }
    assertNull(vm.uiState.value.serverToday)
    connectivity.setOnline(false)

    reachable = true
    connectivity.setOnline(true)

    assertEquals(serverDay, vm.uiState.value.serverToday)
    assertFalse(vm.uiState.value.isReadingServerToday)
  }
}
