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
  // ASCII only, like the Firebase password policy: "٣" does not count.
  DIGIT({ password -> password.any { it in '0'..'9' } }),
  /** One of [SPECIAL_CHARACTERS]; other symbols, spaces and emojis do not count. */
  SPECIAL({ password -> password.any { it in PasswordRule.SPECIAL_CHARACTERS } });

  companion object {
    const val MIN_LENGTH_CHARS = 8

    /**
     * The longest password the Firebase password policy accepts. Not listed as a rule: nobody types
     * that much, so a longer one only gets [AuthError.PASSWORD_TOO_LONG] when signing up.
     */
    const val MAX_LENGTH_CHARS = 4096

    /**
     * The special characters accepted at sign-up; the Firebase password policy accepts them all.
     */
    const val SPECIAL_CHARACTERS = "^$*.[]{}()?\"!@#%&/\\,><':;|_~`"

    /** The rules [password] breaks, in declaration order; empty when it follows them all. */
    fun failedRules(password: String): List<PasswordRule> = entries.filterNot { it.isMet(password) }
  }
}
