// Made with Claude.

package com.swent.polyloop.ui.handover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swent.polyloop.model.auth.AuthRepository
import com.swent.polyloop.model.connectivity.ConnectivityRepository
import com.swent.polyloop.model.handover.HandoverCode
import com.swent.polyloop.model.handover.HandoverPhase
import com.swent.polyloop.model.handover.HandoverPhoto
import com.swent.polyloop.model.handover.HandoverRepository
import com.swent.polyloop.model.handover.RentalChangedException
import com.swent.polyloop.model.handover.WrongCodeException
import com.swent.polyloop.model.rental.Rental
import com.swent.polyloop.model.rental.RentalRepository
import com.swent.polyloop.model.rental.RentalStatus
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

/** Why a scanned code was not confirmed. */
enum class ScanError {
  /** The code is not the one of this rental and phase. */
  WRONG_CODE,
  /** The scan could not be sent (e.g. the connection dropped); scanning again sends it again. */
  NOT_SENT,
  /** The rental can no longer take the scan, e.g. it was cancelled meanwhile. */
  RENTAL_CHANGED,
}

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
 * @property code the secret code the user shows as a QR code, when they show it (see [showsCode]);
 *   null until read.
 * @property codeFingerprint the fingerprint of the code the user scans, when they scan; null until
 *   read.
 * @property codeFailed true when the code or its fingerprint could not be read, or does not exist
 *   yet; [HandoverViewModel.retry] reads it again.
 * @property isSendingScan true while a scanned code is being sent to the server.
 * @property scanConfirmed true once the server confirmed the scan.
 * @property scanError why the last scan was not confirmed, or null.
 * @property isPickedUp true once the rental is picked up (ACTIVE or later), seen live on both
 *   phones while the screen is open: no more photos, no QR and no scan.
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
    val code: String? = null,
    val codeFingerprint: String? = null,
    val codeFailed: Boolean = false,
    val isSendingScan: Boolean = false,
    val scanConfirmed: Boolean = false,
    val scanError: ScanError? = null,
    val isPickedUp: Boolean = false,
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
            !isPickedUp &&
            !isOffline &&
            photos.size < HandoverPhoto.MAX_PHOTOS &&
            !isUploading

  /** True when a new photo can be taken: one can be uploaded, and none is waiting for a retry. */
  val canAddPhoto: Boolean
    get() = canUpload && failedPhotoUri == null

  /**
   * True when the user shows the QR code in [phase]: the person giving the item, so the lender at
   * pickup and the borrower at return. The other person scans it.
   */
  val showsCode: Boolean
    get() = phase != null && isLender == (phase == HandoverPhase.PICKUP)

  /** True when the QR code is shown: on the pickup day, once the user has 3 photos and the code. */
  val canShowCode: Boolean
    get() = showsCode && isPickupDay && !isPickedUp && photosNeeded == 0 && code != null

  /**
   * True when "Scan QR code" can be pressed: on the pickup day, once the user has 3 photos and the
   * fingerprint, online until scans can wait in the upload queue, and not while a scan is being
   * sent, once one is confirmed or the rental is picked up, or once the server said the rental
   * changed.
   */
  val canScan: Boolean
    get() =
        phase != null &&
            !showsCode &&
            isPickupDay &&
            photosNeeded == 0 &&
            codeFingerprint != null &&
            !isOffline &&
            !isSendingScan &&
            !scanConfirmed &&
            scanError != ScanError.RENTAL_CHANGED &&
            !isPickedUp
}

