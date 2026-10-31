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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
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
 * stage on its own; 0 means today. [errorMsg] is non-null when the dispute cannot be loaded (or
 * nobody is signed in, or the user does not take part in the rental); it is blank when there is no
 * message to show, and the screen then shows a generic text. [actionFailed] is true when the last
 * proposal, acceptance or escalation could not be sent; the next one clears it.
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
    val isSending: Boolean = false,
    val actionFailed: Boolean = false,
    val isLoading: Boolean = true,
    val errorMsg: String? = null,
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

  /** True when the user can send a proposal: online, not sending, still settling. */
  val canPropose: Boolean
    get() = isOnline && !isSending && !isSettled && stage == DisputeStage.SETTLE

  /** True when the user can accept the proposal on the table: it exists and is the other's. */
  val canAccept: Boolean
    get() = canPropose && latestProposal?.byMe == false

  /** True when the user can move the dispute on: online, not sending, unsettled, not in court. */
  val canEscalate: Boolean
    get() = isOnline && !isSending && !isSettled && stage != DisputeStage.COURT
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
    _uiState.update { it.copy(isLoading = true, errorMsg = null) }
    val uid = authRepository.getCurrentUser()?.uid
    if (uid == null) {
      showError("")
      return
    }
    observing = viewModelScope.launch {
      val rental =
          rentalRepository.getRental(rentalId).getOrElse {
            showError(it.message ?: "")
            return@launch
          }
      if (rental == null || uid !in rental.participants) {
        showError("")
        return@launch
      }
      _uiState.update {
        it.copy(
            itemTitle = rental.listingTitle,
            otherPersonName = rental.otherPersonName(uid),
            isLender = uid == rental.ownerId,
        )
      }
      disputeRepository
          .observeDispute(rentalId)
          .catch { e -> showError(e.message ?: "") }
          .collect { dispute ->
            if (dispute == null) showError("") else show(dispute, uid)
          }
    }
  }

  private fun show(dispute: Dispute, uid: String) = _uiState.update {
    it.copy(
        stage = dispute.stage,
        history =
            dispute.proposals.reversed().map { p ->
              ProposalRow(p.authorId == uid, p.lenderPercent, p.createdAt)
            },
        isSettled = dispute.isSettled,
        daysUntilEscalation = daysUntil(dispute.escalatesAt),
        isLoading = false,
        errorMsg = null,
    )
  }

  /** The whole days left until [deadline], rounded up; 0 once it has passed. */
  private fun daysUntil(deadline: Instant): Int {
    val seconds = Duration.between(clock(), deadline).seconds
    return if (seconds <= 0) 0 else ((seconds + SECONDS_PER_DAY - 1) / SECONDS_PER_DAY).toInt()
  }

  private fun showError(message: String) = _uiState.update {
    it.copy(isLoading = false, errorMsg = message)
  }

  private companion object {
    const val SECONDS_PER_DAY = 86_400L
  }
}
