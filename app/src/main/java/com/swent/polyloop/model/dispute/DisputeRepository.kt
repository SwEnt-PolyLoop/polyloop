// Made with Claude.

package com.swent.polyloop.model.dispute

import kotlinx.coroutines.flow.Flow

/**
 * The disputes of the rentals the signed-in user takes part in. Cloud Functions move the dispute on
 * and split the deposit.
 */
interface DisputeRepository {

  /**
   * The dispute of the rental [rentalId], emitted again every time it changes, or null if the
   * rental has none. A read error is thrown to the collector, which handles it with `catch`.
   */
  fun observeDispute(rentalId: String): Flow<Dispute?>
}
