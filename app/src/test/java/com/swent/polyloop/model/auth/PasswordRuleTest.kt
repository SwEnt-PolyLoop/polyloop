// Made with Claude.

package com.swent.polyloop.model.auth

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Checks each rule on its own: every password below breaks at most one rule, except the empty one,
 * which breaks them all.
 */
class PasswordRuleTest {

  private fun assertFails(password: String, vararg rules: PasswordRule) {
    assertEquals(rules.toList(), PasswordRule.failedRules(password))
  }

  @Test fun validPasswordBreaksNoRule() = assertFails(VALID)

  @Test fun sevenCharactersBreakMinLength() = assertFails("Abc12!x", PasswordRule.MIN_LENGTH)

  @Test fun eightCharactersFollowMinLength() = assertFails("Abc12!xy")

  @Test fun noUppercaseBreaksUppercase() = assertFails("secret12!", PasswordRule.UPPERCASE)

  @Test fun noLowercaseBreaksLowercase() = assertFails("SECRET12!", PasswordRule.LOWERCASE)

  @Test fun noNumberBreaksDigit() = assertFails("Secretxy!", PasswordRule.DIGIT)

  @Test fun noSpecialCharacterBreaksSpecial() = assertFails("Secret123", PasswordRule.SPECIAL)

  @Test
  fun everyAcceptedSpecialCharacterFollowsSpecial() {
    // Written out again rather than read from PasswordRule, so a changed list fails the test.
    "^$*.[]{}()?\"!@#%&/\\,><':;|_~`".forEach { assertFails("Secret12$it") }
  }

  @Test
  fun otherSymbolsBreakSpecial() {
    listOf(" ", "€", "😀", "=", "+", "-", "é").forEach {
      assertFails("Secret12$it", PasswordRule.SPECIAL)
    }
  }

  @Test
  fun emptyPasswordBreaksEveryRuleInOrder() =
      assertFails(
          "",
          PasswordRule.MIN_LENGTH,
          PasswordRule.UPPERCASE,
          PasswordRule.LOWERCASE,
          PasswordRule.DIGIT,
          PasswordRule.SPECIAL,
      )

  private companion object {
    const val VALID = "Secret12!"
  }
}
