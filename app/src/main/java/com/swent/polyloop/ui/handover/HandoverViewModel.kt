// Made with Claude.

package com.swent.polyloop.ui.handover

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swent.polyloop.model.auth.AuthRepository
import com.swent.polyloop.model.connectivity.ConnectivityRepository
import com.swent.polyloop.model.dispute.DisputeRepository
import com.swent.polyloop.model.handover.HandoverCode
import com.swent.polyloop.model.handover.HandoverPhase
import com.swent.polyloop.model.handover.HandoverPhoto
import com.swent.polyloop.model.handover.HandoverRepository
import com.swent.polyloop.model.handover.RentalChangedException
import com.swent.polyloop.model.handover.ScanRefusedException
import com.swent.polyloop.model.handover.WrongCodeException
import com.swent.polyloop.model.rental.Rental
import com.swent.polyloop.model.rental.RentalRepository
import com.swent.polyloop.model.rental.RentalStatus
import com.swent.polyloop.model.rental.ServerClock
import com.swent.polyloop.ui.createlisting.deletePhotoFile
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
  /**
   * The server refused the scan for a reason it does not name (e.g. not the pickup day by its
   * clock, or the price cannot be paid); scanning again only helps once that is fixed.
   */
  REFUSED,
  /** The rental can no longer take the scan, e.g. it was cancelled meanwhile. */
  RENTAL_CHANGED,
  /** Google's scanner could not open, e.g. while it is being downloaded; scanning again retries. */
  SCANNER_UNAVAILABLE,
}

/** The photo at [uri] whose upload failed, taken in [phase], the only phase it can be sent in. */
data class FailedPhoto(val uri: String, val phase: HandoverPhase)

/** The lender's answer once the item is back: it came back fine, or it is damaged. */
enum class ReturnCheck {
  ITEM_FINE,
  DAMAGE_FLAGGED,
}

