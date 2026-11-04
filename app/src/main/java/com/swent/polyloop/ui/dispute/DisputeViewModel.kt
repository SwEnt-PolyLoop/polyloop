// Made with Claude.

package com.swent.polyloop.ui.dispute

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swent.polyloop.model.auth.AuthRepository
import com.swent.polyloop.model.connectivity.ConnectivityRepository
import com.swent.polyloop.model.dispute.Dispute
import com.swent.polyloop.model.dispute.DisputeRepository
import com.swent.polyloop.model.dispute.DisputeStage
import com.swent.polyloop.model.rental.RentalRepository
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One proposed split, ready to display.
 *
 * @property byMe true when the signed-in user sent it.
 * @property lenderPercent the percentage of the deposit that goes to the lender.
 */
data class ProposalRow(val byMe: Boolean, val lenderPercent: Int, val createdAt: Instant) {
  /** The percentage of the deposit that goes back to the borrower. */
  val borrowerPercent: Int
    get() = 100 - lenderPercent
}

/**
 * State of the Dispute screen.
 *
 * [history] holds every proposal, newest first; the first one is the proposal on the table.
 * [otherPersonName] is "" when the name is missing; the screen then shows a placeholder.
 * [daysUntilEscalation] is the number of days, rounded up, before the dispute moves on to the next
 * stage on its own, kept up to date while the screen is shown; 0 once it has passed, and after
 * direct settlement or once settled, where it never moves on by itself. [loadFailed] is true when
 * the dispute cannot be loaded (or nobody is signed in, or the user does not take part in the
 * rental) and nothing was shown yet; [refreshFailed] is true when it could not be loaded again
 * after it was shown, which keeps the last state on screen.
 */
data class DisputeUiState(
    val itemTitle: String = "",
    val otherPersonName: String = "",
    val isLender: Boolean = false,
    val deposit: Int = 0,
    val stage: DisputeStage = DisputeStage.SETTLE,
    val history: List<ProposalRow> = emptyList(),
    val isSettled: Boolean = false,
    val daysUntilEscalation: Int = 0,
    val isOnline: Boolean = true,
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val refreshFailed: Boolean = false,
) {
  /** The proposal on the table, or null when nobody has proposed yet. */
  val latestProposal: ProposalRow?
    get() = history.firstOrNull()

  /**
   * What the user would get back from the deposit if [latestProposal] were applied, in tenths of a
   * PolyPoint, or null without a proposal. See [shareInTenths].
   */
  val youGetBackTenths: Int?
    get() = latestProposal?.let { shareInTenths(deposit, it.lenderPercent, isLender) }
}

/**
 * The share of a [deposit] (in PolyPoints) that goes to the lender (if [toLender]) or to the
 * borrower when the lender gets [lenderPercent] percent, in tenths of a PolyPoint.
 *
 * Display only, until the team decides how the Cloud Function splits a deposit into whole
 * PolyPoints: the borrower's share is rounded down to a tenth, and the lender gets the rest, so the
 * two shares always add up to the deposit.
 */
fun shareInTenths(deposit: Int, lenderPercent: Int, toLender: Boolean): Int {
  val borrowerTenths = deposit * (100 - lenderPercent) / 10
  return if (toLender) deposit * 10 - borrowerTenths else borrowerTenths
}

/**
 * The dispute of one rental, live, as seen by the signed-in user, who takes part in the rental as
 * lender or borrower. [clock] gives the current time, to count the days left before escalation.
 */
class DisputeViewModel(
    private val rentalId: String,
    private val disputeRepository: DisputeRepository,
    private val rentalRepository: RentalRepository,
    private val authRepository: AuthRepository,
    connectivityRepository: ConnectivityRepository,
    private val clock: () -> Instant = { Instant.now() },
) : ViewModel() {

  private val _uiState = MutableStateFlow(DisputeUiState())
  val uiState: StateFlow<DisputeUiState> = _uiState.asStateFlow()

  private var observing: Job? = null

  /** Keeps [DisputeUiState.daysUntilEscalation] up to date while the screen is shown. */
  private var ticking: Job? = null

  /** The last dispute shown, or null until one is loaded. */
  private var shown: Dispute? = null

  init {
    viewModelScope.launch {
      connectivityRepository.isOnline.collect { online ->
        _uiState.update { it.copy(isOnline = online) }
      }
    }
    observe()
  }

  /** Loads the dispute again, e.g. after an error. */
  fun retry() = observe()

  private fun observe() {
    observing?.cancel()
    // Once a dispute is shown, loading it again keeps it on screen.
    if (shown == null) _uiState.update { it.copy(isLoading = true, loadFailed = false) }
    val uid = authRepository.getCurrentUser()?.uid
    if (uid == null) {
      fail()
      return
    }
    observing = viewModelScope.launch {
      val rental =
          rentalRepository.getRental(rentalId).getOrElse {
            fail()
            return@launch
          }
      if (rental == null || uid !in rental.participants) {
        fail()
        return@launch
      }
      _uiState.update {
        it.copy(
            itemTitle = rental.listingTitle,
            otherPersonName = rental.otherPersonName(uid),
            isLender = uid == rental.ownerId,
            deposit = PLACEHOLDER_DEPOSIT,
        )
      }
      disputeRepository
          .observeDispute(rentalId)
          .catch { fail() }
          .collect { dispute -> if (dispute == null) fail() else show(dispute, uid) }
    }
  }

  private fun show(dispute: Dispute, uid: String) {
    shown = dispute
    val stillSettling = !dispute.isSettled && dispute.stage == DisputeStage.SETTLE
    _uiState.update {
      it.copy(
          stage = dispute.stage,
          history =
              dispute.proposals.reversed().map { p ->
                ProposalRow(p.authorId == uid, p.lenderPercent, p.createdAt)
              },
          isSettled = dispute.isSettled,
          daysUntilEscalation = if (stillSettling) daysUntil(dispute.escalatesAt) else 0,
          isLoading = false,
          loadFailed = false,
          refreshFailed = false,
      )
    }
    ticking?.cancel()
    if (stillSettling) countDownTo(dispute.escalatesAt)
  }

  /** The whole days left until [deadline], rounded up; 0 once it has passed. */
  private fun daysUntil(deadline: Instant): Int {
    val seconds = Duration.between(clock(), deadline).seconds
    return if (seconds <= 0) 0 else ((seconds + SECONDS_PER_DAY - 1) / SECONDS_PER_DAY).toInt()
  }

  /**
   * Updates the days left until [deadline] once a minute while the screen collects [uiState], until
   * the deadline passes. It pauses while nobody collects it (e.g. the screen is in the back stack)
   * and updates them as soon as the screen is shown again.
   */
  private fun countDownTo(deadline: Instant) {
    ticking = viewModelScope.launch {
      while (clock() < deadline) {
        _uiState.subscriptionCount.first { it > 0 }
        _uiState.update { it.copy(daysUntilEscalation = daysUntil(deadline)) }
        delay(MILLIS_PER_MINUTE)
      }
      _uiState.update { it.copy(daysUntilEscalation = 0) }
    }
  }

  /**
   * A failed load: the whole screen shows the error if no dispute was shown yet; otherwise the last
   * one stays, with a note.
   */
  private fun fail() = _uiState.update {
    if (shown == null) it.copy(isLoading = false, loadFailed = true)
    else it.copy(refreshFailed = true)
  }

  private companion object {
    /** The deposit shown until rentals have one (#176), in PolyPoints. */
    const val PLACEHOLDER_DEPOSIT = 100

    const val SECONDS_PER_DAY = 86_400L
    const val MILLIS_PER_MINUTE = 60_000L
  }
}
