// Made with Claude.

package com.swent.polyloop.ui.handover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swent.polyloop.model.auth.AuthRepository
import com.swent.polyloop.model.handover.HandoverPhase
import com.swent.polyloop.model.handover.HandoverPhoto
import com.swent.polyloop.model.handover.HandoverRepository
import com.swent.polyloop.model.rental.RentalRepository
import java.time.LocalDate
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
 * @property isPickupDay true only on the rental's first day, the only day the pickup can be done.
 * @property deposit the deposit held, in PolyPoints.
 * @property photos the photos the signed-in user took in [phase], oldest first.
 * @property photosFailed true when the photos could not be loaded.
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
    val isPickupDay: Boolean = false,
    val deposit: Int = 0, // 0 until Rental.deposit exists (#265)
    val photos: List<HandoverPhoto> = emptyList(),
    val photosFailed: Boolean = false,
) {
  /** How many more photos are needed before the handover can go on; 0 once there are enough. */
  val photosNeeded: Int
    get() = (HandoverPhoto.MIN_PHOTOS - photos.size).coerceAtLeast(0)
}

/** Shows the handover of the rental [rentalId], read once. [today] is the current date. */
class HandoverViewModel(
    private val rentalId: String,
    private val rentalRepository: RentalRepository,
    private val handoverRepository: HandoverRepository,
    private val authRepository: AuthRepository,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {

  private val _uiState = MutableStateFlow(HandoverUiState())
  val uiState: StateFlow<HandoverUiState> = _uiState.asStateFlow()

  init {
    load()
  }

  /** Loads the rental again, after it failed to load. Ignored otherwise. */
  fun retry() {
    if (_uiState.value.loadFailed) load()
  }

  private fun load() {
    _uiState.value = HandoverUiState()
    val uid = authRepository.getCurrentUser()?.uid
    if (uid == null) {
      _uiState.value = HandoverUiState(isLoading = false, loadFailed = true)
      return
    }
    viewModelScope.launch {
      val rental =
          rentalRepository.getRental(rentalId).getOrElse {
            _uiState.value = HandoverUiState(isLoading = false, loadFailed = true)
            return@launch
          }
      if (rental == null) {
        _uiState.value = HandoverUiState(isLoading = false, notFound = true)
        return@launch
      }
      val phase = HandoverPhase.fromStatus(rental.status)
      _uiState.value =
          HandoverUiState(
              isLoading = false,
              isLender = uid == rental.ownerId,
              itemTitle = rental.listingTitle,
              otherPersonName = rental.otherPersonName(uid),
              phase = phase,
              pickupDate = rental.startDate,
              isPickupDay = rental.startDate == today(),
          )
      if (phase != null) observePhotos(phase)
    }
  }

  private fun observePhotos(phase: HandoverPhase) {
    viewModelScope.launch {
      handoverRepository
          .observeMyPhotos(rentalId, phase)
          .catch { _uiState.update { it.copy(photosFailed = true) } }
          .collect { photos -> _uiState.update { it.copy(photos = photos) } }
    }
  }
}
