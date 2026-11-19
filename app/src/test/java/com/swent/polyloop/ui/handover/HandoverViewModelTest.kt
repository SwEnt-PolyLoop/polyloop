// Made with Claude.

package com.swent.polyloop.ui.handover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.auth.FakeAuthRepository
import com.swent.polyloop.model.connectivity.FakeConnectivityRepository
import com.swent.polyloop.model.dispute.DisputeStage
import com.swent.polyloop.model.dispute.FakeDisputeRepository
import com.swent.polyloop.model.handover.FakeHandoverRepository
import com.swent.polyloop.model.handover.FakeHandoverRepository.StoredPhoto
import com.swent.polyloop.model.handover.HandoverCode
import com.swent.polyloop.model.handover.HandoverPhase
import com.swent.polyloop.model.handover.HandoverPhoto
import com.swent.polyloop.model.handover.HandoverRepository
import com.swent.polyloop.model.handover.RentalChangedException
import com.swent.polyloop.model.rental.FakeRentalRepository
import com.swent.polyloop.model.rental.InMemoryServerClockStore
import com.swent.polyloop.model.rental.Rental
import com.swent.polyloop.model.rental.RentalRepository
import com.swent.polyloop.model.rental.RentalStatus
import com.swent.polyloop.model.rental.ServerClock
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
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

@OptIn(ExperimentalCoroutinesApi::class)
class HandoverViewModelTest {

  private val pickupDay = LocalDate.of(2026, 10, 6)
  private val returnDay = pickupDay.plusDays(2)

  private val rental =
      Rental(
          id = "rental1",
          listingId = "listing1",
          listingTitle = "Camping tent",
          ownerId = "owner",
          ownerName = "Mara",
          borrowerId = "borrower",
          borrowerName = "Loca",
          startDate = pickupDay,
          endDate = returnDay,
          status = RentalStatus.ACCEPTED,
          pricePerDay = 15,
          totalPrice = 45,
          pickupArea = "Ecublens",
          dropOffArea = "Renens",
          deposit = 80,
          createdAt = Instant.parse("2026-10-01T09:30:00Z"),
      )

  private val borrower = AuthUser(uid = "borrower", email = "loca@epfl.ch", name = "Loca")

  private lateinit var rentalRepository: FakeRentalRepository
  private lateinit var handoverRepository: FakeHandoverRepository
  private lateinit var disputeRepository: FakeDisputeRepository
  private lateinit var authRepository: FakeAuthRepository
  private lateinit var connectivity: FakeConnectivityRepository
  private val deleted = mutableListOf<String>()

  // The time since the phone booted, sleep included, as the ViewModel reads it.
  private var elapsed = 0L

  // Also runs the server clock's reads, so they finish before each assertion.
  private val testDispatcher = UnconfinedTestDispatcher()

  /** Noon on [day] in Zurich, as the server's clock gives it. */
  private fun noonOn(day: LocalDate): Instant =
      day.atTime(12, 0).atZone(ZoneId.of("Europe/Zurich")).toInstant()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    rentalRepository = FakeRentalRepository(rentals = listOf(rental))
    rentalRepository.serverNow = noonOn(pickupDay)
    handoverRepository = FakeHandoverRepository(currentUid = "borrower")
    authRepository = FakeAuthRepository(signedInUser = borrower)
    disputeRepository =
        FakeDisputeRepository(
            currentUid = { authRepository.getCurrentUser()?.uid },
            clock = { Instant.parse("2026-10-08T17:00:00Z") },
        )
    connectivity = FakeConnectivityRepository()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun viewModel(
      id: String = rental.id,
      repository: RentalRepository = rentalRepository,
      rentals: RentalRepository = repository,
      handover: HandoverRepository = handoverRepository,
  ) =
      HandoverViewModel(
          id,
          rentals,
          handover,
          disputeRepository,
          authRepository,
          connectivity,
          serverClock(repository),
          deletePhoto = { deleted += it },
          elapsedRealtime = { elapsed },
      )

  private fun serverClock(repository: RentalRepository) =
      ServerClock(
          repository,
          InMemoryServerClockStore(),
          // The same time since boot as the ViewModel's, so offline the clock moves on with it.
          elapsedRealtime = { elapsed },
          bootCount = { 0 },
          ioDispatcher = testDispatcher,
      )

  /** [rentalRepository] with a server that cannot be reached until [reachable] returns true. */
  private fun unreachableServer(reachable: () -> Boolean = { false }) =
      object : RentalRepository by rentalRepository {
        override suspend fun serverNow(): Result<Instant> =
            if (reachable()) rentalRepository.serverNow()
            else Result.failure(IOException("offline"))
      }

  /**
   * The rentals and the server of [base], but the server answers only once [gate] completes, when
   * one is set; [reads] counts the times it is asked.
   */
  private class GatedServer(private val base: RentalRepository) : RentalRepository by base {
    var gate: CompletableDeferred<Unit>? = null
    var reads = 0

    override suspend fun serverNow(): Result<Instant> {
      reads++
      gate?.await()
      return base.serverNow()
    }
  }

  /** Stores [rental] in [status] as the only rental, on the same server's day. */
  private fun useStatus(status: RentalStatus) {
    rentalRepository =
        FakeRentalRepository(listOf(rental.copy(status = status))).apply {
          serverNow = rentalRepository.serverNow
        }
  }

  /**
   * The rentals of [base] but [observeRental] follows [live], so a test can change the status, and
   * fails while [error] is set, as a listener that breaks.
   */
  private class LiveRental(initial: Rental, base: RentalRepository) : RentalRepository by base {
    val live = MutableStateFlow<Rental?>(initial)
    val error = MutableStateFlow<Throwable?>(null)

    override fun observeRental(id: String): Flow<Rental?> =
        combine(live, error) { rental, error -> if (error != null) throw error else rental }
  }

  private fun liveRental() = LiveRental(rental, rentalRepository)

  @Test
  fun theBorrowerSeesTheItemTheLenderAndThePickupPhaseOnceLoaded() {
    val vm = viewModel()
    val state = vm.uiState.value
    assertFalse(state.isLoading)
    assertFalse(state.isLender)
    assertEquals("Camping tent", state.itemTitle)
    assertEquals("Mara", state.otherPersonName)
    assertEquals(HandoverPhase.PICKUP, state.phase)
    assertEquals(pickupDay, state.pickupDate)
    assertEquals(returnDay, state.returnDate)
    assertTrue(state.isPickupDay)
    assertEquals(80, state.deposit)
  }

  @Test
  fun theOwnerIsTheLenderAndSeesTheBorrower() {
    authRepository.signedInUser = AuthUser(uid = "owner", email = "mara@epfl.ch", name = "Mara")
    val state = viewModel().uiState.value

    assertTrue(state.isLender)
    assertEquals("Loca", state.otherPersonName)
  }

  @Test
  fun itIsThePickupDayOnlyOnTheRentalsFirstDay() {
    rentalRepository.serverNow = noonOn(pickupDay.minusDays(1))
    assertFalse(viewModel().uiState.value.isPickupDay)
    rentalRepository.serverNow = noonOn(pickupDay.plusDays(1))
    assertFalse(viewModel().uiState.value.isPickupDay)
  }

