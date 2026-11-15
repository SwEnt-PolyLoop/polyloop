// Made with Claude.

package com.swent.polyloop.ui.handover

import androidx.lifecycle.ViewModel
import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.auth.FakeAuthRepository
import com.swent.polyloop.model.connectivity.FakeConnectivityRepository
import com.swent.polyloop.model.handover.FakeHandoverRepository
import com.swent.polyloop.model.handover.FakeHandoverRepository.StoredPhoto
import com.swent.polyloop.model.handover.HandoverPhase
import com.swent.polyloop.model.handover.HandoverPhoto
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
          endDate = pickupDay.plusDays(2),
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
  private lateinit var authRepository: FakeAuthRepository
  private lateinit var connectivity: FakeConnectivityRepository
  private val deleted = mutableListOf<String>()

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
    connectivity = FakeConnectivityRepository()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun viewModel(
      id: String = rental.id,
      repository: RentalRepository = rentalRepository,
  ) =
      HandoverViewModel(
          id,
          repository,
          handoverRepository,
          authRepository,
          connectivity,
          serverClock(repository),
          deletePhoto = { deleted += it },
      )

  private fun serverClock(repository: RentalRepository) =
      ServerClock(
          repository,
          InMemoryServerClockStore(),
          elapsedRealtime = { 0L },
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

  @Test
  fun theBorrowerSeesTheItemTheLenderAndThePickupPhaseOnceLoaded() {
    val gate = CompletableDeferred<Unit>()
    rentalRepository.gate = gate
    val vm = viewModel()
    assertTrue(vm.uiState.value.isLoading)

    gate.complete(Unit)
    val state = vm.uiState.value
    assertFalse(state.isLoading)
    assertFalse(state.isLender)
    assertEquals("Camping tent", state.itemTitle)
    assertEquals("Mara", state.otherPersonName)
    assertEquals(HandoverPhase.PICKUP, state.phase)
    assertEquals(pickupDay, state.pickupDate)
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
  fun aRentalThatIsNotAcceptedTakesNoPhotosAndDoesNotReadTheServersDay() {
    rentalRepository = FakeRentalRepository(listOf(rental.copy(status = RentalStatus.ACTIVE)))
    connectivity.setOnline(false)
    val vm = viewModel()
    connectivity.setOnline(true)

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
  fun aMissingRentalIsNotFoundAndNobodySignedInFails() {
    assertTrue(viewModel(id = "unknown").uiState.value.notFound)

    authRepository.signedInUser = null
    assertTrue(viewModel().uiState.value.loadFailed)
  }

  @Test
  fun aFailedReadIsRetriedButALoadedRentalIsNotReadAgain() {
    rentalRepository.failure = RuntimeException("offline")
    val vm = viewModel()
    assertTrue(vm.uiState.value.loadFailed)

    rentalRepository.failure = null
    vm.retry()
    assertEquals("Camping tent", vm.uiState.value.itemTitle)

    rentalRepository.gate = CompletableDeferred() // a new read would hang on it
    vm.retry()
    assertFalse(vm.uiState.value.isLoading)
  }

  // ---------- photos ----------

  /** [count] pickup photos of the rental by [takenBy], one second apart. */
  private fun photos(count: Int, takenBy: String = "borrower", rentalId: String = rental.id) =
      List(count) {
        val photo =
            HandoverPhoto(
                "$takenBy$it",
                HandoverPhase.PICKUP,
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
    rentalRepository = FakeRentalRepository(listOf(rental.copy(status = RentalStatus.ACTIVE)))
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
  fun photosAreRefusedAndDeletedOffThePickupDayAtTwentyOrWhileUploading() {
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

    assertEquals(listOf("late", "full", "second"), deleted)
    assertEquals(1, handoverRepository.addPhotoCalls)
  }

  @Test
  fun aRentalThatTakesNoPhotosRefusesThem() {
    rentalRepository = FakeRentalRepository(listOf(rental.copy(status = RentalStatus.ACTIVE)))
    viewModel().addPhoto("file1")

    assertEquals(listOf("file1"), deleted)
    assertEquals(0, handoverRepository.addPhotoCalls)
  }

  // ---------- factory ----------

  private fun factory() =
      HandoverViewModel.Factory(
          rental.id,
          rentalRepository,
          handoverRepository,
          authRepository,
          connectivity,
          serverClock(rentalRepository),
      )

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
}
