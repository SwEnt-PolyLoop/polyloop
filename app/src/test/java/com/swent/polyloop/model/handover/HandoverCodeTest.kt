// Made with Claude.

package com.swent.polyloop.model.handover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HandoverCodeTest {

  // SHA-256 of "abc", from the standard's own test vectors (FIPS 180-2).
  private val abcFingerprint = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

  @Test
  fun fingerprintIsTheSha256InLowercaseHex() =
      assertEquals(abcFingerprint, HandoverCode.fingerprint("abc"))

  @Test
  fun fingerprintAlwaysHas64Characters() {
    assertEquals(64, HandoverCode.fingerprint("").length)
    assertEquals(64, HandoverCode.fingerprint("a".repeat(10_000)).length)
  }

  @Test
  fun fingerprintReadsTheCodeAsUtf8() =
      assertEquals(
          // SHA-256 of the two UTF-8 bytes of "é" (c3 a9), from `printf 'é' | shasum -a 256`.
          "4a99557e4033c3539de2eb65472017cad5f9557f7a0625a09f1c3f6e2ba69c4c",
          HandoverCode.fingerprint("é"),
      )

  @Test fun theRightCodeMatches() = assertTrue(HandoverCode.matches("abc", abcFingerprint))

  @Test
  fun anUppercaseFingerprintStillMatches() =
      assertTrue(HandoverCode.matches("abc", abcFingerprint.uppercase()))

  @Test fun anotherCodeDoesNotMatch() = assertFalse(HandoverCode.matches("abd", abcFingerprint))

  @Test fun theCodeIsCaseSensitive() = assertFalse(HandoverCode.matches("ABC", abcFingerprint))

  @Test
  fun aCodeWithExtraSpacesDoesNotMatch() =
      assertFalse(HandoverCode.matches(" abc ", abcFingerprint))

  @Test
  fun theFingerprintItselfScannedAsACodeDoesNotMatch() =
      assertFalse(HandoverCode.matches(abcFingerprint, abcFingerprint))

  @Test
  fun aBlankCodeNeverMatches() {
    assertFalse(HandoverCode.matches("", HandoverCode.fingerprint("")))
    assertFalse(HandoverCode.matches("   ", HandoverCode.fingerprint("   ")))
  }

  @Test
  fun aBlankFingerprintNeverMatches() {
    assertFalse(HandoverCode.matches("abc", ""))
    assertFalse(HandoverCode.matches("abc", "   "))
  }

  @Test
  fun aTruncatedFingerprintDoesNotMatch() =
      assertFalse(HandoverCode.matches("abc", abcFingerprint.dropLast(1)))

  @Test
  fun aLongRandomLikeCodeMatchesItsOwnFingerprintOnly() {
    val code = "9f2c4e1a7b3d8f6e0a5c2b9d4e7f1a3c"
    val other = "9f2c4e1a7b3d8f6e0a5c2b9d4e7f1a3d"
    assertTrue(HandoverCode.matches(code, HandoverCode.fingerprint(code)))
    assertFalse(HandoverCode.matches(other, HandoverCode.fingerprint(code)))
  }
}
