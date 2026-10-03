package com.swent.polyloop.model.auth

object EpflEmail {
  const val DOMAIN = "epfl.ch"

  /** Trims spaces and lowercases the email. */
  fun normalize(email: String): String = email.trim().lowercase()

  /**
   * True for "name@epfl.ch" exactly (after [normalize]); subdomains are rejected. The name may only
   * use a-z, 0-9, '.', '-' and '_', and cannot start or end with '.' or contain "..".
   */
  fun isValid(email: String): Boolean {
    val parts = normalize(email).split("@")
    if (parts.size != 2) return false

    val name = parts[0]
    val domain = parts[1]
    return domain == DOMAIN &&
        name.isNotEmpty() &&
        name.all { it in 'a'..'z' || it in '0'..'9' || it in ".-_" } &&
        !name.startsWith('.') &&
        !name.endsWith('.') &&
        !name.contains("..")
  }
}
