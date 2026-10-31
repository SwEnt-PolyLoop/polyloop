// Made with Claude.

package com.swent.polyloop.ui.dispute

import com.swent.polyloop.model.dispute.DisputeStage
import java.time.Instant

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
