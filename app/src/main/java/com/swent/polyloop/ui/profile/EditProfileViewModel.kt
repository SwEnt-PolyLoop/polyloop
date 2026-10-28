// Made with Claude.

package com.swent.polyloop.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swent.polyloop.model.auth.AuthRepository
import com.swent.polyloop.model.profile.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State of the Edit profile screen, where the signed-in user changes their name and photo.
 *
 * @property name the name as typed; trimmed when saved.
 * @property photoUrl the saved photo's URL, "" when there is none.
 * @property newPhotoUri the photo just taken with the in-app camera, not uploaded yet; null when
 *   the photo is not being changed. The screen shows it instead of [photoUrl].
 * @property loadFailed true when the profile could not be loaded (or nobody is signed in); the
 *   screen offers to retry.
 * @property nameError true when Save was pressed with a blank name; cleared by typing.
 * @property saveFailed true when saving failed; the edits are kept so Save can be pressed again.
 * @property isSaved true once saved; the screen then goes back to the profile.
 */
data class EditProfileUiState(
    val name: String = "",
    val photoUrl: String = "",
    val newPhotoUri: String? = null,
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val isCameraOpen: Boolean = false,
    val isSaving: Boolean = false,
    val nameError: Boolean = false,
    val saveFailed: Boolean = false,
    val isSaved: Boolean = false,
) {
  /** True when Save can be pressed: the profile is loaded and nothing is being saved. */
  val canSave: Boolean
    get() = !isLoading && !loadFailed && !isSaving && !isSaved
}

/**
 * Edits the signed-in user's profile: loads it once, then saves the name and, if one was taken, a
 * new photo (uploaded first, then saved with the name).
 */
class EditProfileViewModel(
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(EditProfileUiState())
  val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()

  // The photo uploaded by a Save whose profile update then failed, as (photo uri, its URL). The
  // upload already made the old URL stop working, so a retry saves this URL instead of uploading
  // the same photo again.
  private var uploadedPhoto: Pair<String, String>? = null

  init {
    load()
  }

  /** Loads the profile again, after it failed to load. */
  fun retry() = load()

  fun onNameChange(name: String) = _uiState.update {
    it.copy(name = name, nameError = false, saveFailed = false)
  }

  fun openCamera() = _uiState.update { if (it.canSave) it.copy(isCameraOpen = true) else it }

  /** Closes the camera without changing the photo. */
  fun closeCamera() = _uiState.update { it.copy(isCameraOpen = false) }

  /**
   * Keeps the photo taken at [uri] to upload on Save, replacing any earlier one, and closes the
   * camera.
   */
  fun onPhotoTaken(uri: String) = _uiState.update {
    it.copy(newPhotoUri = uri, isCameraOpen = false, saveFailed = false)
  }

  /**
   * Saves the trimmed name and, if a new photo was taken, uploads it first and saves its URL. A
   * photo already uploaded by an earlier Save that failed afterwards is not uploaded again. A blank
   * name is not saved and shows [EditProfileUiState.nameError]. Ignored while loading or saving.
   */
  fun save() {
    val form = _uiState.value
    if (!form.canSave) return
    val name = form.name.trim()
    if (name.isEmpty()) {
      _uiState.update { it.copy(nameError = true) }
      return
    }
    val uid = authRepository.getCurrentUser()?.uid
    if (uid == null) {
      _uiState.update { it.copy(saveFailed = true) }
      return
    }
    _uiState.update { it.copy(isSaving = true, saveFailed = false) }
    viewModelScope.launch {
      // 1. Upload the new photo, if one was taken and not uploaded yet; otherwise keep the saved
      //    one.
      val newPhotoUri = form.newPhotoUri
      val alreadyUploaded = uploadedPhoto?.takeIf { it.first == newPhotoUri }?.second
      val photoUrl =
          when {
            newPhotoUri == null -> form.photoUrl
            alreadyUploaded != null -> alreadyUploaded
            else ->
                profileRepository.uploadProfilePhoto(uid, newPhotoUri).getOrElse {
                  onSaveFailed()
                  return@launch
                }
          }
      if (newPhotoUri != null) uploadedPhoto = newPhotoUri to photoUrl
      // 2. Save the name and the photo's URL in the profile.
      profileRepository
          .updateProfile(uid, name, photoUrl)
          .onSuccess {
            _uiState.update {
              it.copy(
                  name = name,
                  photoUrl = photoUrl,
                  newPhotoUri = null,
                  isSaving = false,
                  isSaved = true,
              )
            }
          }
          .onFailure { onSaveFailed() }
    }
  }

  /** Keeps the edits, so Save can be pressed again. */
  private fun onSaveFailed() = _uiState.update { it.copy(isSaving = false, saveFailed = true) }

  private fun load() {
    _uiState.update { it.copy(isLoading = true, loadFailed = false) }
    val uid = authRepository.getCurrentUser()?.uid
    if (uid == null) {
      _uiState.update { it.copy(isLoading = false, loadFailed = true) }
      return
    }
    viewModelScope.launch {
      val profile = profileRepository.getProfile(uid).getOrNull()
      _uiState.update {
        if (profile == null) it.copy(isLoading = false, loadFailed = true)
        else it.copy(name = profile.name, photoUrl = profile.photoUrl, isLoading = false)
      }
    }
  }

  /** Builds the ViewModel with its dependencies (manual constructor injection). */
  class Factory(
      private val profileRepository: ProfileRepository,
      private val authRepository: AuthRepository,
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      require(modelClass.isAssignableFrom(EditProfileViewModel::class.java)) {
        "Unknown ViewModel class: ${modelClass.name}"
      }
      @Suppress("UNCHECKED_CAST")
      return EditProfileViewModel(profileRepository, authRepository) as T
    }
  }
}
