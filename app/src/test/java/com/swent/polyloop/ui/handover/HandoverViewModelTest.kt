// Made with Claude.

package com.swent.polyloop.ui.handover

import androidx.lifecycle.ViewModel
import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.auth.FakeAuthRepository
import com.swent.polyloop.model.handover.FakeHandoverRepository
import com.swent.polyloop.model.handover.FakeHandoverRepository.StoredPhoto
import com.swent.polyloop.model.handover.HandoverPhase
import com.swent.polyloop.model.handover.HandoverPhoto
import com.swent.polyloop.model.rental.FakeRentalRepository
import com.swent.polyloop.model.rental.Rental
import com.swent.polyloop.model.rental.RentalStatus
import java.time.Instant
import java.time.LocalDate
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
  private var today = pickupDay
  private val deleted = mutableListOf<String>()

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    rentalRepository = FakeRentalRepository(rentals = listOf(rental))
    handoverRepository = FakeHandoverRepository(currentUid = "borrower")
    authRepository = FakeAuthRepository(signedInUser = borrower)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun viewModel(id: String = rental.id) =
      HandoverViewModel(
          id,
          rentalRepository,
          handoverRepository,
          authRepository,
          today = { today },
          deletePhoto = { deleted += it },
      )

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
    today = pickupDay.minusDays(1)
    assertFalse(viewModel().uiState.value.isPickupDay)
    today = pickupDay.plusDays(1)
    assertFalse(viewModel().uiState.value.isPickupDay)
  }

  @Test
  fun aRentalThatIsNotAcceptedTakesNoPhotos() {
    rentalRepository = FakeRentalRepository(listOf(rental.copy(status = RentalStatus.ACTIVE)))

    assertNull(viewModel().uiState.value.phase)
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
  fun aNewPhotoReplacesTheOneThatFailed() {
    val vm = viewModel()
    handoverRepository.failure = RuntimeException("offline")
    vm.addPhoto("file1")

    handoverRepository.failure = null
    vm.addPhoto("file2")
    assertEquals(listOf("file1", "file2"), deleted)
    assertEquals(listOf("file2"), handoverRepository.photos.map { it.photo.url })
  }

  @Test
  fun photosAreRefusedAndDeletedOffThePickupDayAtTwentyOrWhileUploading() {
    today = pickupDay.plusDays(1)
    viewModel().addPhoto("late")

    today = pickupDay
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
      HandoverViewModel.Factory(rental.id, rentalRepository, handoverRepository, authRepository)

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
