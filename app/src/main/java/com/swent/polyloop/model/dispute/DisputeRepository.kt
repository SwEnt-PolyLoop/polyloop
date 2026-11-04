// Made with Claude.

package com.swent.polyloop.model.dispute

import kotlinx.coroutines.flow.Flow

/**
 * The disputes of the rentals the signed-in user takes part in. The app only sends proposals and
 * requests; Cloud Functions move the dispute on and split the deposit.
 */
interface DisputeRepository {

  /**
   * The dispute of the rental [rentalId], emitted again every time it changes, or null if the
   * rental has none. A read error is thrown to the collector, which handles it with `catch`.
   */
  fun observeDispute(rentalId: String): Flow<Dispute?>

  /**
   * Sends a new split from [authorId], giving [lenderPercent] percent of the deposit to the lender.
   * It becomes the latest proposal, the one the other party can accept. Fails if the dispute does
   * not exist, is no longer in [DisputeStage.SETTLE], is settled, or cannot be written.
   */
  suspend fun propose(rentalId: String, authorId: String, lenderPercent: Int): Result<Unit>

  /**
   * Accepts, as [uid], the latest proposal of the dispute, which settles it. [lenderPercent] is the
   * split the user saw: if the latest proposal gives the lender anything else (it changed
   * meanwhile), nothing is accepted and the call fails with [ProposalChangedException]. Also fails
   * if the dispute does not exist, has no proposal, is no longer in [DisputeStage.SETTLE], is
   * settled, if the latest proposal is [uid]'s own, or if it cannot be written.
   */
  suspend fun accept(rentalId: String, uid: String, lenderPercent: Int): Result<Unit>
}

/** Thrown when the user accepts a proposal that is no longer the one on the table. */
class ProposalChangedException : IllegalStateException("The proposal on the table changed")