  @Test
  fun thePickupDayComesFromTheServersClockNotThePhones() {
    // The rental starts today by the phone's date, but the server's day is the day before.
    val phoneToday = LocalDate.now()
    rentalRepository = FakeRentalRepository(listOf(rental.copy(startDate = phoneToday)))
    rentalRepository.serverNow = noonOn(phoneToday.minusDays(1))

    val state = viewModel().uiState.value

    assertEquals(phoneToday.minusDays(1), state.serverToday)
    assertFalse(state.isPickupDay)
    assertFalse(state.canAddPhoto)
  }

  @Test
  fun theRentalShowsWhileTheServersDayIsRead() {
    val gate = CompletableDeferred<Unit>()
    val gated =
        object : RentalRepository by rentalRepository {
          override suspend fun serverNow(): Result<Instant> {
            gate.await()
            return rentalRepository.serverNow()
          }
        }
    val vm = viewModel(repository = gated)
    val reading = vm.uiState.value
    assertFalse(reading.isLoading)
    assertEquals(HandoverPhase.PICKUP, reading.phase)
    assertTrue(reading.isReadingServerToday)
    assertNull(reading.serverToday)
    assertFalse(reading.canAddPhoto)

    gate.complete(Unit)

    val read = vm.uiState.value
    assertFalse(read.isReadingServerToday)
    assertEquals(pickupDay, read.serverToday)
    assertTrue(read.canAddPhoto)
  }

  @Test
  fun anUnknownServerDayTakesNoPhotoAndShowsNoError() {
    val vm = viewModel(repository = unreachableServer())

    val state = vm.uiState.value
    assertFalse(state.loadFailed)
    assertFalse(state.isReadingServerToday)
    assertNull(state.serverToday)
    assertFalse(state.isPickupDay)
    vm.addPhoto("file1")
    assertEquals(listOf("file1"), deleted)
    assertEquals(0, handoverRepository.addPhotoCalls)
  }

  @Test
  fun anUnknownServerDayIsReadAgainWhenThePhoneComesBackOnline() {
    connectivity.setOnline(false)
    var reachable = false
    val vm = viewModel(repository = unreachableServer { reachable })
    assertNull(vm.uiState.value.serverToday)

    reachable = true
    connectivity.setOnline(true)

    assertEquals(pickupDay, vm.uiState.value.serverToday)
    assertTrue(vm.uiState.value.canAddPhoto)
  }

  @Test
  fun aKnownServerDayIsNotReadAgainWhenThePhoneComesBackOnline() {
    viewModel()

    connectivity.setOnline(false)
    connectivity.setOnline(true)

    assertEquals(1, rentalRepository.serverNowCalls)
  }

  @Test
  fun aDayTheServerFailedToGiveOnlineIsReadAgainOnRetry() {
    var reachable = false
    val vm = viewModel(repository = unreachableServer { reachable })
    assertFalse(vm.uiState.value.isOffline)
    assertNull(vm.uiState.value.serverToday)

    reachable = true
    vm.retryServerToday()

    assertEquals(pickupDay, vm.uiState.value.serverToday)
    assertTrue(vm.uiState.value.canAddPhoto)
  }

  @Test
  fun aRetryReadsNothingWhenTheDayIsKnownOrThePhoneIsOffline() {
    viewModel().retryServerToday()
    assertEquals(1, rentalRepository.serverNowCalls)

    connectivity.setOnline(false)
    var reads = 0
    val vm =
        viewModel(
            repository =
                unreachableServer {
                  reads++
                  false
                }
        )
    vm.retryServerToday()
    assertEquals(1, reads)
  }

  @Test
  fun aRentalThatTakesNoPhotosDoesNotReadTheServersDay() {
    useStatus(RentalStatus.CLOSED)
    connectivity.setOnline(false)
    val vm = viewModel()
    connectivity.setOnline(true)
    vm.retryServerToday()

    assertNull(vm.uiState.value.phase)
    assertNull(vm.uiState.value.serverToday)
    assertEquals(0, rentalRepository.serverNowCalls)
  }

  @Test
  fun aRetryAfterAFailedReadReadsTheServersDay() {
    rentalRepository.failure = RuntimeException("offline")
    val vm = viewModel()
    assertNull(vm.uiState.value.serverToday)

    rentalRepository.failure = null
    vm.retry()

    assertEquals(pickupDay, vm.uiState.value.serverToday)
    assertTrue(vm.uiState.value.isPickupDay)
  }

  @Test
  fun theReturnCanBeDoneFromTheLastDayOn() {
    useStatus(RentalStatus.ACTIVE)
    rentalRepository.serverNow = noonOn(returnDay.minusDays(1))
    assertFalse(viewModel().uiState.value.isHandoverDay)

    rentalRepository.serverNow = noonOn(returnDay)
    assertTrue(viewModel().uiState.value.isHandoverDay)

    useStatus(RentalStatus.OVERDUE)
    rentalRepository.serverNow = noonOn(returnDay.plusDays(2))
    assertTrue(viewModel().uiState.value.isHandoverDay)
  }

  @Test
  fun theDepositHeldIsWhatTheLateFeesLeave() {
    val late = rental.copy(status = RentalStatus.OVERDUE, lateFees = 17)
    rentalRepository = FakeRentalRepository(listOf(late))
    assertEquals(63, viewModel().uiState.value.depositHeld)

    rentalRepository = FakeRentalRepository(listOf(late.copy(lateFees = 100)))
    assertEquals(0, viewModel().uiState.value.depositHeld)
  }

  // ---------- the day changing while the screen is open ----------

  @Test
  fun aScreenLeftOpenPastMidnightReadsTheNewDay() {
    rentalRepository.serverNow = noonOn(pickupDay.minusDays(1))
    val vm = viewModel()
    assertFalse(vm.uiState.value.isPickupDay)

    rentalRepository.serverNow = noonOn(pickupDay)
    testDispatcher.scheduler.advanceTimeBy(HALF_A_DAY_MS - 1)
    assertEquals(1, rentalRepository.serverNowCalls) // not before midnight
    testDispatcher.scheduler.advanceTimeBy(2)
    assertTrue(vm.uiState.value.isPickupDay)
  }

  @Test
  fun aKnownDayStaysShownButTakesNothingWhileItIsReadAgainAtMidnight() {
    val server = GatedServer(rentalRepository)
    handoverRepository.photos = photos(3)
    val vm = viewModel(repository = server)
    assertTrue(vm.uiState.value.canScan)
    val gate = CompletableDeferred<Unit>()
    server.gate = gate

    testDispatcher.scheduler.advanceTimeBy(HALF_A_DAY_MS + 1)
    assertEquals(2, server.reads) // read again at midnight
    val state = vm.uiState.value
    assertFalse(state.isReadingServerToday)
    assertEquals(pickupDay, state.serverToday)
    assertTrue(state.isDayOver)
    assertFalse(state.canAddPhoto)
    assertFalse(state.canScan)

    rentalRepository.serverNow = noonOn(pickupDay.plusDays(1))
    gate.complete(Unit)
    assertEquals(pickupDay.plusDays(1), vm.uiState.value.serverToday)
    assertFalse(vm.uiState.value.isDayOver)
  }