/**
 * State of the Handover screen for one rental, as seen by the signed-in user.
 *
 * @property loadFailed true when the rental could not be read, is missing from the phone's cache
 *   while offline, or nobody is signed in.
 * @property notFound true when there is no such rental, as the phone can tell only online.
 * @property otherPersonName the borrower for the lender, the owner for the borrower; can be "".
 * @property phase the phase photos are taken in, or null when the rental's status takes none.
 * @property pickupDate the rental's first day, the only day the pickup can be done.
 * @property returnDate the rental's last day, from which the item can be returned.
 * @property serverToday today by the server's clock, read only when photos are taken in [phase];
 *   null until it is read, when it is unknown (the server was not reached since the phone booted),
 *   and from midnight on once the rental no longer takes photos. A pickup done keeps its day, which
 *   is not read again.
 * @property isReadingServerToday true while [serverToday] is being read, so the screen can tell a
 *   read still running from an unknown day.
 * @property isDayOver true once the end of [serverToday] is noticed (at midnight, or when the
 *   screen comes back after the phone slept past it) until the new day is read: the day stays
 *   shown, so its note does not blink, but no photo, QR code or scan is taken on it any more.
 * @property isReturned true once the return was scanned and the lender has not checked the item.
 * @property deposit the deposit accepted, in PolyPoints; 0 when it is not known.
 * @property lateFees the late fees already taken from [deposit], in PolyPoints.
 * @property photos the photos the signed-in user took in [phase], oldest first; on the lender's
 *   check of a returned item, their return photos.
 * @property pickupPhotos on the lender's check of a returned item, their pickup photos, oldest
 *   first; empty otherwise.
 * @property isLoadingPhotos true until the photos followed are read for the first time; only the
 *   lender's check shows it.
 * @property photosFailed true when the photos could not be loaded.
 * @property failedPhoto the photo whose upload failed, kept to try again; null when none did.
 * @property isOffline true while the phone has no connection; no photo can be uploaded then.
 * @property code the secret code the user shows as a QR code, when they show it (see [showsCode]);
 *   null until read.
 * @property codeFingerprint the fingerprint of the code the user scans, when they scan; null until
 *   read.
 * @property codeFailed true when the code or its fingerprint could not be read, or does not exist
 *   yet; [HandoverViewModel.retry] reads it again, and so does the phone coming back online.
 * @property isSendingScan true while a scanned code is being sent to the server.
 * @property scanConfirmed true once the server confirmed the scan.
 * @property scanError why the last scan was not confirmed, or null.
 * @property isPickedUp true once the rental is picked up (ACTIVE or later) while the screen shows
 *   its pickup, seen live on both phones: no more photos, no QR and no scan. A screen opened on a
 *   picked-up rental shows its return instead.
 * @property sendingCheck the lender's answer being sent, or null when none is.
 * @property checkFailed true when the lender's last answer could not be sent.
 * @property checkSent the lender's answer once it was sent, so it is never sent twice.
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
    val returnDate: LocalDate? = null,
    val serverToday: LocalDate? = null,
    val isReadingServerToday: Boolean = false,
    val isDayOver: Boolean = false,
    val isReturned: Boolean = false,
    val deposit: Int = 0,
    val lateFees: Int = 0,
    val photos: List<HandoverPhoto> = emptyList(),
    val pickupPhotos: List<HandoverPhoto> = emptyList(),
    val isLoadingPhotos: Boolean = false,
    val photosFailed: Boolean = false,
    val isUploading: Boolean = false,
    val failedPhoto: FailedPhoto? = null,
    val isOffline: Boolean = false,
    val code: String? = null,
    val codeFingerprint: String? = null,
    val codeFailed: Boolean = false,
    val isSendingScan: Boolean = false,
    val scanConfirmed: Boolean = false,
    val scanError: ScanError? = null,
    val isPickedUp: Boolean = false,
    val sendingCheck: ReturnCheck? = null,
    val checkFailed: Boolean = false,
    val checkSent: ReturnCheck? = null,
) {
  /** The file of [failedPhoto], or null when no upload failed. */
  val failedPhotoUri: String?
    get() = failedPhoto?.uri

  /** What is left of the deposit once the late fees are taken from it, never below 0. */
  val depositHeld: Int
    get() = (deposit - lateFees).coerceAtLeast(0)

  /**
   * True on the pickup day by the server's clock, never by the phone's, whose date the user can
   * change. False while the server's day is unknown.
   */
  val isPickupDay: Boolean
    get() = pickupDate != null && pickupDate == serverToday

  /**
   * True from the rental's last day on, by the server's clock, never by the phone's. False while
   * the server's day is unknown.
   */
  val isReturnDay: Boolean
    get() = returnDate != null && serverToday != null && !serverToday.isBefore(returnDate)

  /** How many more photos are needed before the handover can go on; 0 once there are enough. */
  val photosNeeded: Int
    get() = (HandoverPhoto.MIN_PHOTOS - photos.size).coerceAtLeast(0)

  /** True on the day photos of [phase] can be taken: the pickup day, or from the return day on. */
  val isHandoverDay: Boolean
    get() =
        when (phase) {
          HandoverPhase.PICKUP -> isPickupDay
          HandoverPhase.RETURN -> isReturnDay
          null -> false
        }

  /**
   * True when a photo can be uploaded: on the handover day and not past its end, before the pickup
   * is confirmed, online, under 20, none uploading.
   */
  val canUpload: Boolean
    get() =
        isHandoverDay &&
            !isDayOver &&
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

  /**
   * True when the QR code is shown: on the handover day and not past its end, before the pickup is
   * confirmed, once the user has 3 photos and the code.
   */
  val canShowCode: Boolean
    get() =
        showsCode && isHandoverDay && !isDayOver && !isPickedUp && photosNeeded == 0 && code != null

  /**
   * True when "Scan QR code" can be pressed: on the handover day and not past its end, once the
   * user has 3 photos and the fingerprint, online until scans can wait in the upload queue, and not
   * while a scan is being sent, once one is confirmed or the rental is picked up, or once the
   * server said the rental changed.
   */
  val canScan: Boolean
    get() =
        phase != null &&
            !showsCode &&
            isHandoverDay &&
            !isDayOver &&
            photosNeeded == 0 &&
            codeFingerprint != null &&
            !isOffline &&
            !isSendingScan &&
            !scanConfirmed &&
            scanError != ScanError.RENTAL_CHANGED &&
            !isPickedUp

  /**
   * True when the lender checks the returned item; the borrower only waits for them. It stays true
   * while their answer is sent, once it is, and once it failed, even if the rental moves on
   * meanwhile: an answer the server applied but whose reply was lost moves the rental on, and the
   * lender then sees the error and sends it again, which the server accepts as already done.
   */
  val isReturnCheck: Boolean
    get() = isLender && (isReturned || sendingCheck != null || checkSent != null || checkFailed)

  /** True when the lender can answer: online, with no answer being sent or already sent. */
  val canSendCheck: Boolean
    get() = isReturnCheck && !isOffline && sendingCheck == null && checkSent == null
}

