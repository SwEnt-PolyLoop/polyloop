// Made with Claude.

package com.swent.polyloop.model.dispute

import com.swent.polyloop.model.firestore.enumOrNull
import com.swent.polyloop.model.firestore.instantOrNull
import com.swent.polyloop.model.firestore.intOrNull

/** Firestore names for disputes (see "Firestore schema" in docs/ARCHITECTURE.md). */
internal object DisputeFields {
  const val COLLECTION = "disputes"
  const val STAGE = "stage"
  const val PROPOSALS = "proposals"
  const val PROPOSAL_AUTHOR_ID = "authorId"
  const val PROPOSAL_LENDER_PERCENT = "lenderPercent"
  const val PROPOSAL_CREATED_AT = "createdAt"
  const val ESCALATES_AT = "escalatesAt"
  const val ESCALATED_BY = "escalatedBy"
  const val AGREED_AT = "agreedAt"
}

/**
 * The dispute of the rental [rentalId] stored as [data]. Only Cloud Functions write disputes, so a
 * document that breaks the schema is a bug, not a state to show: it throws an
 * [IllegalStateException] (an unknown or missing `stage`, an `escalatesAt` that is not a date, or a
 * proposal without an author, a date or a whole `lenderPercent` from 0 to 100). As in the Cloud
 * Functions, missing `proposals` read as none, and a missing `escalatesAt` reads as null: the
 * dispute never moves on by itself. A missing `agreedAt` or `escalatedBy` reads as null.
 */
internal fun disputeFromDocument(rentalId: String, data: Map<String, Any?>): Dispute {
  val proposals = data[DisputeFields.PROPOSALS]?.let { it as? List<*> ?: malformed("proposals") }
  return Dispute(
      rentalId = rentalId,
      stage = enumOrNull<DisputeStage>(data[DisputeFields.STAGE]) ?: malformed("stage"),
      proposals = proposals.orEmpty().map(::proposalFromDocument),
      escalatesAt =
          data[DisputeFields.ESCALATES_AT]?.let { instantOrNull(it) ?: malformed("escalatesAt") },
      agreedAt = instantOrNull(data[DisputeFields.AGREED_AT]),
      escalatedBy = data[DisputeFields.ESCALATED_BY] as? String,
  )
}

/** One entry of `proposals`; throws an [IllegalStateException] if it breaks the schema. */
private fun proposalFromDocument(value: Any?): Proposal {
  val fields = value as? Map<*, *> ?: malformed("proposals")
  val lenderPercent =
      intOrNull(fields[DisputeFields.PROPOSAL_LENDER_PERCENT])?.takeIf { it in 0..100 }
  return Proposal(
      authorId = fields[DisputeFields.PROPOSAL_AUTHOR_ID] as? String ?: malformed("authorId"),
      lenderPercent = lenderPercent ?: malformed("lenderPercent"),
      createdAt =
          instantOrNull(fields[DisputeFields.PROPOSAL_CREATED_AT]) ?: malformed("createdAt"),
  )
}

private fun malformed(field: String): Nothing =
    throw IllegalStateException("The dispute has a malformed $field")
