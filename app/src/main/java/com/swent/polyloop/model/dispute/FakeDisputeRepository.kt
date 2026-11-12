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
 * dispute, and escalating moves it on from direct settlement to the LLM proposal. Each call acts as
 * the user [currentUid] gives at that moment (tests usually read it from a `FakeAuthRepository`),
 * and fails when it gives null. Like the Functions, a call made once a dispute's
 * [Dispute.escalatesAt] has passed moves it on as if by itself, then fails with
 * [StageChangedException]. Like `acceptSplit`, accepting checks that the split is still the one on
 * the table before checking its author, so a split the user replaced meanwhile, even with their
 * own, fails with [ProposalChangedException]. Unlike the Functions, it does not check that the user
 * is one of the rental's participants, nor the cap on the number of proposals. When [failure] is
 * set, every call fails with it; an observer throws it when it subscribes or at the next change.
 * When [gate] is set, the suspending calls wait for it to complete, so tests can see a request in
 * progress. [clock] gives the current time, and the time of new proposals, agreements and
 * escalations.
 */
class FakeDisputeRepository(
    disputes: List<Dispute> = emptyList(),
    private val currentUid: () -> String?,
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

  override suspend fun propose(rentalId: String, lenderPercent: Int) =
      change(rentalId) { dispute, uid ->
        check(dispute.latestProposal?.lenderPercent != lenderPercent) { "Already on the table" }
        dispute.copy(proposals = dispute.proposals + Proposal(uid, lenderPercent, clock()))
      }

  override suspend fun accept(rentalId: String, lenderPercent: Int) =
      change(rentalId) { dispute, uid ->
        val latest = checkNotNull(dispute.latestProposal) { "There is no proposal to accept" }
        if (latest.lenderPercent != lenderPercent) throw ProposalChangedException()
        check(latest.authorId != uid) { "$uid cannot accept their own proposal" }
        dispute.copy(agreedAt = clock())
      }

  override suspend fun escalate(rentalId: String) =
      change(rentalId) { dispute, uid ->
        dispute.copy(stage = DisputeStage.LLM_PROPOSAL, escalatedBy = uid)
      }

  /**
   * Applies [update], as the current user, to the dispute of [rentalId] while it is still in direct
   * settlement, or fails like the Cloud Functions would. Past its [Dispute.escalatesAt], the
   * dispute moves on by itself instead and the call fails with [StageChangedException].
   */
  private suspend fun change(
      rentalId: String,
      update: (Dispute, uid: String) -> Dispute,
  ): Result<Unit> {
    gate?.await()
    failure?.let {
      return Result.failure(it)
    }
    return runCatching {
      val uid = checkNotNull(currentUid()) { "Nobody is signed in" }
      val dispute = checkNotNull(stored.value[rentalId]) { "No dispute for rental $rentalId" }
      if (dispute.stage != DisputeStage.SETTLE || dispute.isSettled) throw StageChangedException()
      val deadline = dispute.escalatesAt
      if (deadline != null && clock() >= deadline) {
        put(dispute.copy(stage = DisputeStage.LLM_PROPOSAL, escalatedBy = null))
        throw StageChangedException()
      }
      put(update(dispute, uid))
    }
  }
}
