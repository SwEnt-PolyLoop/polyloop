// Made with Claude.

package com.swent.polyloop.ui.rentaldetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swent.polyloop.model.auth.AuthRepository
import com.swent.polyloop.model.connectivity.ConnectivityRepository
import com.swent.polyloop.model.listing.ListingRepository
import com.swent.polyloop.model.location.MapPoint
import com.swent.polyloop.model.profile.ProfileRepository
import com.swent.polyloop.model.rental.RentalRepository
import com.swent.polyloop.model.rental.RentalStatus
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State of the Rental detail screen for one rental, with raw values the screen formats itself.
 * [errorMsg] is non-null when the rental could not be read; it is blank when the failure carried no
 * message, and the screen then shows a generic text. [notFound] is true when there is no such
 * rental. [isSupported] is false for a status the screen does not handle yet (anything but
 * Requested): only [itemTitle] is then set, for the placeholder. [isLender] is true when the
 * signed-in user owns the item. [sentAt] is when the request was sent, [endDate] the return day and
 * [rentalPrice] the total price in PolyPoints. [suggestedDeposit] is the listing's item value in
 * PolyPoints, or null when the listing is missing or could not be read. [pickupPoint] is the
 * listing's public point, or null when the listing is missing, could not be read or is not geocoded
 * yet. [otherPerson] is the borrower for the lender and the owner for the borrower. [isOffline] is
 * true while the phone has no connection; the screen then shows the pickup area as a placeholder
 * instead of the map.
 */
data class RentalDetailUiState(
    val isLoading: Boolean = false,
    val errorMsg: String? = null,
    val notFound: Boolean = false,
    val isSupported: Boolean = true,
    val isLender: Boolean = false,
    val itemTitle: String = "",
    val status: RentalStatus? = null,
    val sentAt: Instant? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val rentalPrice: Int = 0,
    val suggestedDeposit: Int? = null,
    val pickupArea: String = "",
    val pickupPoint: MapPoint? = null,
    val otherPerson: OtherPerson? = null,
    val isOffline: Boolean = false,
)

/**
 * The other side of the rental, shown on the person card with their initial. [name] comes from
 * their profile, or from the copy on the rental when the profile has none; it can still be blank,
 * and the screen then shows a placeholder. [averageRating] and [reviewCount] are null when the
 * profile is missing or could not be read (the rating is unknown), while 0.0 and 0 mean the person
 * has no reviews yet.
 */
data class OtherPerson(
    val uid: String,
    val name: String,
    val averageRating: Double?,
    val reviewCount: Int?,
)

/**
 * Shows one rental, read once, as seen by the signed-in user. Only Requested is handled so far. It
 * follows the network status from [connectivityRepository], so the screen knows when to replace the
 * map.
 */
class RentalDetailViewModel(
    private val rentalId: String,
    private val rentalRepository: RentalRepository,
    private val listingRepository: ListingRepository,
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
    private val connectivityRepository: ConnectivityRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(RentalDetailUiState())
  val uiState: StateFlow<RentalDetailUiState> = _uiState.asStateFlow()

  private var loading: Job? = null

  init {
    load()
    viewModelScope.launch {
      connectivityRepository.isOnline.collect { online ->
        _uiState.update { it.copy(isOffline = !online) }
      }
    }
  }

  /** Loads the rental again, e.g. after an error. */
  fun retry() = load()

  private fun load() {
    // A retry while an earlier load is still running would otherwise let the older, slower result
    // overwrite the newer one.
    loading?.cancel()
    _uiState.update { it.copy(isLoading = true, errorMsg = null, notFound = false) }
    loading = viewModelScope.launch {
      val rental =
          rentalRepository.getRental(rentalId).getOrElse { e ->
            _uiState.update { it.copy(isLoading = false, errorMsg = e.message ?: "") }
            return@launch
          }
      if (rental == null) {
        _uiState.update { it.copy(isLoading = false, notFound = true) }
        return@launch
      }
      if (rental.status != RentalStatus.REQUESTED) {
        // Only Requested is built so far: the screen shows a placeholder with the title until the
        // other statuses are, so everything else stays at its default and neither the listing nor
        // the other person's profile is read.
        _uiState.update {
          RentalDetailUiState(
              isSupported = false,
              itemTitle = rental.listingTitle,
              isOffline = it.isOffline,
          )
        }
        return@launch
      }
      val uid = authRepository.getCurrentUser()?.uid.orEmpty()
      val isLender = uid == rental.ownerId
      val otherUid = if (isLender) rental.borrowerId else rental.ownerId
      // Only the rental is required: a missing or unreadable listing or profile falls back (no
      // deposit, the name copied on the rental) instead of failing the screen. The two reads need
      // nothing from each other, so they run at the same time; both are awaited before isLoading
      // turns false, so the screen does not flash a half-filled card.
      val listingRead = async { listingRepository.getListing(rental.listingId).getOrNull() }
      val profileRead = async { profileRepository.getProfile(otherUid).getOrNull() }
      val listing = listingRead.await()
      val profile = profileRead.await()
      val otherPerson =
          OtherPerson(
              uid = otherUid,
              name = profile?.name.orEmpty().ifBlank { rental.otherPersonName(uid) },
              averageRating = profile?.averageRating,
              reviewCount = profile?.reviewCount,
          )
      _uiState.update {
        RentalDetailUiState(
            isLender = isLender,
            itemTitle = rental.listingTitle,
            status = rental.status,
            sentAt = rental.createdAt,
            startDate = rental.startDate,
            endDate = rental.endDate,
            rentalPrice = rental.totalPrice,
            suggestedDeposit = listing?.itemValue,
            pickupArea = rental.pickupArea,
            pickupPoint = listing?.pickupPoint,
            otherPerson = otherPerson,
            isOffline = it.isOffline,
        )
      }
    }
  }

  /** Builds the ViewModel with its dependencies (manual constructor injection). */
  class Factory(
      private val rentalId: String,
      private val rentalRepository: RentalRepository,
      private val listingRepository: ListingRepository,
      private val profileRepository: ProfileRepository,
      private val authRepository: AuthRepository,
      private val connectivityRepository: ConnectivityRepository,
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      require(modelClass.isAssignableFrom(RentalDetailViewModel::class.java)) {
        "Unknown ViewModel class: ${modelClass.name}"
      }
      @Suppress("UNCHECKED_CAST")
      return RentalDetailViewModel(
          rentalId,
          rentalRepository,
          listingRepository,
          profileRepository,
          authRepository,
          connectivityRepository,
      )
          as T
    }
  }
}
