// Made with Claude.

package com.swent.polyloop.model.handover

import com.google.firebase.Timestamp
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HandoverDocumentTest {

  private val valid =
      mapOf(
          HandoverFields.TAKEN_BY to "u1",
          HandoverFields.PHASE to "PICKUP",
          HandoverFields.TAKEN_AT to Timestamp(1_000, 0),
          HandoverFields.URL to "https://example.com/p.jpg",
      )

  @Test
  fun readsAValidPhoto() =
      assertEquals(
          HandoverPhoto(
              "p1",
              HandoverPhase.PICKUP,
              Instant.ofEpochSecond(1_000),
              "https://example.com/p.jpg",
          ),
          photoFromDocument("p1", valid),
      )

  @Test
  fun aMissingOrWrongFieldGivesNoPhoto() {
    val broken =
        listOf(
            valid - HandoverFields.PHASE,
            valid + (HandoverFields.PHASE to "IN_USE"), // no such phase
            valid - HandoverFields.TAKEN_AT,
            valid + (HandoverFields.TAKEN_AT to "yesterday"),
            valid - HandoverFields.URL,
            valid + (HandoverFields.URL to " "),
        )
    broken.forEach { assertNull("$it", photoFromDocument("p1", it)) }
  }
}
