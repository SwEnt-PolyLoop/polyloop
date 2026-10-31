// Made with Claude.

package com.swent.polyloop.ui.dispute

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swent.polyloop.model.auth.AuthRepository
import com.swent.polyloop.model.connectivity.ConnectivityRepository
import com.swent.polyloop.model.dispute.Dispute
import com.swent.polyloop.model.dispute.DisputeRepository
import com.swent.polyloop.model.dispute.DisputeStage
import com.swent.polyloop.model.dispute.ProposalChangedException
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

/** What the user tried and could not do; the screen shows it next to the part concerned. */
enum class DisputeActionError {
  /** The proposal could not be sent. */
  PROPOSE,
  /** The proposal could not be accepted. */
  ACCEPT,
  /** The proposal changed just before the user accepted it, so nothing was accepted. */
  PROPOSAL_CHANGED,
  /** The dispute could not be moved on to the next stage. */
  ESCALATE,
}

/**
 * State of the Dispute screen.
 *
 * [history] holds every proposal, newest first; the first one is the proposal on the table.
 * [otherPersonName] is "" when the name is missing; the screen then shows a placeholder.
 * [daysUntilEscalation] is the number of days, rounded up, before the dispute moves on to the next
 * stage on its own; 0 means today. [loadFailed] is true when the dispute cannot be loaded (or
 * nobody is signed in, or the user does not take part in the rental) and nothing was shown yet;
 * [refreshFailed] is true when it could not be loaded again after it was shown, which keeps the
 * last state on screen. [actionError] is what the last proposal, acceptance or escalation could not
 * do; the next one clears it. [draftLenderPercent] is the split the user is editing, as the
 * lender's percentage, or null when the editor is closed.
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
    val draftLenderPercent: Int? = null,
    val actionError: DisputeActionError? = null,
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

  /** True when the user can send a proposal: online, not sending, still settling. */
  val canPropose: Boolean
    get() = isOnline && !isSending && !isSettled && stage == DisputeStage.SETTLE

  /** True while the user edits a split. */
  val isEditing: Boolean
    get() = draftLenderPercent != null

  /** What the user would get back if the draft were applied, in tenths of a PolyPoint. */
  val draftGetBackTenths: Int?
    get() = draftLenderPercent?.let { shareInTenths(deposit, it, isLender) }

  /** True when the draft can be sent: the user can propose and it is not the split on the table. */
  val canSend: Boolean
    get() =
        canPropose &&
            draftLenderPercent != null &&
            draftLenderPercent != latestProposal?.lenderPercent

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

  /**
   * Opens the split editor on the proposal on the table, or on 50/50 without one. Does nothing
   * unless [DisputeUiState.canPropose].
   */
  fun startEditing() {
    if (!_uiState.value.canPropose) return
    _uiState.update {
      it.copy(draftLenderPercent = it.latestProposal?.lenderPercent ?: 50, actionError = null)
    }
  }

  /** Moves the draft to [lenderPercent] percent for the lender, kept between 0 and 100. */
  fun setDraft(lenderPercent: Int) = _uiState.update {
    if (it.isEditing) it.copy(draftLenderPercent = lenderPercent.coerceIn(0, 100)) else it
  }

  /** Closes the split editor without sending the draft. */
  fun cancelEditing() = _uiState.update { it.copy(draftLenderPercent = null) }

  /**
   * Sends the draft as a first proposal, a counter-proposal or an update of the user's own, and
   * closes the editor, whether the proposal goes through or not. Does nothing unless
   * [DisputeUiState.canSend], so the split already on the table is never sent again.
   */
  fun sendDraft() {
    val draft = _uiState.value.draftLenderPercent
    if (!_uiState.value.canSend || draft == null) return
    _uiState.update { it.copy(draftLenderPercent = null) }
    send(DisputeActionError.PROPOSE) { uid -> disputeRepository.propose(rentalId, uid, draft) }
  }

  /** Accepts the other person's proposal. Does nothing unless [DisputeUiState.canAccept]. */
  fun accept() {
    val shown = _uiState.value.latestProposal
    if (!_uiState.value.canAccept || shown == null) return
    send(DisputeActionError.ACCEPT) { uid ->
      disputeRepository.accept(rentalId, uid, shown.lenderPercent)
    }
  }

  /** Moves the dispute on to the next stage. Does nothing unless [DisputeUiState.canEscalate]. */
  fun escalate() {
    if (!_uiState.value.canEscalate) return
    send(DisputeActionError.ESCALATE) { uid -> disputeRepository.escalate(rentalId, uid) }
  }

  /**
   * Runs [request] as the signed-in user, with [DisputeUiState.isSending] set meanwhile so no
   * second action starts. The new dispute arrives through the observer; a failure (or nobody signed
   * in) sets [DisputeUiState.actionError] to [error], or to [DisputeActionError.PROPOSAL_CHANGED]
   * when the proposal accepted was no longer the one on the table.
   */
  private fun send(
      error: DisputeActionError,
      request: suspend (uid: String) -> Result<Unit>,
  ) {
    val uid = authRepository.getCurrentUser()?.uid
    _uiState.update { it.copy(isSending = true, actionError = null) }
    viewModelScope.launch {
      val failure = if (uid == null) null else request(uid).exceptionOrNull()
      val actionError =
          when {
            failure is ProposalChangedException -> DisputeActionError.PROPOSAL_CHANGED
            uid == null || failure != null -> error
            else -> null
          }
      _uiState.update { it.copy(isSending = false, actionError = actionError) }
    }
  }

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
    _uiState.update {
      it.copy(
          stage = dispute.stage,
          // An agreement or a new stage ends the editing.
          draftLenderPercent =
              it.draftLenderPercent.takeIf {
                !dispute.isSettled && dispute.stage == DisputeStage.SETTLE
              },
          history =
              dispute.proposals.reversed().map { p ->
                ProposalRow(p.authorId == uid, p.lenderPercent, p.createdAt)
              },
          isSettled = dispute.isSettled,
          daysUntilEscalation = daysUntil(dispute.escalatesAt),
          isLoading = false,
          loadFailed = false,
          refreshFailed = false,
      )
    }
  }

  /** The whole days left until [deadline], rounded up; 0 once it has passed. */
  private fun daysUntil(deadline: Instant): Int {
    val seconds = Duration.between(clock(), deadline).seconds
    return if (seconds <= 0) 0 else ((seconds + SECONDS_PER_DAY - 1) / SECONDS_PER_DAY).toInt()
  }

  /**
   * A failed load: the whole screen shows the error if no dispute was shown yet; otherwise the last
   * one stays, with a note.
   */
  private fun fail() = _uiState.update {
    if (shown == null) it.copy(isLoading = false, loadFailed = true)
    else it.copy(refreshFailed = true)
  }

  /** Builds the ViewModel with its dependencies (manual constructor injection). */
  class Factory(
      private val rentalId: String,
      private val disputeRepository: DisputeRepository,
      private val rentalRepository: RentalRepository,
      private val authRepository: AuthRepository,
      private val connectivityRepository: ConnectivityRepository,
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      require(modelClass.isAssignableFrom(DisputeViewModel::class.java)) {
        "Unknown ViewModel class: ${modelClass.name}"
      }
      @Suppress("UNCHECKED_CAST")
      return DisputeViewModel(
          rentalId,
          disputeRepository,
          rentalRepository,
          authRepository,
          connectivityRepository,
      )
          as T
    }
  }

  private companion object {
    const val SECONDS_PER_DAY = 86_400L
  }
}
