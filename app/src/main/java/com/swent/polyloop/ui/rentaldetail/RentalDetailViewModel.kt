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
import com.swent.polyloop.model.rental.DepositOffer
import com.swent.polyloop.model.rental.RentalRepository
import com.swent.polyloop.model.rental.RentalStatus
import com.swent.polyloop.model.rental.ServerClock
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State of the Rental detail screen for one rental, with raw values the screen formats itself.
 * [errorMsg] is non-null when the rental could not be read; it is blank when the failure carried no
 * message, and the screen then shows a generic text. [notFound] is true when there is no such
 * rental. [isSupported] is false for a status the screen does not handle yet (anything but
 * Requested, Accepted, Active and Disputed): only [itemTitle] is then set, for the placeholder.
 * [isLender] is true when the signed-in user owns the item. [sentAt] is when the request was sent,
 * [endDate] the return day and [rentalPrice] the total price in PolyPoints. [suggestedDeposit] is
 * the listing's item value in PolyPoints, or null when the listing is missing or could not be read.
 * [pickupArea] is the name copied on the rental, or the listing's if the request was sent before
 * the listing was geocoded. [pickupPoint] is the listing's public point, or null when the listing
 * is missing, could not be read or is not geocoded yet. [otherPerson] is the borrower for the
 * lender and the owner for the borrower. [isOffline] is true while the phone has no connection; the
 * screen then shows the pickup area of a request as a placeholder instead of the map. [offer] is
 * the deposit offer waiting for an answer, if any, and [isOfferMine] is true when the signed-in
 * user wrote it. [deposit] is the accepted amount in PolyPoints, and [pickupAddress] and
 * [returnAddress] are the exact addresses, set once the rental is accepted. [lateFees] is the total
 * taken from the deposit for a late return, in PolyPoints, and 0 when the return was never late.
 * [serverToday] is today by the server's clock, which Start Pickup, Start Return and the cancel
 * window of an accepted rental go by: read only for an accepted or active rental, again each time
 * the screen comes back (so a screen left open across days does not keep an old day), and null
 * until it is read or when it is unknown (the server was not reached since the phone booted).
 * [isReadingServerToday] is true while it is being read with no day known yet, so the screen can
 * tell a read still running from an unknown date; a re-read of a known day leaves it false.
 * [isCancelling] is true while a cancel is being sent, and [cancelError] is non-null when the last
 * one failed (blank when the failure carried no message, like [errorMsg]).
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
    val offer: DepositOffer? = null,
    val isOfferMine: Boolean = false,
    val deposit: Int = 0,
    val pickupAddress: String = "",
    val returnAddress: String = "",
    val lateFees: Int = 0,
    val serverToday: LocalDate? = null,
    val isReadingServerToday: Boolean = false,
    val isCancelling: Boolean = false,
    val cancelError: String? = null,
) {
  // Computed rather than stored, so they can never go out of sync with the fields they come from.

  /**
   * The last day an accepted rental can be cancelled, the day before pickup. Null for any other
   * status, a request included: it has no deadline.
   */
  val cancelUntil: LocalDate?
    get() = if (status == RentalStatus.ACCEPTED) startDate?.minusDays(1) else null

  /**
   * Whether the cancel button is enabled. Disabled offline and while a cancel is being sent.
   *
   * A request can be cancelled at any time, without the server's day: a request without an offer
   * never expires, so without the button it would stay in My rentals as a request forever.
   *
   * An accepted rental is locked from its pickup day: the 1-day buffer before it is the lender's
   * last chance to cancel the next rental, so cancelling stops the day before. That day is the
   * server's, like Start Pickup's, because the phone's date can be changed in Settings; so while it
   * is unknown or being read, the button stays disabled rather than falling back to the phone's
   * date.
   */
  val canCancel: Boolean
    get() {
      if (isOffline || isCancelling) return false
      return when (status) {
        RentalStatus.REQUESTED -> true
        RentalStatus.ACCEPTED -> {
          val today = serverToday
          val lastDay = cancelUntil
          today != null && lastDay != null && !isReadingServerToday && !today.isAfter(lastDay)
        }
        else -> false
      }
    }
}

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
 * Shows one rental as seen by the signed-in user. Requested, Accepted, Active and Disputed are
 * handled so far. The rental is read once, then followed, so that the offer and an acceptance show
 * without a reload. It follows the network status from [connectivityRepository], so the screen
 * knows when to replace the map. For an accepted or active rental, it reads today from
 * [serverClock], so that Start Pickup, Start Return and the cancel window of an accepted rental do
 * not go by the phone's date, which the user can change; a request has no deadline, so it does not
 * need that date. The screen shows without waiting for it. [cancel] asks the server to cancel a
 * requested or accepted rental. The date is read again each time the screen comes back
 * ([onScreenResumed]), and an unknown date when the phone comes back online. A requested rental
 * whose listing could not be read (e.g. opened offline, listing not cached) reads it again when the
 * phone comes back online, to fill the suggested deposit and the map point.
 */
