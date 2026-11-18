// Made with Claude.

package com.swent.polyloop.model.handover

import java.security.MessageDigest

/**
 * The secret code of a handover, shown as a QR code by the person giving the item.
 *
 * The person scanning only has the code's fingerprint: its SHA-256 hash, written as 64 lowercase
 * hex characters. The fingerprint is enough to check a scanned code with no connection, but the
 * code cannot be worked out from it.
 */
object HandoverCode {

  /** The fingerprint of [code]: its SHA-256 hash in lowercase hex. */
  fun fingerprint(code: String): String {
    val hash = MessageDigest.getInstance("SHA-256").digest(code.toByteArray(Charsets.UTF_8))
    // Each of the 32 bytes becomes 2 hex characters, e.g. 10 -> "0a".
    return hash.joinToString(separator = "") { byte -> "%02x".format(byte) }
  }

  /**
   * True when the scanned [code] has the fingerprint [expected]. A blank code or a blank
   * fingerprint never matches, and the fingerprint's letters can be in either case.
   */
  fun matches(code: String, expected: String): Boolean =
      code.isNotBlank() && expected.isNotBlank() && fingerprint(code) == expected.lowercase()
}
