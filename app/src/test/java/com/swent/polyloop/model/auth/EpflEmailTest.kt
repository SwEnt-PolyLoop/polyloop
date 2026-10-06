package com.swent.polyloop.model.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpflEmailTest {

  @Test
  fun epflEmailIsValid() {
    assertTrue(EpflEmail.isValid("john@epfl.ch"))
  }

  @Test
  fun uppercaseEpflEmailIsValid() {
    assertTrue(EpflEmail.isValid("John.Doe@EPFL.CH"))
  }

  @Test
  fun epflEmailWithSpacesAroundIsValid() {
    assertTrue(EpflEmail.isValid("  john@epfl.ch  "))
  }

  @Test
  fun subdomainIsRejected() {
    assertFalse(EpflEmail.isValid("john@student.epfl.ch"))
  }

  @Test
  fun otherDomainEndingWithEpflIsRejected() {
    assertFalse(EpflEmail.isValid("john@notepfl.ch"))
  }

  @Test
  fun emailWithoutAtIsRejected() {
    assertFalse(EpflEmail.isValid("johnepfl.ch"))
  }

  @Test
  fun emailWithEmptyNameIsRejected() {
    assertFalse(EpflEmail.isValid("@epfl.ch"))
  }

  @Test
  fun emailWithSpaceInNameIsRejected() {
    assertFalse(EpflEmail.isValid("jo hn@epfl.ch"))
  }

  @Test
  fun emailWithTwoAtsIsRejected() {
    assertFalse(EpflEmail.isValid("a@b@epfl.ch"))
  }

  @Test
  fun emailWithTwoDotsInARowIsRejected() {
    assertFalse(EpflEmail.isValid("john..doe@epfl.ch"))
  }

  @Test
  fun emailStartingWithDotIsRejected() {
    assertFalse(EpflEmail.isValid(".john@epfl.ch"))
  }

  @Test
  fun emailEndingWithDotIsRejected() {
    assertFalse(EpflEmail.isValid("john.@epfl.ch"))
  }

  @Test
  fun emailWithAccentedLetterIsRejected() {
    assertFalse(EpflEmail.isValid("jöhn@epfl.ch"))
  }

  @Test
  fun emailWithPlusIsRejected() {
    assertFalse(EpflEmail.isValid("john+test@epfl.ch"))
  }

  @Test
  fun emailWithHyphenUnderscoreAndDigitsIsValid() {
    assertTrue(EpflEmail.isValid("marie-claire_dupont2@epfl.ch"))
  }

  @Test
  fun normalizeTrimsAndLowercases() {
    assertEquals("john.doe@epfl.ch", EpflEmail.normalize("  John.Doe@EPFL.ch "))
  }
}
