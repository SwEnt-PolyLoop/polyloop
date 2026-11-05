// Made with Claude.

package com.swent.polyloop.model.dispute

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * In-memory [DisputeRepository] for tests and previews; the app itself never uses it. It lives in
 * `main` so that unit tests, Compose tests and `@Preview`s can all share it.
 *
 * Holds the disputes in memory; [put] plays the part of the Cloud Functions that change them. When
 * [failure] is set, an observer throws it when it subscribes or at the next change.
 */
class FakeDisputeRepository(
    disputes: List<Dispute> = emptyList(),
    var failure: Throwable? = null,
) : DisputeRepository {

  private val stored = MutableStateFlow(disputes.associateBy { it.rentalId })

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
}
