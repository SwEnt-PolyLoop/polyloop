// Made with Claude.

package com.swent.polyloop.model.dispute

import com.google.firebase.Timestamp
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DisputeDocumentTest {

  private val at = Instant.parse("2026-10-14T10:00:00Z")

  private fun proposal(lenderPercent: Any? = 70L): Map<String, Any?> =
      mapOf("authorId" to "lender", "lenderPercent" to lenderPercent, "createdAt" to Timestamp(at))

  /** A dispute as the Cloud Functions store it, with [changes] applied (null removes a field). */
  private fun stored(vararg changes: Pair<String, Any?>): Map<String, Any?> =
      (mapOf(
              DisputeFields.STAGE to "LLM_PROPOSAL",
              DisputeFields.PROPOSALS to listOf(proposal()),
              DisputeFields.ESCALATES_AT to Timestamp(at),
              DisputeFields.ESCALATED_BY to "borrower",
              DisputeFields.AGREED_AT to Timestamp(at),
          ) + changes)
          .filterValues { it != null }

  @Test
  fun readsEveryField() {
    assertEquals(
        Dispute(
            rentalId = "r1",
            stage = DisputeStage.LLM_PROPOSAL,
            proposals = listOf(Proposal("lender", 70, at)),
            escalatesAt = at,
            agreedAt = at,
            escalatedBy = "borrower",
        ),
        disputeFromDocument("r1", stored()),
    )
  }

  @Test
  fun missingOptionalFieldsReadAsNone() {
    val dispute =
        disputeFromDocument(
            "r1",
            stored(
                DisputeFields.PROPOSALS to null,
                DisputeFields.ESCALATES_AT to null,
                DisputeFields.AGREED_AT to null,
                DisputeFields.ESCALATED_BY to null,
            ),
        )

    assertEquals(Dispute("r1", DisputeStage.LLM_PROPOSAL, emptyList(), null), dispute)
  }

  @Test
  fun aDocumentThatBreaksTheSchemaIsAnError() {
    val broken =
        listOf(
            stored(DisputeFields.STAGE to "ARBITRATION"),
            stored(DisputeFields.STAGE to null),
            stored(DisputeFields.ESCALATES_AT to "2026-10-14"),
            stored(DisputeFields.PROPOSALS to "70"),
            stored(DisputeFields.PROPOSALS to listOf("70")),
            stored(DisputeFields.PROPOSALS to listOf(proposal(lenderPercent = 101L))),
            stored(DisputeFields.PROPOSALS to listOf(proposal(lenderPercent = 12.5))),
            stored(DisputeFields.PROPOSALS to listOf(proposal() - "authorId")),
            stored(DisputeFields.PROPOSALS to listOf(proposal() - "createdAt")),
        )

    broken.forEach { data ->
      assertThrows(IllegalStateException::class.java) { disputeFromDocument("r1", data) }
    }
  }
}