/**
 * Shows the handover of the rental [rentalId], then follows it live: uploads the photos taken,
 * reads the code to show or the fingerprint of the one to scan, and sends the scanned code. When
 * the rental takes pickup photos, it reads today from [serverClock], so that photos cannot be taken
 * early by changing the phone's date; an unknown day is read again when the phone comes back
 * online, and online with [retryServerToday]. [deletePhoto] deletes a photo file once it is
 * uploaded or refused.
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
  private var followJob: Job? = null
  val uiState: StateFlow<HandoverUiState> = _uiState.asStateFlow()

  private var serverTodayRead: Job? = null

  init {
    viewModelScope.launch {
      var wasOnline: Boolean? = null
      connectivityRepository.isOnline.collect { online ->
        _uiState.update { it.copy(isOffline = !online) }
        // The note asks to connect when the day is unknown, so connecting reads it again.
        if (online && wasOnline == false) retryServerToday()
        wasOnline = online
        // Back online: a code that could not be read (e.g. no copy on the phone) is read again,
        // so nobody has to press Retry.
        val state = _uiState.value
        if (online && state.codeFailed) state.phase?.let { loadCode(it, state.showsCode) }
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
   * Reads the server's day again when it is unknown and the rental takes pickup photos, e.g. after
   * the server failed to answer while the phone was online: when the screen comes back to the
   * front, and from "Retry" on the note. Ignored offline: coming back online reads it.
   */
  fun retryServerToday() {
    val state = _uiState.value
    if (state.phase == HandoverPhase.PICKUP && state.serverToday == null && !state.isOffline) {
      readServerToday()
    }
  }

  /**
   * Loads the rental again after it failed to load; otherwise follows the photos again and reads
   * the code again, each only if it failed to load.
   */
  fun retry() {
    val state = _uiState.value
    if (state.loadFailed) return load()
    val phase = state.phase ?: return
    if (state.photosFailed) observePhotos(phase)
    if (state.codeFailed) loadCode(phase, state.showsCode)
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

  /**
   * Checks the [code] the user scanned against its fingerprint, then sends it to the server. A
   * wrong code is refused on the phone and not sent. Ignored when Scan cannot be pressed (see
   * [HandoverUiState.canScan]).
   */
  fun onCodeScanned(code: String) {
    val state = _uiState.value
    val phase = state.phase ?: return
    val fingerprint = state.codeFingerprint ?: return
    if (!state.canScan) return
    if (!HandoverCode.matches(code, fingerprint)) {
      _uiState.update { it.copy(scanError = ScanError.WRONG_CODE) }
      return
    }
    _uiState.update { it.copy(isSendingScan = true, scanError = null) }
    viewModelScope.launch {
      val failure = handoverRepository.confirmScan(rentalId, phase, code).exceptionOrNull()
      val error =
          when (failure) {
            null -> null
            is WrongCodeException -> ScanError.WRONG_CODE
            is RentalChangedException -> ScanError.RENTAL_CHANGED
            else -> ScanError.NOT_SENT
          }
      _uiState.update {
        it.copy(isSendingScan = false, scanConfirmed = failure == null, scanError = error)
      }
    }
  }

  /** Deletes the file the camera prepared for a shot that failed; it was never a photo. */
  fun onCaptureFailed(photoUri: String) = deletePhoto(photoUri)

  private fun upload(photoUri: String) {
    val phase = _uiState.value.phase ?: return
    _uiState.update { it.copy(isUploading = true, failedPhotoUri = null) }
    viewModelScope.launch {
      val uploaded = handoverRepository.addPhoto(rentalId, phase, photoUri).isSuccess
      // A failed photo is kept to try again, unless the pickup was confirmed meanwhile: it can no
      // longer be sent. Otherwise its file goes (once uploaded, the copy in Storage is shown).
      val keep = !uploaded && !_uiState.value.isPickedUp
      if (!keep) deletePhoto(photoUri)
      _uiState.update { it.copy(isUploading = false, failedPhotoUri = photoUri.takeIf { keep }) }
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
      if (phase != null) {
        observePhotos(phase)
        loadCode(phase, _uiState.value.showsCode)
      }
      followRental()
    }
  }

  /**
   * Follows the rental once it is shown, so that both phones see the pickup confirmed by the server
   * (or the rental cancelled) without leaving the screen.
   */
  private fun followRental() {
    followJob?.cancel()
    followJob = viewModelScope.launch {
      rentalRepository
          .observeRental(rentalId)
          // The rental is already shown; if following it fails, the screen keeps it as it is.
          .catch {}
          .collect { rental ->
            _uiState.update { it.withRental(rental) }
            dropFailedPhotoOncePickedUp()
          }
    }
  }

  /**
   * Once the rental is picked up, a pickup photo that failed to upload can no longer be sent (the
   * rules refuse it): its file is deleted and its retry tile goes away.
   */
  private fun dropFailedPhotoOncePickedUp() {
    val state = _uiState.value
    val failed = state.failedPhotoUri ?: return
    if (!state.isPickedUp) return
    deletePhoto(failed)
    _uiState.update { it.copy(failedPhotoUri = null) }
  }

  /** [this] after the rental changed to [rental] on the server. */
  private fun HandoverUiState.withRental(rental: Rental?): HandoverUiState =
      when {
        rental == null -> copy(notFound = true)
        phase == HandoverPhase.PICKUP && rental.status in PICKED_UP -> copy(isPickedUp = true)
        // E.g. cancelled: nothing left to hand over.
        !isPickedUp && HandoverPhase.fromStatus(rental.status) == null -> copy(phase = null)
        else -> this
      }

  /**
   * Reads the code to show if the user [shows] it, otherwise the fingerprint of the one to scan.
   */
  private fun loadCode(phase: HandoverPhase, shows: Boolean) {
    _uiState.update { it.copy(codeFailed = false) }
    viewModelScope.launch {
      val result =
          if (shows) handoverRepository.getCodeToShow(rentalId, phase)
          else handoverRepository.getCodeFingerprint(rentalId, phase)
      // A code not made yet cannot be used either, so it is reported the same way, with Retry.
      val value = result.getOrNull()
      _uiState.update {
        when {
          value == null -> it.copy(codeFailed = true)
          shows -> it.copy(code = value)
          else -> it.copy(codeFingerprint = value)
        }
      }
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

  private companion object {
    /** The statuses of a rental whose item has been picked up. */
    val PICKED_UP =
        setOf(
            RentalStatus.ACTIVE,
            RentalStatus.RETURNED,
            RentalStatus.OVERDUE,
            RentalStatus.DISPUTED,
            RentalStatus.FROZEN,
            RentalStatus.LOST,
            RentalStatus.CLOSED,
        )
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
