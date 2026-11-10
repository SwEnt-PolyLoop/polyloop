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
import com.swent.polyloop.model.dispute.StageChangedException
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

/** How the dispute moved on to its current stage while the screen was open. */
enum class EscalationNotice {
  /** The other person escalated it. */
  BY_OTHER,
  /** It escalated by itself, at the end of the time left. */
  AUTOMATIC,
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
 * after it was shown, which keeps the last state on screen. [actionError] is what the last
 * proposal, acceptance or escalation could not do; the next action, opening the split editor, a new
 * stage or an agreement clears it, since the action it names then no longer applies.
 * [draftLenderPercent] is the split the user is editing, as the lender's percentage, or null when
 * the editor is closed. [isConfirmingEscalation] is true while the user is asked to confirm moving
 * the dispute on to the LLM proposal.
 *
 * What the other person did while the screen is open, never what the user did or what was there
 * when the screen opened: [offerUpdated] is true when they sent a new proposal, until the user
 * opens the split editor or sends something, or the dispute leaves direct settlement;
 * [acceptedByOther] is true once they accepted the user's proposal, until the screen is opened
 * again; and [escalationNotice] says how the dispute moved on to its current stage, until it moves
 * on again (null when the user moved it on).
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
    val isConfirmingEscalation: Boolean = false,
    val offerUpdated: Boolean = false,
    val acceptedByOther: Boolean = false,
    val escalationNotice: EscalationNotice? = null,
    val actionError: DisputeActionError? = null,
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val refreshFailed: Boolean = false,
) {
  /** The proposal on the table, or null when nobody has proposed yet. */
  val latestProposal: ProposalRow?
    get() = history.firstOrNull()

  /**
   * What the user would get back from the deposit if [latestProposal] were applied, in PolyPoints,
   * or null without a proposal. See [splitDeposit].
   */
  val youGetBack: Int?
    get() = latestProposal?.let { splitDeposit(deposit, it.lenderPercent, isLender) }

  /** True when the user can send a proposal: loaded, online, not sending, still settling. */
  val canPropose: Boolean
    get() = isLoaded && isOnline && !isSending && !isSettled && stage == DisputeStage.SETTLE

  /** True while the user edits a split. */
  val isEditing: Boolean
    get() = draftLenderPercent != null

  /** What the user would get back if the draft were applied, in PolyPoints. */
  val draftGetBack: Int?
    get() = draftLenderPercent?.let { splitDeposit(deposit, it, isLender) }

  /** True when the draft can be sent: the user can propose and it is not the split on the table. */
  val canSend: Boolean
    get() =
        canPropose &&
            draftLenderPercent != null &&
            draftLenderPercent != latestProposal?.lenderPercent

  /** True when the user can accept the proposal on the table: it exists and is the other's. */
  val canAccept: Boolean
    get() = canPropose && latestProposal?.byMe == false

  /**
   * True when the screen says the other person updated their offer: not when the user's acceptance
   * failed because of it, whose error already says so.
   */
  val showsOfferUpdated: Boolean
    get() = offerUpdated && actionError != DisputeActionError.PROPOSAL_CHANGED

  /**
   * True when the user can move the dispute on to the LLM proposal: loaded, online, not sending,
   * unsettled, and in direct settlement, the only stage that can be escalated.
   */
  val canEscalate: Boolean
    get() = canPropose

  /** True once a dispute is shown: not while it first loads, nor when that load failed. */
  private val isLoaded: Boolean
    get() = !isLoading && !loadFailed
}

/**
 * The share of a [deposit] that goes to the lender (if [toLender]) or to the borrower when the
 * lender gets [lenderPercent] percent, in whole PolyPoints.
 *
 * The borrower's share is rounded down and the lender gets the rest, so the two shares always add
 * up to the deposit: 33 PP at 50% gives 16 PP to the borrower and 17 PP to the lender. The Cloud
 * Function that applies a split must round the same way (see "Disputes" in ARCHITECTURE.md).
 */