  @Test
  fun theLenderShowsNoCodeWhileTheDayIsReadAgainAtMidnight() {
    val server = GatedServer(rentalRepository)
    handoverRepository.photos = photos(3, takenBy = "owner")
    signInLender()
    val vm = viewModel(repository = server)
    assertTrue(vm.uiState.value.canShowCode)
    val gate = CompletableDeferred<Unit>()
    server.gate = gate

    testDispatcher.scheduler.advanceTimeBy(HALF_A_DAY_MS + 1)
    assertTrue(vm.uiState.value.isDayOver)
    assertFalse(vm.uiState.value.canShowCode)
    gate.complete(Unit)
  }

  @Test
  fun aRentalChangeDoesNotReadAnUnknownDayAgain() {
    var reads = 0
    val live = liveRental()
    val vm =
        viewModel(
            repository =
                unreachableServer {
                  reads++
                  false
                },
            rentals = live,
        )
    assertEquals(1, reads)

    live.live.value = rental.copy(lastMessage = "See you at noon")
    assertEquals(1, reads)
    assertFalse(vm.uiState.value.isReadingServerToday)
  }

  @Test
  fun aMidnightPassedWhileThePhoneSleptBlocksPhotosUntilTheNewDayIsRead() {
    val server = GatedServer(rentalRepository)
    handoverRepository.photos = photos(3)
    val vm = viewModel(repository = server)
    assertTrue(vm.uiState.value.canScan)

    // Deep sleep stops the wait for midnight, not the time since boot.
    elapsed += HALF_A_DAY_MS + 1
    val gate = CompletableDeferred<Unit>()
    server.gate = gate
    rentalRepository.serverNow = noonOn(pickupDay.plusDays(1))
    vm.retryServerToday()
    val state = vm.uiState.value
    assertTrue(state.isDayOver)
    assertEquals(pickupDay, state.serverToday)
    assertFalse(state.canAddPhoto)
    assertFalse(state.canScan)

    gate.complete(Unit)
    assertEquals(pickupDay.plusDays(1), vm.uiState.value.serverToday)
    assertFalse(vm.uiState.value.isDayOver)
  }

  @Test
  fun aMidnightPassedWhileThePhoneSleptIsWorkedOutOffline() {
    var reachable = true
    val vm = viewModel(repository = unreachableServer { reachable })

    reachable = false
    connectivity.setOnline(false)
    elapsed += HALF_A_DAY_MS + 1
    vm.retryServerToday()

    // Worked out from the last answer and the time since boot.
    assertEquals(pickupDay.plusDays(1), vm.uiState.value.serverToday)
    assertFalse(vm.uiState.value.isDayOver)
  }

  @Test
  fun aSleepBeforeMidnightDoesNotDelayTheNextRead() {
    val vm = viewModel()

    // An hour of sleep in the evening: the wait stops, the time since boot goes on.
    elapsed += HOUR_MS
    vm.retryServerToday()
    assertEquals(1, rentalRepository.serverNowCalls)

    testDispatcher.scheduler.advanceTimeBy(HALF_A_DAY_MS - HOUR_MS + 1)
    assertEquals(2, rentalRepository.serverNowCalls)
  }

  @Test
  fun aRentalThatNoLongerTakesPhotosForgetsTheDayAtMidnight() {
    val live = liveRental()
    val vm = viewModel(rentals = live)
    // Cancelled: a pickup confirmed stays on screen, so only this takes the phase away.
    live.live.value = rental.copy(status = RentalStatus.CANCELLED)

    testDispatcher.scheduler.advanceTimeBy(HALF_A_DAY_MS + 1)
    assertNull(vm.uiState.value.serverToday)
    assertEquals(1, rentalRepository.serverNowCalls)
  }

  // ---------- loading ----------

  @Test
  fun theScreenLoadsUntilTheRentalArrives() {
    val silent =
        object : RentalRepository by rentalRepository {
          override fun observeRental(id: String): Flow<Rental?> = MutableSharedFlow()
        }
    val state = viewModel(rentals = silent).uiState.value

    assertTrue(state.isLoading)
    assertFalse(state.loadFailed)
    assertNull(state.phase)
  }

  @Test
  fun aMissingRentalIsNotFoundAndNobodySignedInFails() {
    assertTrue(viewModel(id = "unknown").uiState.value.notFound)

    authRepository.signedInUser = null
    assertTrue(viewModel().uiState.value.loadFailed)
  }

  @Test
  fun aRentalMissingOfflineIsAnErrorUntilThePhoneIsBackOnline() {
    // Offline, a rental missing from the phone's cache may still exist.
    connectivity.setOnline(false)
    val vm = viewModel(id = "unknown")
    assertTrue(vm.uiState.value.loadFailed)
    assertFalse(vm.uiState.value.notFound)

    connectivity.setOnline(true)
    assertTrue(vm.uiState.value.notFound)
  }

  @Test
  fun aFailedReadIsRetriedButALoadedRentalIsNotReadAgain() {
    rentalRepository.failure = RuntimeException("offline")
    val vm = viewModel()
    assertTrue(vm.uiState.value.loadFailed)

    rentalRepository.failure = null
    vm.retry()
    assertEquals("Camping tent", vm.uiState.value.itemTitle)

    rentalRepository.failure = RuntimeException("would fail a new read")
    vm.retry()
    assertFalse(vm.uiState.value.loadFailed)
  }

  @Test
  fun aReturnOnScreenMovesOnWhenTheReturnIsScanned() {
    val active = rental.copy(status = RentalStatus.ACTIVE)
    val live = LiveRental(active, rentalRepository)
    handoverRepository.photos = photos(3) + photos(2, phase = HandoverPhase.RETURN)
    val vm = viewModel(rentals = live)
    assertEquals(HandoverPhase.RETURN, vm.uiState.value.phase)
    assertEquals(listOf(HandoverPhase.RETURN), vm.uiState.value.photos.map { it.phase }.distinct())

    live.live.value = active.copy(status = RentalStatus.RETURNED)
    assertNull(vm.uiState.value.phase)
    assertEquals(emptyList<HandoverPhoto>(), vm.uiState.value.photos)

    live.live.value = null
    assertTrue(vm.uiState.value.notFound)
  }

  // ---------- following the rental ----------

  @Test
  fun theBorrowerSeesThePickupOnceTheServerConfirmsIt() {
    handoverRepository.photos = photos(3)
    val vm = viewModel()
    assertTrue(vm.uiState.value.canScan)
    assertTrue(vm.uiState.value.canAddPhoto)

    rentalRepository.setStatus(rental.id, RentalStatus.ACTIVE)

    val state = vm.uiState.value
    assertTrue(state.isPickedUp)
    assertEquals(HandoverPhase.PICKUP, state.phase) // the screen stays on the pickup
    assertFalse(state.canScan)
    assertFalse(state.canAddPhoto) // the rules would refuse a pickup photo now
  }

  @Test
  fun theLenderSeesThePickupWithoutLeavingTheScreen() {
    handoverRepository.photos = photos(3, takenBy = "owner")
    signInLender()
    val vm = viewModel()
    assertTrue(vm.uiState.value.canShowCode)

    rentalRepository.setStatus(rental.id, RentalStatus.ACTIVE)

    assertTrue(vm.uiState.value.isPickedUp)
    assertFalse(vm.uiState.value.canShowCode)
  }

