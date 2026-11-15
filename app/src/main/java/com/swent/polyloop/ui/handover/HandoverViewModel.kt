// Made with Claude.

package com.swent.polyloop.ui.handover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swent.polyloop.model.auth.AuthRepository
import com.swent.polyloop.model.connectivity.ConnectivityRepository
import com.swent.polyloop.model.handover.HandoverPhase
import com.swent.polyloop.model.handover.HandoverPhoto
import com.swent.polyloop.model.handover.HandoverRepository
import com.swent.polyloop.model.rental.RentalRepository
import com.swent.polyloop.model.rental.ServerClock
import com.swent.polyloop.ui.createlisting.deletePhotoFile
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State of the Handover screen for one rental, as seen by the signed-in user.
 *
 * @property loadFailed true when the rental could not be read or nobody is signed in.
 * @property notFound true when there is no such rental.
 * @property otherPersonName the borrower for the lender, the owner for the borrower; can be "".
 * @property phase the phase photos are taken in, or null when the rental's status takes none.
 * @property pickupDate the rental's first day, the only day the pickup can be done.
 * @property serverToday today by the server's clock, read only when photos are taken in [phase];
 *   null until it is read or when it is unknown (the server was not reached since the phone
 *   booted).
 * @property isReadingServerToday true while [serverToday] is being read, so the screen can tell a
 *   read still running from an unknown day.
 * @property deposit the deposit held, in PolyPoints.
 * @property photos the photos the signed-in user took in [phase], oldest first.
 * @property photosFailed true when the photos could not be loaded.
 * @property failedPhotoUri the photo whose upload failed, kept to try again; null when none did.
 * @property isOffline true while the phone has no connection; no photo can be uploaded then.
 */
data class HandoverUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val notFound: Boolean = false,
    val isLender: Boolean = false,
    val itemTitle: String = "",
    val otherPersonName: String = "",
    val phase: HandoverPhase? = null,
    val pickupDate: LocalDate? = null,
    val serverToday: LocalDate? = null,
    val isReadingServerToday: Boolean = false,
    val deposit: Int = 0,
    val photos: List<HandoverPhoto> = emptyList(),
    val photosFailed: Boolean = false,
    val isUploading: Boolean = false,
    val failedPhotoUri: String? = null,
    val isOffline: Boolean = false,
) {
  /**
   * True on the pickup day by the server's clock, never by the phone's, whose date the user can
   * change. False while the server's day is unknown.
   */
  val isPickupDay: Boolean
    get() = pickupDate != null && pickupDate == serverToday

  /** How many more photos are needed before the handover can go on; 0 once there are enough. */
  val photosNeeded: Int
    get() = (HandoverPhoto.MIN_PHOTOS - photos.size).coerceAtLeast(0)

  /** True when a photo can be uploaded: on the pickup day, online, under 20, none uploading. */
  val canUpload: Boolean
    get() =
        phase == HandoverPhase.PICKUP &&
            isPickupDay &&
            !isOffline &&
            photos.size < HandoverPhoto.MAX_PHOTOS &&
            !isUploading

  /** True when a new photo can be taken: one can be uploaded, and none is waiting for a retry. */
  val canAddPhoto: Boolean
    get() = canUpload && failedPhotoUri == null
}

/**
 * Shows the handover of the rental [rentalId], read once, and uploads the photos taken. When the
 * rental takes pickup photos, it reads today from [serverClock], so that photos cannot be taken
 * early by changing the phone's date; an unknown day is read again when the phone comes back
 * online. [deletePhoto] deletes a photo file once it is uploaded or refused.
 */