/**
 * Shows the handover of the rental [rentalId], followed live: a screen showing the pickup sees it
 * confirmed by the scan (or the rental cancelled) on both phones, and one showing the return moves
 * on by itself when the return is scanned. It uploads the photos taken, reads the code to show or
 * the fingerprint of the one to scan, sends the scanned code, and sends the lender's answer once
 * the item is back. When the rental takes photos, it reads today from [serverClock], so that photos
 * cannot be taken early by changing the phone's date; an unknown day is read again when the phone
 * comes back online, and online with [retryServerToday]. A known day is read again at midnight in
 * Zurich, by the server's time, so a screen left open does not keep yesterday; [elapsedRealtime]
 * (the time since boot, sleep included) tells [retryServerToday] whether that midnight passed while
 * the phone slept, which stops the wait. [deletePhoto] deletes a photo file: once it is uploaded or
 * refused, when its shot failed, or when a photo that failed to upload can no longer be sent or the
 * screen is left.
 */
class HandoverViewModel(
    private val rentalId: String,
    private val rentalRepository: RentalRepository,
    private val handoverRepository: HandoverRepository,
    private val disputeRepository: DisputeRepository,
    private val authRepository: AuthRepository,
    private val connectivityRepository: ConnectivityRepository,
    private val serverClock: ServerClock,
    private val deletePhoto: (uri: String) -> Unit = ::deletePhotoFile,
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) : ViewModel() {

  private val _uiState = MutableStateFlow(HandoverUiState())
  val uiState: StateFlow<HandoverUiState> = _uiState.asStateFlow()

  private var rentalJob: Job? = null
  private var photosJob: Job? = null
  private var codeJob: Job? = null
  private var serverTodayRead: Job? = null
  private var midnightRead: Job? = null

  // The phase whose photos are followed; null when none.
  private var shownPhase: HandoverPhase? = null

  // True while the photos of the lender's check are followed.
  private var showsCheck = false

  // When the server's day shown ends, in [elapsedRealtime] time; null while the day is unknown.
  private var dayEndsAt: Long? = null

  // True once the pickup was shown, kept by a reload so a pickup done stays on screen after it.
  private var showedPickup = false

  init {
    viewModelScope.launch {
      var wasOnline: Boolean? = null
      connectivityRepository.isOnline.collect { online ->
        _uiState.update { it.copy(isOffline = !online) }
        // Connecting loads again a rental that could not be read, and reads an unknown day,
        // which the note asks to connect for.
        if (online && wasOnline == false) {
          if (_uiState.value.loadFailed) load()
          retryServerToday()
        }
        wasOnline = online
        // Back online: a code that could not be read (e.g. no copy on the phone) is read again,
        // so nobody has to press Retry.
        val state = _uiState.value
        if (online && state.codeFailed) state.phase?.let { loadCode(it, state.showsCode) }
      }
    }
    load()
  }

  /**
   * Reads today from [serverClock] for the handover, unless a read is already running. A known day
   * stays shown while it is read again, so its note does not blink.
   */
  private fun readServerToday() {
    if (serverTodayRead?.isActive == true) return
    if (_uiState.value.serverToday == null) _uiState.update { it.copy(isReadingServerToday = true) }
    serverTodayRead = viewModelScope.launch {
      val now = serverClock.now()
      val today = now?.let { ServerClock.dayOf(it) }
      _uiState.update {
        it.copy(serverToday = today, isReadingServerToday = false, isDayOver = false)
      }
      dropStaleFailedPhoto()
      if (now != null) readAgainAtMidnight(now) else forgetDayEnd()
    }
  }

  /**
   * Reads the day again at the next midnight in Zurich after the server's time [now], so a phone
   * whose clock is wrong still reads it once. The wait is counted on [elapsedRealtime], which the
   * user cannot change (see [waitForDayEnd]).
   */
  private fun readAgainAtMidnight(now: Instant) {
    val midnight = ServerClock.dayOf(now).plusDays(1).atStartOfDay(ServerClock.ZURICH).toInstant()
    dayEndsAt = elapsedRealtime() + Duration.between(now, midnight).toMillis()
    waitForDayEnd()
  }

  /**
   * Waits until [dayEndsAt], then reads the day again; a rental that no longer takes photos needs
   * no day, so it is forgotten instead, and read again if photos are taken again. A pickup done
   * keeps its day, so the screen still shows it as done, not as a pickup on another day. [delay]
   * stops while the phone sleeps, so a sleep shifts the wait: [retryServerToday] starts it again
   * with the time really left when the screen comes back.
   */
  private fun waitForDayEnd() {
    val endsAt = dayEndsAt ?: return
    midnightRead?.cancel()
    midnightRead = viewModelScope.launch {
      delay(endsAt - elapsedRealtime())
      if (_uiState.value.isPickedUp) return@launch
      if (_uiState.value.phase != null) {
        endDay()
        readServerToday()
      } else {
        dayEndsAt = null
        _uiState.update { it.copy(serverToday = null) }
      }
    }
  }

  /**
   * Marks the day as over. A scan message from that day goes with it, so a message shown afterwards
   * can only be the answer to a scan still being sent at midnight, which the screen keeps showing.
   */
  private fun endDay() = _uiState.update { it.copy(isDayOver = true, scanError = null) }

  private fun forgetDayEnd() {
    midnightRead?.cancel()
    dayEndsAt = null
  }

  /**
   * Reads the server's day again when the rental takes photos and the day is unknown, e.g. after
   * the server failed to answer while the phone was online, or over: its midnight passed while the
   * phone slept, which stops the wait for it. A day not over yet is waited for again with the time
   * really left, which a sleep shifted. Called when the screen comes back to the front, and from
   * "Retry" on the note. An unknown day is not read offline, where coming back online reads it; a
   * day that is over is, as [serverClock] works it out from its last answer. A pickup done keeps
   * its day (see [waitForDayEnd]).
   */
  fun retryServerToday() {
    val state = _uiState.value
    if (state.phase == null || state.isPickedUp) return
    val isDayOver = dayEndsAt?.let { elapsedRealtime() >= it } == true
    when {
      isDayOver -> {
        endDay()
        readServerToday()
      }
      state.serverToday == null -> if (!state.isOffline) readServerToday()
      else -> waitForDayEnd()
    }
  }

  /**
   * Follows the rental again after it failed to load; otherwise follows the photos again and reads
   * the code again, each only if it failed to load.
   */
  fun retry() {
    val state = _uiState.value
    if (state.loadFailed) return load()
    if (state.photosFailed) followPhotos(shownPhase, showsCheck)
    val phase = state.phase ?: return
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
            is ScanRefusedException -> ScanError.REFUSED
            else -> ScanError.NOT_SENT
          }
      _uiState.update {
        it.copy(isSendingScan = false, scanConfirmed = failure == null, scanError = error)
      }
    }
  }

  /**
   * Reports that Google's scanner could not open, so "Scan QR code" does not look broken. Ignored
   * when Scan cannot be pressed (see [HandoverUiState.canScan]).
   */
  fun onScannerFailed() {
    if (!_uiState.value.canScan) return
    _uiState.update { it.copy(scanError = ScanError.SCANNER_UNAVAILABLE) }
  }

  /** Deletes the file the camera prepared for a shot that failed; it was never a photo. */
  fun onCaptureFailed(photoUri: String) = deletePhoto(photoUri)

  /** Tells, as the lender, that the returned item is fine, which releases the deposit. */
  fun confirmItemFine() =
      sendCheck(ReturnCheck.ITEM_FINE) { handoverRepository.confirmReturnOk(rentalId) }

  /** Tells, as the lender, that the returned item is damaged, which opens a dispute. */
  fun flagDamage() =
      sendCheck(ReturnCheck.DAMAGE_FLAGGED) { disputeRepository.openDispute(rentalId) }

  /** Sends the lender's [answer] with [send], if it can be sent now (see `canSendCheck`). */
  private fun sendCheck(answer: ReturnCheck, send: suspend () -> Result<Unit>) {
    if (!_uiState.value.canSendCheck) return
    _uiState.update { it.copy(sendingCheck = answer, checkFailed = false) }
    viewModelScope.launch {
      val sent = send().isSuccess
      _uiState.update {
        it.copy(sendingCheck = null, checkFailed = !sent, checkSent = answer.takeIf { sent })
      }
    }
  }

  private fun upload(photoUri: String) {
    val phase = _uiState.value.phase ?: return
    _uiState.update { it.copy(isUploading = true, failedPhoto = null) }
    viewModelScope.launch {
      val uploaded = handoverRepository.addPhoto(rentalId, phase, photoUri).isSuccess
      if (uploaded) deletePhoto(photoUri) // the copy in Storage is the one shown now
      _uiState.update {
        it.copy(
            isUploading = false,
            failedPhoto = FailedPhoto(photoUri, phase).takeUnless { uploaded },
        )
      }
      // A scan may have moved the rental on, or midnight the day, while it uploaded.
      dropStaleFailedPhoto()
    }
  }

  /**
   * Deletes the photo whose upload failed once it can no longer be sent: the phase shown is not the
   * one it was taken in, the pickup was confirmed (the rules refuse it), or the server's day is
   * known and photos of that phase are not taken on it. It is kept while no rental is shown
   * (loading, failed or missing) or the day is unknown, as neither tells.
   */
  private fun dropStaleFailedPhoto() {
    val state = _uiState.value
    val failed = state.failedPhoto ?: return
    if (state.isLoading || state.loadFailed || state.notFound) return
    val isOffDay = state.serverToday != null && !state.isHandoverDay
    if (failed.phase == state.phase && !state.isPickedUp && !isOffDay) return
    deletePhoto(failed.uri)
    _uiState.update { it.copy(failedPhoto = null) }
  }

  private fun load() {
    rentalJob?.cancel()
    codeJob?.cancel()
    followPhotos(null)
    show(HandoverUiState())
    val uid = authRepository.getCurrentUser()?.uid
    if (uid == null) {
      show(HandoverUiState(isLoading = false, loadFailed = true))
      return
    }
    rentalJob = viewModelScope.launch {
      rentalRepository
          .observeRental(rentalId)
          .catch {
            followPhotos(null)
            show(HandoverUiState(isLoading = false, loadFailed = true))
          }
          .collect { rental ->
            if (rental == null) {
              // Offline, the phone's cache can miss a rental that still exists: an error to retry,
              // which coming back online loads again, not a deleted rental. Either way the photo
              // waiting for a retry is kept, for a rental that comes back with its phase.
              val isOffline = _uiState.value.isOffline
              followPhotos(null)
              show(
                  HandoverUiState(isLoading = false, loadFailed = isOffline, notFound = !isOffline)
              )
            } else {
              showRental(rental, uid)
            }
          }
    }
  }

  /**
   * Shows [rental] as seen by [uid], keeping the photos, the upload and the lender's answer, and
   * follows other photos and reads another code when its status changed the phase shown. A pickup
   * shown when the rental is picked up stays on screen, marked [HandoverUiState.isPickedUp], even
   * after a reload. A photo that failed to upload and can no longer be sent is deleted.
   */
  private fun showRental(rental: Rental, uid: String) {
    val shown = _uiState.value
    val pickedUp = showedPickup && rental.status in PICKED_UP
    val phase = if (pickedUp) HandoverPhase.PICKUP else HandoverPhase.fromStatus(rental.status)
    if (phase == HandoverPhase.PICKUP) showedPickup = true
    val isLender = uid == rental.ownerId
    val phaseChanged = phase != shown.phase
    _uiState.update {
      val updated =
          it.copy(
              isLoading = false,
              loadFailed = false,
              notFound = false,
              isLender = isLender,
              itemTitle = rental.listingTitle,
              otherPersonName = rental.otherPersonName(uid),
              phase = phase,
              pickupDate = rental.startDate,
              returnDate = rental.endDate,
              isReturned = rental.status == RentalStatus.RETURNED,
              deposit = rental.deposit,
              lateFees = rental.lateFees,
              isPickedUp = pickedUp,
          )
      // The code and the scan belong to one phase.
      if (phaseChanged) updated.withoutScan() else updated
    }
    dropStaleFailedPhoto()
    if (phaseChanged) {
      // A code still being read for the phase shown before must not land in this one; loadCode
      // cancels it too, so this matters when no phase is shown any more.
      codeJob?.cancel()
      // The server makes no return code yet, so only the pickup's is read.
      if (phase == HandoverPhase.PICKUP) loadCode(phase, _uiState.value.showsCode)
      // Read after the rental is shown, not awaited with it: the Function can take seconds on a
      // cold start. Only a rental that takes photos needs the day, read when its phase is first
      // shown and kept on later changes; an unknown day is read again by retryServerToday, not on
      // each change (a chat message changes the rental too).
      if (phase != null && _uiState.value.serverToday == null) readServerToday()
    }
    val check = _uiState.value.isReturnCheck
    if (phase != shownPhase || check != showsCheck) followPhotos(phase, check)
  }

  /**
   * Shows [state], keeping what is read or followed apart: whether the phone is online, the
   * server's day, the upload running or the photo waiting for a retry, a scan being sent, and the
   * lender's answer being sent, failed or already sent, so a reload (a chat message re-emits the
   * rental) does not lose them and the answer is never sent twice.
   */
  private fun show(state: HandoverUiState) = _uiState.update {
    state.copy(
        isOffline = it.isOffline,
        serverToday = it.serverToday,
        isReadingServerToday = it.isReadingServerToday,
        isDayOver = it.isDayOver,
        isUploading = it.isUploading,
        failedPhoto = it.failedPhoto,
        isSendingScan = it.isSendingScan,
        sendingCheck = it.sendingCheck,
        checkFailed = it.checkFailed,
        checkSent = it.checkSent,
    )
  }

  /** Deletes the file of the photo waiting for a retry: once the screen is left, it is not sent. */
  override fun onCleared() {
    _uiState.value.failedPhotoUri?.let(deletePhoto)
  }

  /**
   * Follows the photos of [phase], or with [check] those of the lender's check of a returned item:
   * the pickup photos in `pickupPhotos` and the return photos in `photos`. None are followed when
   * [phase] is null and [check] is false. The photos shown before are cleared.
   */
  private fun followPhotos(phase: HandoverPhase?, check: Boolean = false) {
    photosJob?.cancel()
    shownPhase = phase
    showsCheck = check
    val phases =
        if (check) listOf(HandoverPhase.RETURN, HandoverPhase.PICKUP) else listOfNotNull(phase)
    _uiState.update {
      it.copy(
          photos = emptyList(),
          pickupPhotos = emptyList(),
          isLoadingPhotos = phases.isNotEmpty(),
          photosFailed = false,
      )
    }
    if (phases.isEmpty()) return
    photosJob = viewModelScope.launch {
      val read = mutableSetOf<HandoverPhase>()
      phases.forEach { shown ->
        launch {
          handoverRepository
              .observeMyPhotos(rentalId, shown)
              .catch { _uiState.update { it.copy(photosFailed = true, isLoadingPhotos = false) } }
              .collect { photos ->
                read += shown
                _uiState.update {
                  val withPhotos =
                      if (check && shown == HandoverPhase.PICKUP) it.copy(pickupPhotos = photos)
                      else it.copy(photos = photos)
                  withPhotos.copy(isLoadingPhotos = read.size < phases.size)
                }
              }
        }
      }
    }
  }

  /**
   * Reads the code to show if the user [shows] it, otherwise the fingerprint of the one to scan.
   */
  private fun loadCode(phase: HandoverPhase, shows: Boolean) {
    codeJob?.cancel()
    _uiState.update { it.copy(codeFailed = false) }
    codeJob = viewModelScope.launch {
      val result =
          if (shows) handoverRepository.getCodeToShow(rentalId, phase)
          else handoverRepository.getCodeFingerprint(rentalId, phase)
      // A code not made yet cannot be used either, so it is reported the same way, with Retry.
      // Since #435 the server makes the code when the offer is accepted, so only a rental accepted
      // before that has none.
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

  /** [this] without the code and the scan of the phase shown before. */
  private fun HandoverUiState.withoutScan(): HandoverUiState =
      copy(
          code = null,
          codeFingerprint = null,
          codeFailed = false,
          scanConfirmed = false,
          scanError = null,
      )

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
      private val disputeRepository: DisputeRepository,
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
          disputeRepository,
          authRepository,
          connectivityRepository,
          serverClock,
      )
          as T
    }
  }
}