  @Test
  fun aPickupDoneStaysOnScreenAfterAReload() {
    val live = liveRental()
    val vm = viewModel(rentals = live)

    // The listener breaks, the rental is picked up meanwhile, and Retry follows it again.
    live.error.value = RuntimeException("listener broke")
    live.live.value = rental.copy(status = RentalStatus.ACTIVE)
    live.error.value = null
    vm.retry()

    assertEquals(HandoverPhase.PICKUP, vm.uiState.value.phase)
    assertTrue(vm.uiState.value.isPickedUp)
  }

  @Test
  fun aPickupDoneKeepsItsDayAtMidnightAndAfterASleep() {
    val vm = viewModel()
    rentalRepository.setStatus(rental.id, RentalStatus.ACTIVE)
    rentalRepository.serverNow = noonOn(pickupDay.plusDays(1))

    testDispatcher.scheduler.advanceTimeBy(HALF_A_DAY_MS + 1)
    elapsed += 2 * HALF_A_DAY_MS
    vm.retryServerToday() // the screen comes back after a sleep past the next midnight

    assertEquals(pickupDay, vm.uiState.value.serverToday)
    assertFalse(vm.uiState.value.isDayOver)
    assertEquals(1, rentalRepository.serverNowCalls)
  }

  @Test
  fun aLaterStatusKeepsThePickupDone() {
    val vm = viewModel()
    rentalRepository.setStatus(rental.id, RentalStatus.ACTIVE)
    rentalRepository.setStatus(rental.id, RentalStatus.DISPUTED)

    assertTrue(vm.uiState.value.isPickedUp)
    assertEquals(HandoverPhase.PICKUP, vm.uiState.value.phase)
  }

  @Test
  fun aFailedPhotoIsDroppedAndItsFileDeletedOncePickedUp() {
    val vm = viewModel()
    handoverRepository.failure = RuntimeException("offline")
    vm.addPhoto("file1")
    assertEquals("file1", vm.uiState.value.failedPhotoUri)

    rentalRepository.setStatus(rental.id, RentalStatus.ACTIVE)

    assertNull(vm.uiState.value.failedPhotoUri) // no retry tile left that would do nothing
    assertEquals(listOf("file1"), deleted)
  }

  @Test
  fun anUploadThatFailsAfterThePickupIsDroppedToo() {
    // The scan is allowed while a 4th photo uploads; the pickup is confirmed before it ends.
    val gate = CompletableDeferred<Unit>()
    handoverRepository.gate = gate
    val vm = viewModel()
    vm.addPhoto("file1")
    rentalRepository.setStatus(rental.id, RentalStatus.ACTIVE)

    handoverRepository.failure = RuntimeException("refused: the rental is ACTIVE")
    gate.complete(Unit)

    assertFalse(vm.uiState.value.isUploading)
    assertNull(vm.uiState.value.failedPhotoUri)
    assertEquals(listOf("file1"), deleted)
  }

  @Test
  fun aRentalCancelledWhileOpenHasNothingLeftToHandOver() {
    val vm = viewModel()
    rentalRepository.setStatus(rental.id, RentalStatus.CANCELLED)

    assertNull(vm.uiState.value.phase)
    assertFalse(vm.uiState.value.isPickedUp)
  }

  @Test
  fun aRentalDeletedWhileOpenIsNotFound() {
    val vm = viewModel()
    rentalRepository.remove(rental.id)

    assertTrue(vm.uiState.value.notFound)
  }

  // ---------- photos ----------

  /** [count] photos of the rental in [phase] by [takenBy], one second apart. */
  private fun photos(
      count: Int,
      takenBy: String = "borrower",
      rentalId: String = rental.id,
      phase: HandoverPhase = HandoverPhase.PICKUP,
  ) =
      List(count) {
        val photo =
            HandoverPhoto(
                "$takenBy$it",
                phase,
                Instant.ofEpochSecond(it.toLong()),
                "url$it",
            )
        StoredPhoto(rentalId, takenBy, photo)
      }

  @Test
  fun followsMyPickupPhotosAndCountsTheOnesStillNeeded() {
    handoverRepository.photos = photos(5, takenBy = "owner") + photos(5, rentalId = "other")
    val vm = viewModel()
    assertEquals(emptyList<HandoverPhoto>(), vm.uiState.value.photos)
    assertEquals(3, vm.uiState.value.photosNeeded)

    handoverRepository.photos = photos(2)
    assertEquals(listOf("borrower0", "borrower1"), vm.uiState.value.photos.map { it.id })
    assertEquals(1, vm.uiState.value.photosNeeded)

    handoverRepository.photos = photos(HandoverPhoto.MAX_PHOTOS)
    assertEquals(0, vm.uiState.value.photosNeeded)
  }

  @Test
  fun photosThatCannotBeLoadedAreReportedAndCanBeRetried() {
    handoverRepository.failure = RuntimeException("offline")
    handoverRepository.photos = photos(2)
    val vm = viewModel()
    assertTrue(vm.uiState.value.photosFailed)
    assertEquals(3, vm.uiState.value.photosNeeded)

    handoverRepository.failure = null
    vm.retry()
    assertFalse(vm.uiState.value.photosFailed)
    assertEquals(2, vm.uiState.value.photos.size)
    assertEquals(1, vm.uiState.value.photosNeeded)
  }

  @Test
  fun noPhotosAreReadWhenTheRentalTakesNone() {
    useStatus(RentalStatus.CLOSED)
    handoverRepository.failure = RuntimeException("would be reported if read")

    assertFalse(viewModel().uiState.value.photosFailed)
  }

  // ---------- adding photos ----------

  @Test
  fun aPhotoIsUploadedThenItsFileDeleted() {
    val gate = CompletableDeferred<Unit>()
    handoverRepository.gate = gate
    val vm = viewModel()

    vm.addPhoto("file1")
    assertTrue(vm.uiState.value.isUploading)
    assertFalse(vm.uiState.value.canAddPhoto)

    gate.complete(Unit)
    assertFalse(vm.uiState.value.isUploading)
    assertEquals(1, vm.uiState.value.photos.size)
    assertEquals(listOf("file1"), deleted)
  }

  @Test
  fun aReturnPhotoIsUploadedAsAReturnPhoto() {
    useStatus(RentalStatus.ACTIVE)
    rentalRepository.serverNow = noonOn(returnDay)
    val vm = viewModel()

    vm.addPhoto("file1")
    assertEquals(HandoverPhase.RETURN, handoverRepository.photos.single().photo.phase)
    assertEquals(1, vm.uiState.value.photos.size)
  }

  @Test
  fun leavingTheScreenDeletesThePhotoWaitingForARetry() {
    val store = ViewModelStore()
    val factory =
        object : ViewModelProvider.Factory {
          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T = viewModel() as T
        }
    val vm = ViewModelProvider(store, factory)[HandoverViewModel::class.java]
    handoverRepository.failure = RuntimeException("offline")
    vm.addPhoto("file1")
    assertEquals(emptyList<String>(), deleted)

    store.clear()
    assertEquals(listOf("file1"), deleted)
  }

