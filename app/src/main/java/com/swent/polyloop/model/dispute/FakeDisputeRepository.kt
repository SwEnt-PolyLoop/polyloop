// Made with Claude.

package com.swent.polyloop.model.dispute

import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * In-memory [DisputeRepository] for tests and previews; the app itself never uses it. It lives in
 * `main` so that unit tests, Compose tests and `@Preview`s can all share it.
 *
 * Holds the disputes in memory and plays the part of the Cloud Functions: accepting settles the
 * dispute, and escalating moves it on from direct settlement to the LLM proposal. When [failure] is
 * set, every call fails with it; an observer throws it when it subscribes or at the next change.
 * When [gate] is set, the suspending calls wait for it to complete, so tests can see a request in
 * progress. [clock] gives the time of new proposals, agreements and escalations.
 */
class FakeDisputeRepository(
    disputes: List<Dispute> = emptyList(),
    var failure: Throwable? = null,
    var gate: CompletableDeferred<Unit>? = null,
    private val clock: () -> Instant = { Instant.now() },
) : DisputeRepository {

  private val stored = MutableStateFlow(disputes.associateBy { it.rentalId })

  /** The disputes currently stored, e.g. to check what a ViewModel sent. */
  val disputes: List<Dispute>
    get() = stored.value.values.toList()

  /**
   * Stores [dispute] as the server would, e.g. after an automatic escalation; observers then see
   * it, or throw [failure] if it is set.
   */
  fun put(dispute: Dispute) {
    stored.value = stored.value + (dispute.rentalId to dispute)
  }

  /** Deletes the dispute of [rentalId], to test a missing dispute; observers then see null. */
  fun remove(rentalId: String) {
    stored.value = stored.value - rentalId
  }

  override fun observeDispute(rentalId: String): Flow<Dispute?> =
      stored
          .map {
            failure?.let { throw it }
            it[rentalId]
          }
          .distinctUntilChanged()

  override suspend fun propose(rentalId: String, authorId: String, lenderPercent: Int) =
      change(rentalId) {
        check(it.stage == DisputeStage.SETTLE && !it.isSettled) { "Proposals are closed" }
        it.copy(proposals = it.proposals + Proposal(authorId, lenderPercent, clock()))
      }

  override suspend fun accept(rentalId: String, uid: String, lenderPercent: Int) =
      change(rentalId) {
        check(it.stage == DisputeStage.SETTLE && !it.isSettled) { "Proposals are closed" }
        val latest = checkNotNull(it.latestProposal) { "There is no proposal to accept" }
        check(latest.authorId != uid) { "$uid cannot accept their own proposal" }
        if (latest.lenderPercent != lenderPercent) throw ProposalChangedException()
        it.copy(agreedAt = clock())
      }

  override suspend fun escalate(rentalId: String, uid: String) =
      change(rentalId) {
        if (it.stage != DisputeStage.SETTLE) throw StageChangedException()
        check(!it.isSettled) { "The dispute cannot move on" }
        it.copy(stage = DisputeStage.LLM_PROPOSAL, escalatedBy = uid)
      }

  /** Applies [update] to the dispute of [rentalId], or fails like the real repository would. */
  private suspend fun change(rentalId: String, update: (Dispute) -> Dispute): Result<Unit> {
    gate?.await()
    failure?.let {
      return Result.failure(it)
    }
    return runCatching {
      val dispute = checkNotNull(stored.value[rentalId]) { "No dispute for rental $rentalId" }
      stored.value = stored.value + (rentalId to update(dispute))
    }
  }
}