internal fun splitDeposit(deposit: Int, lenderPercent: Int, toLender: Boolean): Int {
  val borrower = deposit * (100 - lenderPercent) / 100
  return if (toLender) deposit - borrower else borrower
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

  /**
   * Opens the split editor on the proposal on the table, or on 50/50 without one. Does nothing
   * unless [DisputeUiState.canPropose].
   */
  fun startEditing() {
    if (!_uiState.value.canPropose) return
    _uiState.update {
      it.copy(
          draftLenderPercent = it.latestProposal?.lenderPercent ?: 50,
          actionError = null,
          offerUpdated = false,
      )
    }
  }

  /** Moves the draft to [lenderPercent] percent for the lender, kept between 0 and 100. */
  fun setDraft(lenderPercent: Int) = _uiState.update {
    if (it.isEditing) it.copy(draftLenderPercent = lenderPercent.coerceIn(0, 100)) else it
  }

  /**
   * Closes the split editor without sending the draft, and drops the error of a proposal that
   * failed, which no longer applies once the draft is gone. Does nothing while a proposal is being
   * sent, so the editor stays open to show whether it went through.
   */
  fun cancelEditing() {
    if (_uiState.value.isSending) return
    _uiState.update {
      it.copy(
          draftLenderPercent = null,
          actionError = it.actionError.takeUnless { error -> error == DisputeActionError.PROPOSE },
      )
    }
  }

  /**
   * Sends the draft as a first proposal, a counter-proposal or an update of the user's own. The
   * editor closes once the proposal goes through, and stays open with the draft if it fails, so the
   * user can send it again. Does nothing unless [DisputeUiState.canSend], so the split already on
   * the table is never sent again.
   */
  fun sendDraft() {
    val draft = _uiState.value.draftLenderPercent
    if (!_uiState.value.canSend || draft == null) return
    send(DisputeActionError.PROPOSE, closesEditor = true) { uid ->
      disputeRepository.propose(rentalId, uid, draft)
    }
  }

  /** Accepts the other person's proposal. Does nothing unless [DisputeUiState.canAccept]. */
  fun accept() {
    val seen = _uiState.value.latestProposal
    if (!_uiState.value.canAccept || seen == null) return
    send(DisputeActionError.ACCEPT) { uid ->
      disputeRepository.accept(rentalId, uid, seen.lenderPercent)
    }
  }

  /**
   * Asks the user to confirm moving the dispute on to the LLM proposal. Does nothing unless
   * [DisputeUiState.canEscalate].
   */
  fun askEscalate() {
    if (!_uiState.value.canEscalate) return
    _uiState.update { it.copy(isConfirmingEscalation = true) }
  }

  /** Closes the confirmation without escalating. */
  fun dismissEscalate() = _uiState.update { it.copy(isConfirmingEscalation = false) }

  /**
   * Closes the confirmation and moves the dispute on to the LLM proposal. Does nothing else unless
   * the confirmation is still open (a new stage closes it) and [DisputeUiState.canEscalate]. If the
   * dispute already moved on meanwhile, nothing changes and no error is shown: the new stage and
   * its notice are shown instead, after loading the dispute again if it could not be refreshed.
   */
  fun escalate() {
    val confirmed = _uiState.value.isConfirmingEscalation
    dismissEscalate()
    if (!confirmed || !_uiState.value.canEscalate) return
    send(DisputeActionError.ESCALATE) { uid -> disputeRepository.escalate(rentalId, uid) }
  }

  /**
   * Runs [request] as the signed-in user, with [DisputeUiState.isSending] set meanwhile so no
   * second action starts. The new dispute arrives through the observer; a failure (or nobody signed
   * in) sets [DisputeUiState.actionError] to [error], or to [DisputeActionError.PROPOSAL_CHANGED]
   * when the proposal accepted was no longer the one on the table. An escalation refused because
   * the dispute already moved on sets none. After either refusal, the dispute is loaded again if it
   * could not be refreshed, so the new proposal or stage shows. A success also closes the split
   * editor if [closesEditor].
   */
  private fun send(
      error: DisputeActionError,
      closesEditor: Boolean = false,
      request: suspend (uid: String) -> Result<Unit>,
  ) {
    val uid = authRepository.getCurrentUser()?.uid
    _uiState.update { it.copy(isSending = true, actionError = null, offerUpdated = false) }
    viewModelScope.launch {
      val failure = if (uid == null) null else request(uid).exceptionOrNull()
      val actionError =
          when {
            failure is ProposalChangedException -> DisputeActionError.PROPOSAL_CHANGED
            failure is StageChangedException -> null
            uid == null || failure != null -> error
            else -> null
          }
      _uiState.update {
        val succeeded = actionError == null
        it.copy(
            isSending = false,
            actionError = actionError,
            draftLenderPercent = if (closesEditor && succeeded) null else it.draftLenderPercent,
        )
      }
      val changedMeanwhile = failure is ProposalChangedException || failure is StageChangedException
      if (changedMeanwhile && _uiState.value.refreshFailed) observe()
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
      // A rental that cannot be read fails like a missing one.
      val rental = rentalRepository.getRental(rentalId).getOrNull()
      if (rental == null || uid !in rental.participants) {
        fail()
        return@launch
      }
      _uiState.update {
        it.copy(
            itemTitle = rental.listingTitle,
            otherPersonName = rental.otherPersonName(uid),
            isLender = uid == rental.ownerId,
            deposit = rental.deposit,
        )
      }
      disputeRepository
          .observeDispute(rentalId)
          .catch { fail() }
          .collect { dispute -> if (dispute == null) fail() else show(dispute, uid) }
    }
  }

  private fun show(dispute: Dispute, uid: String) {
    // Changes are told only against a dispute already shown, so not when the screen opens.
    val before = shown
    shown = dispute
    val stillSettling = !dispute.isSettled && dispute.stage == DisputeStage.SETTLE
    val theirNewOffer =
        before != null &&
            dispute.proposals.size > before.proposals.size &&
            dispute.latestProposal?.authorId != uid
    val stageChanged = before != null && dispute.stage != before.stage
    val justSettled = before?.isSettled == false && dispute.isSettled
    _uiState.update {
      it.copy(
          offerUpdated = (it.offerUpdated || theirNewOffer) && stillSettling,
          acceptedByOther =
              it.acceptedByOther || (justSettled && dispute.latestProposal?.authorId == uid),
          // The failed action was for a stage or an offer that is gone, so retrying it is wrong.
          actionError = it.actionError.takeUnless { stageChanged || justSettled },
          escalationNotice =
              if (stageChanged) escalationNoticeFor(dispute, uid) else it.escalationNotice,
          stage = dispute.stage,
          // An agreement or a new stage ends the editing and the confirmation, which was for
          // another stage.
          isConfirmingEscalation =
              it.isConfirmingEscalation && !dispute.isSettled && dispute.stage == it.stage,
          draftLenderPercent = it.draftLenderPercent.takeIf { stillSettling },
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

  /** How [dispute] moved on to its current stage, as seen by [uid]; null when they moved it on. */
  private fun escalationNoticeFor(dispute: Dispute, uid: String): EscalationNotice? =
      when (dispute.escalatedBy) {
        null -> EscalationNotice.AUTOMATIC
        uid -> null
        else -> EscalationNotice.BY_OTHER
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
    const val MILLIS_PER_MINUTE = 60_000L
  }
}