  @Test
  fun aFailedUploadKeepsItsFileToTryAgain() {
    val vm = viewModel()
    handoverRepository.failure = RuntimeException("offline")

    vm.addPhoto("file1")
    assertEquals("file1", vm.uiState.value.failedPhotoUri)
    assertEquals(emptyList<String>(), deleted)

    handoverRepository.failure = null
    vm.retryUpload()
    assertNull(vm.uiState.value.failedPhotoUri)
    assertEquals(listOf("file1"), deleted)
    assertEquals(2, handoverRepository.addPhotoCalls)
  }

  @Test
  fun aFailedPhotoIsDeletedOnceItsDayIsOver() {
    val vm = viewModel()
    handoverRepository.failure = RuntimeException("down")
    vm.addPhoto("file1")

    rentalRepository.serverNow = noonOn(pickupDay.plusDays(1))
    testDispatcher.scheduler.advanceTimeBy(HALF_A_DAY_MS + 1)
    assertNull(vm.uiState.value.failedPhotoUri)
    assertEquals(listOf("file1"), deleted)
  }

  @Test
  fun aBrokenRentalListenerKeepsTheFailedPhotoForARetry() {
    val live = liveRental()
    val vm = viewModel(rentals = live)
    handoverRepository.failure = RuntimeException("down")
    vm.addPhoto("file1")

    live.error.value = RuntimeException("listener broke")
    assertTrue(vm.uiState.value.loadFailed)
    assertEquals("file1", vm.uiState.value.failedPhotoUri)

    live.error.value = null
    handoverRepository.failure = null
    vm.retry()
    vm.retryUpload()
    assertNull(vm.uiState.value.failedPhotoUri)
    assertEquals(HandoverPhase.PICKUP, handoverRepository.photos.single().photo.phase)
    assertEquals(listOf("file1"), deleted) // deleted once uploaded, never before
  }

  @Test
  fun aMissingRentalKeepsTheFailedPhotoUntilItComesBack() {
    val live = liveRental()
    val vm = viewModel(rentals = live)
    handoverRepository.failure = RuntimeException("down")
    vm.addPhoto("file1")

    live.live.value = null
    assertTrue(vm.uiState.value.notFound)
    assertEquals("file1", vm.uiState.value.failedPhotoUri)

    live.live.value = rental
    handoverRepository.failure = null
    vm.retryUpload()
    assertEquals(HandoverPhase.PICKUP, handoverRepository.photos.single().photo.phase)
    assertEquals(listOf("file1"), deleted)
  }

  @Test
  fun anUploadThatFailsWhileTheRentalIsMissingKeepsItsPhoto() {
    val live = liveRental()
    val gate = CompletableDeferred<Unit>()
    handoverRepository.gate = gate
    val vm = viewModel(rentals = live)
    vm.addPhoto("file1")

    live.live.value = null
    handoverRepository.failure = RuntimeException("down")
    gate.complete(Unit)

    assertEquals("file1", vm.uiState.value.failedPhotoUri)
    assertEquals(emptyList<String>(), deleted)
  }

  @Test
  fun aReloadKeepsTheUploadRunning() {
    val live = liveRental()
    val gate = CompletableDeferred<Unit>()
    handoverRepository.gate = gate
    val vm = viewModel(rentals = live)
    vm.addPhoto("file1")

    live.error.value = RuntimeException("listener broke")
    live.error.value = null
    vm.retry()
    assertTrue(vm.uiState.value.isUploading)
    assertFalse(vm.uiState.value.canAddPhoto)

    gate.complete(Unit)
    assertFalse(vm.uiState.value.isUploading)
    assertEquals(listOf("file1"), deleted)
  }

  @Test
  fun aFailedShotHasItsFileDeletedAndAddsNothing() {
    val vm = viewModel()

    vm.onCaptureFailed("file1")
    assertEquals(listOf("file1"), deleted)
    assertEquals(0, handoverRepository.addPhotoCalls)
  }

  @Test
  fun noNewPhotoIsTakenWhileOneWaitsForARetry() {
    val vm = viewModel()
    handoverRepository.failure = RuntimeException("down")
    vm.addPhoto("file1")
    assertFalse(vm.uiState.value.canAddPhoto)

    handoverRepository.failure = null
    vm.addPhoto("file2") // refused: the failed photo is kept, the new one deleted
    assertEquals("file1", vm.uiState.value.failedPhotoUri)
    assertEquals(listOf("file2"), deleted)
    assertEquals(1, handoverRepository.addPhotoCalls)
  }

  @Test
  fun offlineNoPhotoIsUploadedAndTheStateSaysSo() {
    val vm = viewModel()
    handoverRepository.failure = RuntimeException("down")
    vm.addPhoto("file1")

    connectivity.setOnline(false)
    assertTrue(vm.uiState.value.isOffline)
    assertFalse(vm.uiState.value.canUpload)
    vm.retryUpload()
    vm.addPhoto("file2")
    assertEquals(1, handoverRepository.addPhotoCalls)
    assertEquals(listOf("file2"), deleted)

    handoverRepository.failure = null
    connectivity.setOnline(true)
    vm.retryUpload()
    assertNull(vm.uiState.value.failedPhotoUri)
  }

  @Test
  fun reloadingKeepsTheOfflineState() {
    connectivity.setOnline(false)
    rentalRepository.failure = RuntimeException("down")
    val vm = viewModel()
    assertTrue(vm.uiState.value.isOffline)

    rentalRepository.failure = null
    vm.retry()
    assertEquals("Camping tent", vm.uiState.value.itemTitle)
    assertTrue(vm.uiState.value.isOffline)
  }

  @Test
  fun photosAreRefusedAndDeletedOffTheHandoverDayAtTwentyOrWhileUploading() {
    rentalRepository.serverNow = noonOn(pickupDay.plusDays(1))
    viewModel().addPhoto("late")

    rentalRepository.serverNow = noonOn(pickupDay)
    handoverRepository.photos = photos(HandoverPhoto.MAX_PHOTOS)
    viewModel().addPhoto("full")

    handoverRepository.photos = emptyList()
    handoverRepository.gate = CompletableDeferred()
    val vm = viewModel()
    vm.addPhoto("first")
    vm.addPhoto("second")
    vm.retryUpload() // nothing failed: ignored

    useStatus(RentalStatus.ACTIVE)
    viewModel().addPhoto("early") // before the return day

    assertEquals(listOf("late", "full", "second", "early"), deleted)
    assertEquals(1, handoverRepository.addPhotoCalls)
  }

  @Test
  fun aRentalThatTakesNoPhotosRefusesThem() {
    useStatus(RentalStatus.CLOSED)
    viewModel().addPhoto("file1")

    assertEquals(listOf("file1"), deleted)
    assertEquals(0, handoverRepository.addPhotoCalls)
  }

  // ---------- the lender's check ----------

  /** Signs in as the lender, for the repositories too. */
  private fun signInAsLender() {
    authRepository.signedInUser = AuthUser(uid = "owner", email = "lender@epfl.ch", name = "Lender")
    handoverRepository.currentUid = "owner"
  }

  /** The ViewModel of the returned rental, seen by its lender. */
  private fun lenderCheck(): HandoverViewModel {
    useStatus(RentalStatus.RETURNED)
    signInAsLender()
    return viewModel()
  }