class RentalDetailViewModel(
    private val rentalId: String,
    private val rentalRepository: RentalRepository,
    private val listingRepository: ListingRepository,
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
    private val connectivityRepository: ConnectivityRepository,
    private val serverClock: ServerClock,
) : ViewModel() {

  private val _uiState = MutableStateFlow(RentalDetailUiState())
  val uiState: StateFlow<RentalDetailUiState> = _uiState.asStateFlow()

  private var loading: Job? = null
  private var serverTodayRead: Job? = null
  private var listingReread: Job? = null
  // The listing whose read failed, so it is read again on reconnection; null once read (a missing
  // listing counts as read).
  private var failedListingId: String? = null

  init {
    load()
    viewModelScope.launch {
      var wasOnline: Boolean? = null
      connectivityRepository.isOnline.collect { online ->
        _uiState.update { it.copy(isOffline = !online) }
        // Under the grey button, an unknown date asks the user to connect, so connecting reads it
        // again.
        val state = _uiState.value
        val reconnected = online && wasOnline == false
        if (reconnected && needsServerToday(state.status) && state.serverToday == null) {
          readServerToday()
        }
        if (reconnected && state.status == RentalStatus.REQUESTED) readListingAgain()
        wasOnline = online
      }
    }
  }

  /**
   * Reads today from [serverClock] again when the screen comes back, for an accepted or active
   * rental, even when a day is already known: the ViewModel can stay in memory across days (behind
   * Handover, or with the app in the background), so a day read earlier would keep Start Pickup or
   * Start Return grey on the very day they should open, or leave cancelling open after its last
   * day. [ServerClock] works offline from its saved time, so this needs no connection. It does
   * nothing while the rental is loading (no status yet), for a status with nothing that goes by the
   * server's day, or while a read is running.
   */
  fun onScreenResumed() {
    val state = _uiState.value
    if (!needsServerToday(state.status)) return
    readServerToday(showReading = state.serverToday == null)
  }

  /**
   * Cancels the rental, if [RentalDetailUiState.canCancel]; otherwise does nothing. It does not set
   * the status itself: the rental is followed live, so it turns Cancelled once the Cloud Function
   * has run, and a call that failed never shows as Cancelled. After a success it also loads the
   * rental again (see below). A failure sets [RentalDetailUiState.cancelError], which the next
   * attempt clears.
   */
  fun cancel() {
    if (!_uiState.value.canCancel) return
    _uiState.update { it.copy(isCancelling = true, cancelError = null) }
    viewModelScope.launch {
      val error = rentalRepository.cancelRental(rentalId).exceptionOrNull()
      _uiState.update {
        it.copy(isCancelling = false, cancelError = error?.let { e -> e.message ?: "" })
      }
      // If the live updates had already failed, nothing would show the Cancelled rental, and the
      // screen would stay on Requested or Accepted with no sign that it worked. Reading the rental
      // again shows it as it is now (the placeholder) and restarts the live updates.
      if (error == null) load()
    }
  }

  /**
   * Reads today from [serverClock] for Start Pickup, Start Return or the cancel window, unless a
   * read is already running. Whatever it returns replaces the day, null included (unknown, e.g.
   * after a reboot).
   *
   * @param showReading whether [RentalDetailUiState.isReadingServerToday] turns true during the
   *   read. False when a day is already known, so the bottom button keeps its look until the new
   *   day arrives instead of flickering to the reading state and back.
   */
  private fun readServerToday(showReading: Boolean = true) {
    if (serverTodayRead?.isActive == true) return
    if (showReading) _uiState.update { it.copy(isReadingServerToday = true) }
    serverTodayRead = viewModelScope.launch {
      val today = serverClock.today()
      _uiState.update { it.copy(serverToday = today, isReadingServerToday = false) }
    }
  }

  /**
   * Reads the listing again after a failed read, unless a re-read is already running or the last
   * read succeeded. On success it fills the same fields as [load] (the suggested deposit and the
   * map point, and the pickup area only when it is still blank, so the area copied on the rental is
   * never overwritten). A missing listing (a successful read with no listing) counts as read and is
   * not tried again; it clears the suggested deposit and the map point. A failure changes nothing
   * and is retried at the next reconnection. [load] and [retry] cancel a running re-read, so an old
   * result cannot land after a reload.
   */
  private fun readListingAgain() {
    val listingId = failedListingId ?: return
    if (listingReread?.isActive == true) return
    listingReread = viewModelScope.launch {
      // A failure changes nothing; the next reconnection tries again.
      val result = listingRepository.getListing(listingId)
      if (result.isFailure) return@launch
      failedListingId = null
      // Null when the listing does not exist: that is a successful read, and it clears the fields.
      val listing = result.getOrNull()
      _uiState.update {
        it.copy(
            suggestedDeposit = listing?.itemValue,
            pickupPoint = listing?.pickupPoint,
            // Never overwrites the area copied on the rental.
            pickupArea = it.pickupArea.ifBlank { listing?.pickupArea.orEmpty() },
        )
      }
    }
  }

  /** Loads the rental again, e.g. after an error. */
  fun retry() = load()

  private fun load() {
    // A retry while an earlier load is still running would otherwise let the older, slower result
    // overwrite the newer one.
    loading?.cancel()
    // A re-read still running would otherwise land its result after the reload.
    listingReread?.cancel()
    failedListingId = null
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
      if (rental.status !in SUPPORTED_STATUSES) {
        // Only these statuses are built so far: the screen shows a placeholder with the title until
        // the
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
      val listingRead = async { listingRepository.getListing(rental.listingId) }
      val profileRead = async { profileRepository.getProfile(otherUid).getOrNull() }
      val listingResult = listingRead.await()
      val listing = listingResult.getOrNull()
      failedListingId = if (listingResult.isFailure) rental.listingId else null
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
            // A request sent before the listing was geocoded has a blank copy of the area.
            pickupArea = rental.pickupArea.ifBlank { listing?.pickupArea.orEmpty() },
            pickupPoint = listing?.pickupPoint,
            otherPerson = otherPerson,
            isOffline = it.isOffline,
            offer = rental.offer,
            isOfferMine = rental.offer?.authorId == uid,
            deposit = rental.deposit,
            pickupAddress = rental.pickupAddress,
            returnAddress = rental.returnAddress,
            lateFees = rental.lateFees,
            // Kept from before a retry, so a read still running or a date already known stays.
            serverToday = it.serverToday,
            isReadingServerToday = it.isReadingServerToday,
            // A cancel still running keeps the button disabled, so it cannot be sent twice.
            isCancelling = it.isCancelling,
        )
      }
      followRental(uid, readsServerToday = needsServerToday(rental.status))
    }
  }

  /**
   * Reads today for Start Pickup, Start Return or the cancel window when [readsServerToday], then
   * follows the rental live until the collection ends or fails.
   *
   * A failure of the live updates hides the offer rather than leaving it out of date. A rental
   * cancelled meanwhile leaves the supported statuses, so it shows the placeholder.
   *
   * @param uid the signed-in user, used to tell whether the live offer is theirs.
   * @param readsServerToday true when the rental is accepted or active (see [needsServerToday]).
   */
  private suspend fun followRental(uid: String, readsServerToday: Boolean) {
    // Read after the state is shown, not awaited with the listing and profile: only the bottom
    // button and the cancel row need the date, and the Function can take seconds on a cold start.
    var serverTodayAsked = readsServerToday
    if (serverTodayAsked) readServerToday()
    // A read error here stops the updates: the rental read above stays on screen, but not its
    // offer, which could be out of date. The chat hides its offer card in the same way.
    rentalRepository
        .observeRental(rentalId)
        .catch { _uiState.update { it.copy(offer = null, isOfferMine = false) } }
        .collect { live ->
          when {
            live == null -> Unit
            live.status !in SUPPORTED_STATUSES ->
                _uiState.update {
                  RentalDetailUiState(
                      isSupported = false,
                      itemTitle = live.listingTitle,
                      isOffline = it.isOffline,
                  )
                }
            else ->
                _uiState.update {
                  it.copy(
                      status = live.status,
                      offer = live.offer,
                      isOfferMine = live.offer?.authorId == uid,
                      deposit = live.deposit,
                      pickupAddress = live.pickupAddress,
                      returnAddress = live.returnAddress,
                      lateFees = live.lateFees,
                  )
                }
          }
          // A request accepted, or picked up, while the screen is open now needs the date for its
          // button. Asked once, so an unknown date is not asked again at every change of the
          // rental: from Accepted to Active, the day already read for Start Pickup serves Start
          // Return too, and reconnecting still reads it if it is unknown.
          if (live != null && needsServerToday(live.status) && !serverTodayAsked) {
            serverTodayAsked = true
            readServerToday()
          }
        }
  }

  private companion object {
    /** The statuses the screen shows in full; every other one shows the placeholder. */
    val SUPPORTED_STATUSES =
        setOf(
            RentalStatus.REQUESTED,
            RentalStatus.ACCEPTED,
            RentalStatus.ACTIVE,
            RentalStatus.DISPUTED,
        )

    /**
     * Whether [status] shows something that goes by the server's day: Start Pickup and the cancel
     * window (Accepted), or Start Return (Active). A request has no deadline, so it needs no day.
     */
    fun needsServerToday(status: RentalStatus?): Boolean =
        status == RentalStatus.ACCEPTED || status == RentalStatus.ACTIVE
  }

  /** Builds the ViewModel with its dependencies (manual constructor injection). */
  class Factory(
      private val rentalId: String,
      private val rentalRepository: RentalRepository,
      private val listingRepository: ListingRepository,
      private val profileRepository: ProfileRepository,
      private val authRepository: AuthRepository,
      private val connectivityRepository: ConnectivityRepository,
      private val serverClock: ServerClock,
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
          serverClock,
      )
          as T
    }
  }
}