class HandoverViewModel(
    private val rentalId: String,
    private val rentalRepository: RentalRepository,
    private val handoverRepository: HandoverRepository,
    private val authRepository: AuthRepository,
    private val connectivityRepository: ConnectivityRepository,
    private val serverClock: ServerClock,
    private val deletePhoto: (uri: String) -> Unit = ::deletePhotoFile,
) : ViewModel() {

  private val _uiState = MutableStateFlow(HandoverUiState())
  val uiState: StateFlow<HandoverUiState> = _uiState.asStateFlow()

  private var serverTodayRead: Job? = null

  init {
    viewModelScope.launch {
      var wasOnline: Boolean? = null
      connectivityRepository.isOnline.collect { online ->
        _uiState.update { it.copy(isOffline = !online) }
        // The note asks to connect when the day is unknown, so connecting reads it again.
        val state = _uiState.value
        if (
            online &&
                wasOnline == false &&
                state.phase == HandoverPhase.PICKUP &&
                state.serverToday == null
        ) {
          readServerToday()
        }
        wasOnline = online
      }
    }
    load()
  }

  /** Reads today from [serverClock] for the pickup, unless a read is already running. */
  private fun readServerToday() {
    if (serverTodayRead?.isActive == true) return
    _uiState.update { it.copy(isReadingServerToday = true) }
    serverTodayRead = viewModelScope.launch {
      val today = serverClock.today()
      _uiState.update { it.copy(serverToday = today, isReadingServerToday = false) }
    }
  }

  /**
   * Loads the rental again after it failed to load, or follows the photos again after they failed
   * to load. Ignored otherwise.
   */
  fun retry() {
    val state = _uiState.value
    when {
      state.loadFailed -> load()
      state.photosFailed -> state.phase?.let(::observePhotos)
    }
  }

  /**
   * Uploads the photo just taken at [photoUri]. A photo that cannot be added (see
   * [HandoverUiState.canAddPhoto]) is deleted.
   */
  fun addPhoto(photoUri: String) {
    if (_uiState.value.canAddPhoto) upload(photoUri) else deletePhoto(photoUri)
  }

  /** Uploads again the photo whose upload failed. */
  fun retryUpload() {
    val uri = _uiState.value.failedPhotoUri ?: return
    if (_uiState.value.canUpload) upload(uri)
  }

  /** Deletes the file the camera prepared for a shot that failed; it was never a photo. */
  fun onCaptureFailed(photoUri: String) = deletePhoto(photoUri)

  private fun upload(photoUri: String) {
    val phase = _uiState.value.phase ?: return
    _uiState.update { it.copy(isUploading = true, failedPhotoUri = null) }
    viewModelScope.launch {
      val uploaded = handoverRepository.addPhoto(rentalId, phase, photoUri).isSuccess
      if (uploaded) deletePhoto(photoUri) // the copy in Storage is the one shown now
      _uiState.update {
        it.copy(isUploading = false, failedPhotoUri = photoUri.takeUnless { uploaded })
      }
    }
  }

  private fun load() {
    show(HandoverUiState())
    val uid = authRepository.getCurrentUser()?.uid
    if (uid == null) {
      show(HandoverUiState(isLoading = false, loadFailed = true))
      return
    }
    viewModelScope.launch {
      val rental =
          rentalRepository.getRental(rentalId).getOrElse {
            show(HandoverUiState(isLoading = false, loadFailed = true))
            return@launch
          }
      if (rental == null) {
        show(HandoverUiState(isLoading = false, notFound = true))
        return@launch
      }
      val phase = HandoverPhase.fromStatus(rental.status)
      show(
          HandoverUiState(
              isLoading = false,
              isLender = uid == rental.ownerId,
              itemTitle = rental.listingTitle,
              otherPersonName = rental.otherPersonName(uid),
              phase = phase,
              pickupDate = rental.startDate,
              deposit = rental.deposit,
          )
      )
      // Read after the rental is shown, not awaited with it: the Function can take seconds on a
      // cold start. Only pickup photos need the day.
      if (phase == HandoverPhase.PICKUP) readServerToday()
      if (phase != null) observePhotos(phase)
    }
  }

  /**
   * Shows [state], keeping whether the phone is online and the server's day, which are read apart,
   * so a retry neither loses a known day nor hides a read still running.
   */
  private fun show(state: HandoverUiState) = _uiState.update {
    state.copy(
        isOffline = it.isOffline,
        serverToday = it.serverToday,
        isReadingServerToday = it.isReadingServerToday,
    )
  }

  private fun observePhotos(phase: HandoverPhase) {
    _uiState.update { it.copy(photosFailed = false) }
    viewModelScope.launch {
      handoverRepository
          .observeMyPhotos(rentalId, phase)
          .catch { _uiState.update { it.copy(photosFailed = true) } }
          .collect { photos -> _uiState.update { it.copy(photos = photos) } }
    }
  }

  /** Builds the ViewModel with its dependencies (manual constructor injection). */
  class Factory(
      private val rentalId: String,
      private val rentalRepository: RentalRepository,
      private val handoverRepository: HandoverRepository,
      private val authRepository: AuthRepository,
      private val connectivityRepository: ConnectivityRepository,
      private val serverClock: ServerClock,
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      require(modelClass.isAssignableFrom(HandoverViewModel::class.java)) {
        "Unknown ViewModel class: ${modelClass.name}"
      }
      @Suppress("UNCHECKED_CAST")
      return HandoverViewModel(
          rentalId,
          rentalRepository,
          handoverRepository,
          authRepository,
          connectivityRepository,
          serverClock,
      )
          as T
    }
  }
}