  /** The lender's pickup and return photos, plus the borrower's return photos, never shown. */
  private fun checkPhotos() =
      photos(3, takenBy = "owner") +
          photos(4, takenBy = "owner", phase = HandoverPhase.RETURN) +
          photos(5, phase = HandoverPhase.RETURN)

  @Test
  fun onlyAReturnedRentalIsMarkedReturned() {
    useStatus(RentalStatus.RETURNED)
    assertTrue(viewModel().uiState.value.isReturned)
    useStatus(RentalStatus.CLOSED)
    assertFalse(viewModel().uiState.value.isReturned)
  }

  @Test
  fun theLenderChecksTheirPickupAndReturnPhotosOfAReturnedItem() {
    handoverRepository.photos = checkPhotos()
    val state = lenderCheck().uiState.value

    assertTrue(state.isReturnCheck)
    assertFalse(state.isLoadingPhotos)
    assertEquals(3, state.pickupPhotos.size)
    assertEquals(listOf(HandoverPhase.PICKUP), state.pickupPhotos.map { it.phase }.distinct())
    assertEquals(4, state.photos.size)
    assertEquals(listOf(HandoverPhase.RETURN), state.photos.map { it.phase }.distinct())
    assertFalse(state.canAddPhoto)
  }

  @Test
  fun theLenderMovesToTheCheckLiveOnceTheReturnIsScanned() {
    val live = LiveRental(rental.copy(status = RentalStatus.ACTIVE), rentalRepository)
    signInAsLender()
    handoverRepository.photos = checkPhotos()
    val vm = viewModel(rentals = live)
    assertFalse(vm.uiState.value.isReturnCheck)
    assertEquals(emptyList<HandoverPhoto>(), vm.uiState.value.pickupPhotos)

    live.live.value = rental.copy(status = RentalStatus.RETURNED)
    val state = vm.uiState.value
    assertTrue(state.isReturnCheck)
    assertEquals(3, state.pickupPhotos.size)
    assertEquals(4, state.photos.size)
  }

  @Test
  fun theBorrowerOfAReturnedItemOnlyWaitsAndReadsNoPhotos() {
    useStatus(RentalStatus.RETURNED)
    handoverRepository.photos = checkPhotos()
    val state = viewModel().uiState.value

    assertTrue(state.isReturned)
    assertFalse(state.isReturnCheck)
    assertFalse(state.canSendCheck)
    assertFalse(state.isLoadingPhotos)
    assertEquals(emptyList<HandoverPhoto>(), state.photos)
  }

  @Test
  fun theChecksPhotosAreLoadingUntilBothPhasesAreRead() {
    useStatus(RentalStatus.RETURNED)
    signInAsLender()
    handoverRepository.photos = checkPhotos()
    val pickup = MutableSharedFlow<List<HandoverPhoto>>(replay = 1)
    // The return photos are read at once, the pickup ones only when the test sends them.
    val slowPickup =
        object : HandoverRepository by handoverRepository {
          override fun observeMyPhotos(rentalId: String, phase: HandoverPhase) =
              if (phase == HandoverPhase.PICKUP) pickup
              else handoverRepository.observeMyPhotos(rentalId, phase)
        }
    val vm = viewModel(handover = slowPickup)
    assertTrue(vm.uiState.value.isLoadingPhotos)
    assertEquals(4, vm.uiState.value.photos.size)

    pickup.tryEmit(emptyList())
    assertFalse(vm.uiState.value.isLoadingPhotos)
  }

  @Test
  fun itemIsFineShowsItsSpinnerThenIsSent() {
    val gate = CompletableDeferred<Unit>()
    handoverRepository.gate = gate
    val vm = lenderCheck()
    assertTrue(vm.uiState.value.canSendCheck)

    vm.confirmItemFine()
    assertEquals(ReturnCheck.ITEM_FINE, vm.uiState.value.sendingCheck)
    assertFalse(vm.uiState.value.canSendCheck)
    vm.flagDamage() // ignored while the first answer is sent
    assertEquals(emptyList<Any>(), disputeRepository.disputes)

    gate.complete(Unit)
    assertNull(vm.uiState.value.sendingCheck)
    assertEquals(ReturnCheck.ITEM_FINE, vm.uiState.value.checkSent)
    assertEquals(listOf(rental.id), handoverRepository.confirmedReturns)
  }

  @Test
  fun theCheckStaysWithItsPhotosWhileItsAnswerIsSentEvenIfTheRentalMovesOn() {
    val live = LiveRental(rental.copy(status = RentalStatus.RETURNED), rentalRepository)
    signInAsLender()
    handoverRepository.photos = photos(3, takenBy = "owner")
    val gate = CompletableDeferred<Unit>()
    handoverRepository.gate = gate
    val vm = viewModel(rentals = live)

    vm.confirmItemFine()
    live.live.value = rental.copy(status = RentalStatus.CLOSED) // before the answer's result
    assertTrue(vm.uiState.value.isReturnCheck)
    assertEquals(3, vm.uiState.value.pickupPhotos.size)

    gate.complete(Unit)
    assertTrue(vm.uiState.value.isReturnCheck)
    assertEquals(ReturnCheck.ITEM_FINE, vm.uiState.value.checkSent)
  }

  @Test
  fun aRentalReloadedWhileTheAnswerIsSentLetsNoSecondAnswerGo() {
    val live = LiveRental(rental.copy(status = RentalStatus.RETURNED), rentalRepository)
    signInAsLender()
    val gate = CompletableDeferred<Unit>()
    handoverRepository.gate = gate
    val vm = viewModel(rentals = live)

    vm.confirmItemFine()
    live.error.value = RuntimeException("listener broke")
    assertTrue(vm.uiState.value.loadFailed)
    assertEquals(ReturnCheck.ITEM_FINE, vm.uiState.value.sendingCheck)

    live.error.value = null
    vm.retry()
    assertFalse(vm.uiState.value.canSendCheck)
    vm.flagDamage() // the first answer is still being sent
    assertEquals(emptyList<Any>(), disputeRepository.disputes)

    gate.complete(Unit)
    assertEquals(ReturnCheck.ITEM_FINE, vm.uiState.value.checkSent)
  }

  @Test
  fun photosThatFailedOnTheLendersCheckAreRetriedForBothPhases() {
    handoverRepository.failure = RuntimeException("down")
    handoverRepository.photos = checkPhotos()
    val vm = lenderCheck()
    assertTrue(vm.uiState.value.photosFailed)
    assertFalse(vm.uiState.value.isLoadingPhotos)

    handoverRepository.failure = null
    vm.retry()
    assertFalse(vm.uiState.value.photosFailed)
    assertEquals(3, vm.uiState.value.pickupPhotos.size)
    assertEquals(4, vm.uiState.value.photos.size)
  }

  @Test
  fun aFailedAnswerShowsAnErrorAndAnotherCanBeSent() {
    val vm = lenderCheck()
    disputeRepository.failure = RuntimeException("down")

    vm.flagDamage()
    assertTrue(vm.uiState.value.checkFailed)
    assertNull(vm.uiState.value.checkSent)
    assertTrue(vm.uiState.value.canSendCheck)

    disputeRepository.failure = null
    vm.flagDamage()
    assertFalse(vm.uiState.value.checkFailed)
    assertEquals(ReturnCheck.DAMAGE_FLAGGED, vm.uiState.value.checkSent)
    assertEquals(DisputeStage.SETTLE, disputeRepository.disputes.single().stage)
  }

