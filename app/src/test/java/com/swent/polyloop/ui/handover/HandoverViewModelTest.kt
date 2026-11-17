// Made with Claude.

package com.swent.polyloop.ui.handover

import androidx.lifecycle.ViewModel
import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.auth.FakeAuthRepository
import com.swent.polyloop.model.connectivity.FakeConnectivityRepository
import com.swent.polyloop.model.handover.FakeHandoverRepository
import com.swent.polyloop.model.handover.FakeHandoverRepository.StoredPhoto
import com.swent.polyloop.model.handover.HandoverCode
import com.swent.polyloop.model.handover.HandoverPhase
import com.swent.polyloop.model.handover.HandoverPhoto
import com.swent.polyloop.model.handover.RentalChangedException
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
  private lateinit var connectivity: FakeConnectivityRepository
  private var today = pickupDay
  private val deleted = mutableListOf<String>()

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    rentalRepository = FakeRentalRepository(rentals = listOf(rental))
    handoverRepository = FakeHandoverRepository(currentUid = "borrower")
    authRepository = FakeAuthRepository(signedInUser = borrower)
    connectivity = FakeConnectivityRepository()
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
          connectivity,
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

  @Test
  fun aFailureWhileFollowingKeepsTheRentalShown() {
    val vm = viewModel()
    rentalRepository.failure = RuntimeException("listener lost")
    rentalRepository.setStatus(rental.id, RentalStatus.ACTIVE)

    val state = vm.uiState.value
    assertFalse(state.loadFailed)
    assertFalse(state.isPickedUp)
    assertEquals("Camping tent", state.itemTitle)
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
      HandoverViewModel.Factory(
          rental.id,
          rentalRepository,
          handoverRepository,
          authRepository,
          connectivity,
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
      today = day
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
  fun withoutACodeNothingIsShownOrScanned() {
    handoverRepository.code = null
    handoverRepository.photos = photos(3) + photos(3, takenBy = "owner")
    val borrowerState = viewModel().uiState.value
    assertNull(borrowerState.codeFingerprint)
    assertFalse(borrowerState.codeFailed)
    assertFalse(borrowerState.canScan)

    signInLender()
    val lenderState = viewModel().uiState.value
    assertNull(lenderState.code)
    assertFalse(lenderState.canShowCode)
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
  fun aLoadedCodeIsNotReadAgainOnRetry() {
    handoverRepository.failure = RuntimeException("photos down")
    val vm = viewModel()
    handoverRepository.codeFailure = RuntimeException("would be reported if read again")
    vm.retry()
    assertFalse(vm.uiState.value.codeFailed)
  }

  @Test
  fun noCodeIsReadWhenTheRentalTakesNone() {
    rentalRepository = FakeRentalRepository(listOf(rental.copy(status = RentalStatus.ACTIVE)))
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
}
