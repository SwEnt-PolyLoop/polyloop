// Made with Claude.

package com.swent.polyloop.model.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileDocumentTest {

  @Test
  fun readsEveryField() {
    val data =
        mapOf(
            "name" to "John",
            "email" to "john@epfl.ch",
            "photoUrl" to "https://example.com/john.jpg",
            "averageRating" to 4.5,
            "reviewCount" to 2L,
        )

    assertEquals(
        UserProfile("u1", "John", "john@epfl.ch", "https://example.com/john.jpg", 4.5, 2),
        profileFromDocument("u1", data),
    )
  }

  @Test
  fun profileWithoutRatingFieldsReadsZero() {
    // Like the seeded profiles, created before the rating fields existed.
    val data = mapOf("name" to "Vida", "photoUrl" to "https://example.com/vida.jpg")

    val profile = profileFromDocument("seed-vida", data)

    assertEquals(0.0, profile.averageRating, 0.0)
    assertEquals(0, profile.reviewCount)
    assertEquals("Vida", profile.name)
  }

  @Test
  fun emptyDocumentReadsDefaults() {
    assertEquals(UserProfile("u1", "", "", "", 0.0, 0), profileFromDocument("u1", emptyMap()))
  }

  @Test
  fun wholeNumberRatingIsAccepted() {
    // Firestore returns 4 as a Long, not a Double.
    val profile = profileFromDocument("u1", mapOf("averageRating" to 4L))

    assertEquals(4.0, profile.averageRating, 0.0)
  }

  @Test
  fun wronglyTypedFieldsReadDefaults() {
    val data =
        mapOf(
            "name" to 42L,
            "email" to true,
            "photoUrl" to null,
            "averageRating" to "high",
            "reviewCount" to "two",
        )

    assertEquals(UserProfile("u1", "", "", "", 0.0, 0), profileFromDocument("u1", data))
  }
}