  @Test
  fun aFailedAnswerKeepsItsErrorWhenTheRentalIsReloaded() {
    val live = LiveRental(rental.copy(status = RentalStatus.RETURNED), rentalRepository)
    signInAsLender()
    val vm = viewModel(rentals = live)
    disputeRepository.failure = RuntimeException("down")

    vm.flagDamage()
    live.live.value = rental.copy(status = RentalStatus.RETURNED, lastMessage = "Any news?")
    assertTrue(vm.uiState.value.checkFailed)
    assertTrue(vm.uiState.value.canSendCheck)
  }

  @Test
  fun noAnswerIsSentOfflineByTheBorrowerOrTwice() {
    useStatus(RentalStatus.RETURNED)
    viewModel().confirmItemFine() // the borrower

    val vm = lenderCheck()
    connectivity.setOnline(false)
    vm.confirmItemFine()
    assertEquals(emptyList<String>(), handoverRepository.confirmedReturns)

    connectivity.setOnline(true)
    vm.confirmItemFine()
    vm.flagDamage() // already answered
    assertEquals(listOf(rental.id), handoverRepository.confirmedReturns)
    assertEquals(emptyList<Any>(), disputeRepository.disputes)
  }

  // ---------- factory ----------

  private fun factory() =
      HandoverViewModel.Factory(
          rental.id,
          rentalRepository,
          handoverRepository,
          disputeRepository,
          authRepository,
          connectivity,
          serverClock(rentalRepository),
      )

  // ---------- the QR code ----------

  /** Signs in the lender, who shows the code at pickup. */
  private fun signInLender() {
    authRepository.signedInUser = AuthUser(uid = "owner", email = "mara@epfl.ch", name = "Mara")
    handoverRepository.currentUid = "owner"
  }

  @Test
  fun theLenderShowsTheCodeOnceTheyHaveThreePhotos() {
    signInLender()
    handoverRepository.photos = photos(2, takenBy = "owner")
    val vm = viewModel()
    val state = vm.uiState.value
    assertTrue(state.showsCode)
    assertEquals("secret-code", state.code)
    assertNull(state.codeFingerprint)
    assertFalse(state.canShowCode) // 2 photos only
    assertFalse(state.canScan)

    handoverRepository.photos = photos(3, takenBy = "owner")
    assertTrue(vm.uiState.value.canShowCode)
    assertFalse(vm.uiState.value.canScan) // the lender never scans at pickup
  }

  @Test
  fun theBorrowerScansOnceTheyHaveThreePhotos() {
    handoverRepository.photos = photos(2)
    val vm = viewModel()
    val state = vm.uiState.value
    assertFalse(state.showsCode)
    assertNull(state.code) // the borrower never gets the code itself
    assertEquals(HandoverCode.fingerprint("secret-code"), state.codeFingerprint)
    assertFalse(state.canScan)

    handoverRepository.photos = photos(3)
    assertTrue(vm.uiState.value.canScan)
    assertFalse(vm.uiState.value.canShowCode)
  }

  @Test
  fun theLendersPhotosDoNotCountForTheBorrower() {
    handoverRepository.photos = photos(3, takenBy = "owner")
    assertFalse(viewModel().uiState.value.canScan)
  }

  @Test
  fun neitherTheCodeNorTheScanAreAvailableOffThePickupDay() {
    handoverRepository.photos = photos(3) + photos(3, takenBy = "owner")
    for (day in listOf(pickupDay.minusDays(1), pickupDay.plusDays(1))) {
      rentalRepository.serverNow = noonOn(day)
      assertFalse(viewModel().uiState.value.canScan)
      signInLender()
      assertFalse(viewModel().uiState.value.canShowCode)
      authRepository.signedInUser = borrower
      handoverRepository.currentUid = "borrower"
    }
  }

  @Test
  fun offlineTheCodeStaysShownButTheScanIsDisabled() {
    handoverRepository.photos = photos(3) + photos(3, takenBy = "owner")
    val borrowerVm = viewModel()
    signInLender()
    val lenderVm = viewModel()

    connectivity.setOnline(false)
    assertFalse(borrowerVm.uiState.value.canScan)
    assertTrue(lenderVm.uiState.value.canShowCode)

    connectivity.setOnline(true)
    assertTrue(borrowerVm.uiState.value.canScan)
  }

  @Test
  fun aCodeNotMadeYetIsReportedLikeAFailure() {
    handoverRepository.code = null
    handoverRepository.photos = photos(3) + photos(3, takenBy = "owner")
    val borrowerState = viewModel().uiState.value
    assertNull(borrowerState.codeFingerprint)
    assertTrue(borrowerState.codeFailed)
    assertFalse(borrowerState.canScan)

    signInLender()
    val lenderState = viewModel().uiState.value
    assertNull(lenderState.code)
    assertTrue(lenderState.codeFailed)
    assertFalse(lenderState.canShowCode)
  }

  @Test
  fun retryReadsACodeMadeSince() {
    handoverRepository.code = null
    handoverRepository.photos = photos(3, takenBy = "owner")
    signInLender()
    val vm = viewModel()

    handoverRepository.code = "made-since"
    vm.retry()

    assertEquals("made-since", vm.uiState.value.code)
    assertFalse(vm.uiState.value.codeFailed)
    assertTrue(vm.uiState.value.canShowCode)
  }

  @Test
  fun aCodeThatCannotBeReadIsReportedAndCanBeRetried() {
    handoverRepository.codeFailure = RuntimeException("down")
    handoverRepository.photos = photos(3)
    val vm = viewModel()
    assertTrue(vm.uiState.value.codeFailed)
    assertFalse(vm.uiState.value.canScan)

    handoverRepository.codeFailure = null
    vm.retry()
    assertFalse(vm.uiState.value.codeFailed)
    assertTrue(vm.uiState.value.canScan)
  }

  @Test
  fun retryReadsAgainBothThePhotosAndTheCodeWhenBothFailed() {
    handoverRepository.failure = RuntimeException("photos down")
    handoverRepository.codeFailure = RuntimeException("code down")
    handoverRepository.photos = photos(3)
    val vm = viewModel()
    assertTrue(vm.uiState.value.photosFailed)
    assertTrue(vm.uiState.value.codeFailed)

    handoverRepository.failure = null
    handoverRepository.codeFailure = null
    vm.retry()
    assertFalse(vm.uiState.value.photosFailed)
    assertFalse(vm.uiState.value.codeFailed)
    assertTrue(vm.uiState.value.canScan)
  }

  @Test
  fun aCodeStillReadForThePickupDoesNotLandOnceTheRentalIsCancelled() {
    val lateFingerprint = CompletableDeferred<Unit>()
    val handover =
        object : HandoverRepository by handoverRepository {
          override suspend fun getCodeFingerprint(
              rentalId: String,
              phase: HandoverPhase,
          ): Result<String?> {
            lateFingerprint.await()
            return Result.failure(IOException("late"))
          }
        }
    val live = liveRental()
    val vm = viewModel(rentals = live, handover = handover)

    live.live.value = rental.copy(status = RentalStatus.CANCELLED)
    assertNull(vm.uiState.value.phase)

    lateFingerprint.complete(Unit)
    assertFalse(vm.uiState.value.codeFailed)
  }

