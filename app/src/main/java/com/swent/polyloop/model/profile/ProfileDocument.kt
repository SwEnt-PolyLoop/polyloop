// Made with Claude.

package com.swent.polyloop.model.profile

/**
 * Firestore names for profiles, kept in one place so they are easy to change. Proposed in #45, not
 * agreed by the team yet.
 */
internal object ProfileFields {
  const val COLLECTION = "users"
  const val NAME = "name"
  const val EMAIL = "email"
  const val PHOTO_URL = "photoUrl"
  const val AVERAGE_RATING = "averageRating"
  const val REVIEW_COUNT = "reviewCount"
}

/**
 * Builds the [UserProfile] of [uid] from its Firestore document. Never drops a profile: a missing
 * or wrongly typed field gets its default ("" for text, 0 for the rating), e.g. for profiles
 * created before the rating fields existed.
 */
internal fun profileFromDocument(uid: String, data: Map<String, Any?>): UserProfile =
    UserProfile(
        uid = uid,
        name = data[ProfileFields.NAME] as? String ?: "",
        email = data[ProfileFields.EMAIL] as? String ?: "",
        photoUrl = data[ProfileFields.PHOTO_URL] as? String ?: "",
        // Firestore returns whole numbers as Long and others as Double, so accept any number.
        averageRating = (data[ProfileFields.AVERAGE_RATING] as? Number)?.toDouble() ?: 0.0,
        reviewCount = (data[ProfileFields.REVIEW_COUNT] as? Number)?.toInt() ?: 0,
    )
