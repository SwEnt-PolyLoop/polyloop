// Made with Claude.

package com.swent.polyloop.model.dispute

import kotlinx.coroutines.flow.Flow

/**
 * The disputes of the rentals the signed-in user takes part in. The app only sends proposals and
 * requests; Cloud Functions move the dispute on and split the deposit. Every action also fails if
 * the rental is no longer disputed.
 */
interface DisputeRepository {

  /**
   * The dispute of the rental [rentalId], emitted again every time it changes, or null if the
   * rental has none. A read error is thrown to the collector, which handles it with `catch`.
   */
  fun observeDispute(rentalId: String): Flow<Dispute?>

  /**
   * Sends a new split from the signed-in user, giving [lenderPercent] percent of the deposit to the
   * lender. It becomes the latest proposal, the one the other party can accept. Fails with
   * [StageChangedException] if the dispute is settled or no longer in [DisputeStage.SETTLE], or if
   * its [Dispute.escalatesAt] has passed (the dispute then moves on to the next stage instead).
   * Also fails if nobody is signed in, the user does not take part in the rental, the dispute does
   * not exist, the split is the latest proposal already, or it cannot be sent.
   */
  suspend fun propose(rentalId: String, lenderPercent: Int): Result<Unit>

  /**
   * Accepts, as the signed-in user, the latest proposal of the dispute, which settles it.
   * [lenderPercent] is the split the user saw: if the latest proposal gives the lender anything
   * else (it changed meanwhile), nothing is accepted and the call fails with
   * [ProposalChangedException]. Fails with [StageChangedException] like [propose]. Also fails if
   * nobody is signed in, the user does not take part in the rental, the dispute does not exist, has
   * no proposal, if the latest proposal is the user's own, or if it cannot be sent.
   */
  suspend fun accept(rentalId: String, lenderPercent: Int): Result<Unit>

  /**
   * Moves the dispute on from direct settlement to the LLM proposal right away, as the signed-in
   * user; it is the only stage that can be escalated. If the dispute is settled or no longer in
   * [DisputeStage.SETTLE] (it moved on meanwhile), nothing changes and the call fails with
   * [StageChangedException], so two escalations sent at once never skip a stage. Once its
   * [Dispute.escalatesAt] has passed, it moves on as if by itself ([Dispute.escalatedBy] null) and
   * the call fails with [StageChangedException] too. Also fails if nobody is signed in, the user
   * does not take part in the rental, the dispute does not exist, or it cannot be sent.
   */
  suspend fun escalate(rentalId: String): Result<Unit>
}

/** Thrown when the user accepts a proposal that is no longer the one on the table. */
class ProposalChangedException : IllegalStateException("The proposal on the table changed")

/**
 * Thrown when the user acts on a dispute that is settled or no longer in direct settlement, e.g.
 * because the other person escalated it or it moved on by itself meanwhile.
 */
class StageChangedException : IllegalStateException("The dispute is no longer at that stage")