  @Test
  fun noCodeIsReadAtReturnYet() {
    useStatus(RentalStatus.ACTIVE)
    rentalRepository.serverNow = noonOn(returnDay)
    handoverRepository.codeFailure = RuntimeException("would be reported if read")
    val vm = viewModel()
    vm.retry()

    assertEquals(HandoverPhase.RETURN, vm.uiState.value.phase)
    assertFalse(vm.uiState.value.codeFailed)
    assertNull(vm.uiState.value.codeFingerprint)
  }

  @Test
  fun aLoadedCodeIsNotReadAgainOnRetry() {
    handoverRepository.failure = RuntimeException("photos down")
    val vm = viewModel()
    handoverRepository.codeFailure = RuntimeException("would be reported if read again")
    vm.retry()
    assertFalse(vm.uiState.value.codeFailed)
  }

  @Test
  fun noCodeIsReadWhenTheRentalTakesNone() {
    rentalRepository = FakeRentalRepository(listOf(rental.copy(status = RentalStatus.CLOSED)))
    handoverRepository.codeFailure = RuntimeException("would be reported if read")
    val state = viewModel().uiState.value

    assertFalse(state.codeFailed)
    assertFalse(state.showsCode)
    assertFalse(state.canScan)
  }

  @Test
  fun theRolesSwapAtReturn() {
    val pickup = HandoverUiState(phase = HandoverPhase.PICKUP, isLender = true)
    val atReturn = pickup.copy(phase = HandoverPhase.RETURN)
    assertTrue(pickup.showsCode)
    assertFalse(atReturn.showsCode)
    assertTrue(atReturn.copy(isLender = false).showsCode)
  }

  // ---------- scanning ----------

  /** The borrower's ViewModel with 3 photos, ready to scan. */
  private fun readyToScan(): HandoverViewModel {
    handoverRepository.photos = photos(3)
    return viewModel().also { assertTrue(it.uiState.value.canScan) }
  }

  @Test
  fun theRightCodeIsSentAndConfirmed() {
    val vm = readyToScan()
    vm.onCodeScanned("secret-code")

    val state = vm.uiState.value
    assertEquals(listOf("secret-code"), handoverRepository.scannedCodes)
    assertTrue(state.scanConfirmed)
    assertNull(state.scanError)
    assertFalse(state.isSendingScan)
    assertFalse(state.canScan) // done: nothing more to scan
  }

  @Test
  fun aWrongCodeIsRefusedOnThePhoneWithoutBeingSent() {
    val vm = readyToScan()
    vm.onCodeScanned("not-the-code")

    assertEquals(ScanError.WRONG_CODE, vm.uiState.value.scanError)
    assertTrue(handoverRepository.scannedCodes.isEmpty())
    assertTrue(vm.uiState.value.canScan) // can try again
  }

  @Test
  fun anEmptyScanIsAWrongCode() {
    val vm = readyToScan()
    vm.onCodeScanned("")

    assertEquals(ScanError.WRONG_CODE, vm.uiState.value.scanError)
    assertTrue(handoverRepository.scannedCodes.isEmpty())
  }

  @Test
  fun whileSendingScanIsDisabledAndASecondScanIsIgnored() {
    val gate = CompletableDeferred<Unit>()
    handoverRepository.gate = gate
    val vm = readyToScan()
    vm.onCodeScanned("secret-code")
    assertTrue(vm.uiState.value.isSendingScan)
    assertFalse(vm.uiState.value.canScan)

    vm.onCodeScanned("secret-code")
    assertEquals(1, handoverRepository.scannedCodes.size)

    gate.complete(Unit)
    assertFalse(vm.uiState.value.isSendingScan)
    assertTrue(vm.uiState.value.scanConfirmed)
  }

  @Test
  fun aCodeTheServerRefusesIsAWrongCode() {
    val vm = readyToScan()
    // The phone's fingerprint is out of date: the server knows another code now.
    handoverRepository.code = "new-code"
    vm.onCodeScanned("secret-code")

    assertEquals(ScanError.WRONG_CODE, vm.uiState.value.scanError)
    assertFalse(vm.uiState.value.scanConfirmed)
  }

  @Test
  fun aRentalThatChangedIsReported() {
    handoverRepository.scanFailure = RentalChangedException()
    val vm = readyToScan()
    vm.onCodeScanned("secret-code")

    assertEquals(ScanError.RENTAL_CHANGED, vm.uiState.value.scanError)
    assertFalse(vm.uiState.value.scanConfirmed)
  }

  @Test
  fun aScanThatCannotBeSentCanBeScannedAgain() {
    handoverRepository.scanFailure = RuntimeException("connection dropped")
    val vm = readyToScan()
    vm.onCodeScanned("secret-code")
    assertEquals(ScanError.NOT_SENT, vm.uiState.value.scanError)
    assertTrue(vm.uiState.value.canScan)

    handoverRepository.scanFailure = null
    vm.onCodeScanned("secret-code")
    assertNull(vm.uiState.value.scanError) // a new scan clears the old error
    assertTrue(vm.uiState.value.scanConfirmed)
  }

  @Test
  fun aScanIsIgnoredWhenScanCannotBePressed() {
    handoverRepository.photos = photos(2)
    viewModel().onCodeScanned("secret-code") // 2 photos only
    signInLender()
    handoverRepository.photos = photos(3, takenBy = "owner")
    viewModel().onCodeScanned("secret-code") // the lender shows, never scans
    authRepository.signedInUser = borrower
    handoverRepository.currentUid = "borrower"
    handoverRepository.photos = photos(3)
    connectivity.setOnline(false)
    viewModel().onCodeScanned("secret-code") // offline

    assertTrue(handoverRepository.scannedCodes.isEmpty())
  }

  @Test
  fun aScanIsIgnoredOnceConfirmed() {
    val vm = readyToScan()
    vm.onCodeScanned("secret-code")
    vm.onCodeScanned("secret-code")

    assertEquals(1, handoverRepository.scannedCodes.size)
  }

  @Test
  fun aScanIsIgnoredWithoutAFingerprint() {
    handoverRepository.code = null
    handoverRepository.photos = photos(3)
    val vm = viewModel()
    vm.onCodeScanned("secret-code")

    assertTrue(handoverRepository.scannedCodes.isEmpty())
    assertNull(vm.uiState.value.scanError)
  }

  @Test
  fun factoryCreatesTheViewModelForTheRental() {
    val vm = factory().create(HandoverViewModel::class.java)

    assertEquals("Camping tent", vm.uiState.value.itemTitle)
  }

  @Test(expected = IllegalArgumentException::class)
  fun factoryRejectsOtherViewModels() {
    factory().create(OtherViewModel::class.java)
  }

  private class OtherViewModel : ViewModel()

  private companion object {
    const val HALF_A_DAY_MS = 12 * 60 * 60_000L
    const val HOUR_MS = 60 * 60_000L
  }
}
