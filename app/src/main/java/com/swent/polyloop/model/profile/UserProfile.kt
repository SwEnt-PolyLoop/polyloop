// Made with Claude.

package com.swent.polyloop.model.profile

/**
 * A user's profile, stored in Firestore at `users/{uid}` and visible to other users.
 *
 * [averageRating] and [reviewCount] are written only by Cloud Functions when a review is added; the
 * app never writes them.
 */
data class UserProfile(
    val uid: String,
    val name: String,
    val email: String,
    val photoUrl: String = "", // empty until the user adds a photo in Edit profile
    val averageRating: Double = 0.0,
    val reviewCount: Int = 0,
)
