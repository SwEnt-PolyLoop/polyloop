// Made with Claude.

package com.swent.polyloop.ui.profile

import androidx.lifecycle.ViewModel
import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.auth.FakeAuthRepository
import com.swent.polyloop.model.profile.FakeProfileRepository
import com.swent.polyloop.model.profile.UserProfile
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
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditProfileViewModelTest {

  private val oldPhoto = "https://example.com/old.jpg"
  private val me =
      UserProfile(uid = "me", name = "John", email = "john@epfl.ch", photoUrl = oldPhoto)
  private val newPhoto = "file:///data/listing_photos/new.jpg"
  private val failure = IllegalStateException("offline")

  private lateinit var profiles: FakeProfileRepository
  private lateinit var auth: FakeAuthRepository

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    profiles = FakeProfileRepository(listOf(me))
    auth = FakeAuthRepository(signedInUser = AuthUser(uid = "me", email = me.email, name = "John"))
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun viewModel() = EditProfileViewModel(profiles, auth)

  private fun savedProfile(): UserProfile = profiles.profiles.single { it.uid == "me" }

  // ---------- loading ----------

  @Test
  fun loadsTheNameAndThePhoto() {
    val state = viewModel().uiState.value

    assertEquals("John", state.name)
    assertEquals(oldPhoto, state.photoUrl)
    assertNull(state.newPhotoUri)
    assertFalse(state.isLoading)
    assertFalse(state.loadFailed)
    assertTrue(state.canSave)
  }

  @Test
  fun isLoadingUntilTheProfileArrives() {
    val gate = CompletableDeferred<Unit>()
    profiles.gate = gate

    val vm = viewModel()

    assertTrue(vm.uiState.value.isLoading)
    assertFalse(vm.uiState.value.canSave)
    gate.complete(Unit)
    assertFalse(vm.uiState.value.isLoading)
    assertEquals("John", vm.uiState.value.name)
  }

  @Test
  fun nobodySignedInFailsToLoad() {
    auth.signedInUser = null

    val state = viewModel().uiState.value

    assertTrue(state.loadFailed)
    assertFalse(state.isLoading)
    assertFalse(state.canSave)
  }

  @Test
  fun aMissingProfileFailsToLoad() {
    profiles = FakeProfileRepository()

    assertTrue(viewModel().uiState.value.loadFailed)
  }

  @Test
  fun aReadErrorFailsToLoadAndRetryLoadsIt() {
    profiles.failure = failure
    val vm = viewModel()
    assertTrue(vm.uiState.value.loadFailed)

    profiles.failure = null
    vm.retry()

    assertFalse(vm.uiState.value.loadFailed)
    assertEquals("John", vm.uiState.value.name)
  }

  // ---------- name ----------

  @Test
  fun typingChangesTheNameAndClearsTheErrors() {
    val vm = viewModel()
    vm.onNameChange(" ")
    vm.save()
    assertTrue(vm.uiState.value.nameError)

    vm.onNameChange("Johnny")

    assertEquals("Johnny", vm.uiState.value.name)
    assertFalse(vm.uiState.value.nameError)
    assertFalse(vm.uiState.value.saveFailed)
  }

  // ---------- camera ----------

  @Test
  fun aPhotoTakenIsKeptAndClosesTheCamera() {
    val vm = viewModel()
    vm.openCamera()
    assertTrue(vm.uiState.value.isCameraOpen)

    vm.onPhotoTaken(newPhoto)

    assertEquals(newPhoto, vm.uiState.value.newPhotoUri)
    assertFalse(vm.uiState.value.isCameraOpen)
    // Nothing is uploaded before Save.
    assertTrue(profiles.uploadedPhotos.isEmpty())
  }

  @Test
  fun aSecondPhotoReplacesTheFirst() {
    val vm = viewModel()
    vm.onPhotoTaken("file:///first.jpg")

    vm.onPhotoTaken(newPhoto)

    assertEquals(newPhoto, vm.uiState.value.newPhotoUri)
  }

  @Test
  fun closingTheCameraKeepsThePhoto() {
    val vm = viewModel()
    vm.onPhotoTaken(newPhoto)
    vm.openCamera()

    vm.closeCamera()

    assertFalse(vm.uiState.value.isCameraOpen)
    assertEquals(newPhoto, vm.uiState.value.newPhotoUri)
  }

  @Test
  fun theCameraDoesNotOpenBeforeTheProfileIsLoaded() {
    profiles.failure = failure
    val vm = viewModel()

    vm.openCamera()

    assertFalse(vm.uiState.value.isCameraOpen)
  }

  // ---------- saving ----------

  @Test
  fun savingANewNameKeepsTheOldPhotoWithoutUploading() {
    val vm = viewModel()
    vm.onNameChange("Johnny")

    vm.save()

    assertEquals(me.copy(name = "Johnny"), savedProfile())
    assertTrue(profiles.uploadedPhotos.isEmpty())
    assertTrue(vm.uiState.value.isSaved)
    assertFalse(vm.uiState.value.isSaving)
  }

  @Test
  fun theNameIsTrimmedWhenSaved() {
    val vm = viewModel()
    vm.onNameChange("  Johnny  ")

    vm.save()

    assertEquals("Johnny", savedProfile().name)
    assertEquals("Johnny", vm.uiState.value.name)
  }

  @Test
  fun aNewPhotoIsUploadedThenSavedWithTheName() {
    val vm = viewModel()
    vm.onPhotoTaken(newPhoto)

    vm.save()

    val url = FakeProfileRepository.uploadedUrl(newPhoto)
    assertEquals(listOf("me" to newPhoto), profiles.uploadedPhotos)
    assertEquals(me.copy(photoUrl = url), savedProfile())
    assertEquals(url, vm.uiState.value.photoUrl)
    assertNull(vm.uiState.value.newPhotoUri)
    assertTrue(vm.uiState.value.isSaved)
  }

  @Test
  fun aProfileWithoutPhotoGetsItsFirstPhoto() {
    profiles = FakeProfileRepository(listOf(me.copy(photoUrl = "")))
    val vm = viewModel()
    assertEquals("", vm.uiState.value.photoUrl)
    vm.onPhotoTaken(newPhoto)

    vm.save()

    assertEquals(FakeProfileRepository.uploadedUrl(newPhoto), savedProfile().photoUrl)
  }

  @Test
  fun aBlankNameIsNotSaved() {
    val vm = viewModel()
    vm.onPhotoTaken(newPhoto)

    for (blank in listOf("", "   ", "\n\t")) {
      vm.onNameChange(blank)
      vm.save()

      assertTrue(vm.uiState.value.nameError)
      assertFalse(vm.uiState.value.isSaving)
      assertFalse(vm.uiState.value.isSaved)
    }
    assertEquals(me, savedProfile())
    assertTrue(profiles.uploadedPhotos.isEmpty())
  }

  @Test
  fun showsSavingUntilTheSaveEnds() {
    val vm = viewModel()
    val gate = CompletableDeferred<Unit>()
    profiles.gate = gate

    vm.save()

    assertTrue(vm.uiState.value.isSaving)
    assertFalse(vm.uiState.value.canSave)
    gate.complete(Unit)
    assertFalse(vm.uiState.value.isSaving)
    assertTrue(vm.uiState.value.isSaved)
  }

  @Test
  fun pressingSaveTwiceSavesOnce() {
    val vm = viewModel()
    vm.onPhotoTaken(newPhoto)
    val gate = CompletableDeferred<Unit>()
    profiles.gate = gate

    vm.save()
    vm.save()
    gate.complete(Unit)

    assertEquals(1, profiles.uploadedPhotos.size)
    assertTrue(vm.uiState.value.isSaved)
  }

  @Test
  fun aFailedUploadSavesNothingAndKeepsTheEdits() {
    val vm = viewModel()
    vm.onNameChange("Johnny")
    vm.onPhotoTaken(newPhoto)
    profiles.uploadFailure = failure

    vm.save()

    val state = vm.uiState.value
    assertTrue(state.saveFailed)
    assertFalse(state.isSaving)
    assertFalse(state.isSaved)
    assertEquals("Johnny", state.name)
    assertEquals(newPhoto, state.newPhotoUri)
    // The name is not saved without its photo.
    assertEquals(me, savedProfile())
  }

  @Test
  fun aFailedProfileUpdateKeepsTheEdits() {
    val vm = viewModel()
    vm.onNameChange("Johnny")
    profiles.failure = failure

    vm.save()

    assertTrue(vm.uiState.value.saveFailed)
    assertFalse(vm.uiState.value.isSaved)
    assertEquals("Johnny", vm.uiState.value.name)
  }

  @Test
  fun saveWorksAgainAfterAFailure() {
    val vm = viewModel()
    vm.onPhotoTaken(newPhoto)
    profiles.uploadFailure = failure
    vm.save()

    profiles.uploadFailure = null
    vm.save()

    assertFalse(vm.uiState.value.saveFailed)
    assertTrue(vm.uiState.value.isSaved)
    assertEquals(FakeProfileRepository.uploadedUrl(newPhoto), savedProfile().photoUrl)
  }

  @Test
  fun takingAPhotoClearsTheSaveError() {
    val vm = viewModel()
    profiles.failure = failure
    vm.save()

    vm.onPhotoTaken(newPhoto)

    assertFalse(vm.uiState.value.saveFailed)
  }

  @Test
  fun signedOutBeforeSavingFailsWithoutSaving() {
    val vm = viewModel()
    vm.onNameChange("Johnny")
    auth.signedInUser = null

    vm.save()

    assertTrue(vm.uiState.value.saveFailed)
    assertEquals(me, savedProfile())
  }

  @Test
  fun saveIsIgnoredWhenTheProfileFailedToLoad() {
    auth.signedInUser = null
    val vm = viewModel()
    vm.onNameChange("Johnny")
    auth.signedInUser = AuthUser(uid = "me", email = me.email, name = "John")

    vm.save()

    assertFalse(vm.uiState.value.isSaved)
    assertEquals(me, savedProfile())
  }

  @Test
  fun saveIsIgnoredOnceSaved() {
    val vm = viewModel()
    vm.onNameChange("Johnny")
    vm.save()
    vm.onNameChange("Jo")

    vm.save()

    assertEquals("Johnny", savedProfile().name)
  }

  // ---------- factory ----------

  @Test
  fun factoryCreatesTheViewModel() {
    val vm = EditProfileViewModel.Factory(profiles, auth).create(EditProfileViewModel::class.java)

    assertEquals("John", vm.uiState.value.name)
  }

  @Test
  fun factoryRejectsOtherViewModels() {
    class Other : ViewModel()

    assertThrows(IllegalArgumentException::class.java) {
      EditProfileViewModel.Factory(profiles, auth).create(Other::class.java)
    }
  }
}
