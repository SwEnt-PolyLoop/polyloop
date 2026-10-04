package com.swent.polyloop.model.auth

/**
 * The login account of a signed-in user (always verified). Profile details live in Firestore,
 * linked by [uid].
 */
data class AuthUser(val uid: String, val email: String)
