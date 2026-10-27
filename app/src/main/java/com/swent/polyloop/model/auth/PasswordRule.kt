// Made with Claude.

package com.swent.polyloop.model.auth

/**
 * A rule a new password must follow at sign-up. The entries are declared in the order their errors
 * are shown.
 *
 * @property isMet true when the password follows this rule.
 */
enum class PasswordRule(private val isMet: (String) -> Boolean) {
  MIN_LENGTH({ it.length >= PasswordRule.MIN_LENGTH_CHARS }),
  UPPERCASE({ password -> password.any { it.isUpperCase() } }),
  LOWERCASE({ password -> password.any { it.isLowerCase() } }),
  DIGIT({ password -> password.any { it.isDigit() } }),
  /** One of [SPECIAL_CHARACTERS]; other symbols, spaces and emojis do not count. */
  SPECIAL({ password -> password.any { it in PasswordRule.SPECIAL_CHARACTERS } });

  companion object {
    const val MIN_LENGTH_CHARS = 8

    /** The special characters the Firebase password policy accepts. */
    const val SPECIAL_CHARACTERS = "^$*.[]{}()?\"!@#%&/\\,><':;|_~`"

    /** The rules [password] breaks, in declaration order; empty when it follows them all. */
    fun failedRules(password: String): List<PasswordRule> = entries.filterNot { it.isMet(password) }
  }
}
