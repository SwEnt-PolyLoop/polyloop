// Made with Claude.

package com.swent.polyloop.model.dispute

import java.time.Instant

/**
 * A disagreement about how to split a rental's deposit, opened when the lender flags damage after
 * the return. There is at most one per rental, so it is identified by [rentalId]. Only Cloud
 * Functions change its [stage], [escalatesAt] and [agreedAt]; the app sends proposals and requests.
 *
 * @property proposals every split proposed so far, oldest first.
 * @property escalatesAt when the dispute moves on to the next stage on its own if nobody agrees. It
 *   is set again each time the dispute moves on to a new stage.
 * @property agreedAt when one party accepted the other's latest proposal, or null while they
 *   disagree.
 */
data class Dispute(
    val rentalId: String,
    val stage: DisputeStage,
    val proposals: List<Proposal>,
    val escalatesAt: Instant,
    val agreedAt: Instant? = null,
) {
  /** The proposal on the table: the last one sent, or null when nobody has proposed yet. */
  val latestProposal: Proposal?
    get() = proposals.lastOrNull()

  /** True once both parties agreed on a split. */
  val isSettled: Boolean
    get() = agreedAt != null
}

/** The three stages of a dispute, in order; see "Disputes" in docs/ARCHITECTURE.md. */
enum class DisputeStage {
  /** The parties propose splits to each other. */
  SETTLE,
  /** Gemini proposes a split from the pickup and return photos. */
  LLM_PROPOSAL,
  /** A court ruling, uploaded as a PDF, is reviewed by an admin. */
  COURT,
}

/**
 * A split of the deposit proposed by [authorId]: [lenderPercent] percent goes to the lender, the
 * rest to the borrower.
 */
data class Proposal(val authorId: String, val lenderPercent: Int, val createdAt: Instant) {
  init {
    require(lenderPercent in 0..100) { "lenderPercent $lenderPercent is not between 0 and 100" }
  }

  /** The percentage of the deposit that goes back to the borrower. */
  val borrowerPercent: Int
    get() = 100 - lenderPercent
}
