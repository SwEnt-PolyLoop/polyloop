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
  /** Any character that is neither a letter nor a digit, a space included. */
  SPECIAL({ password -> password.any { !it.isLetterOrDigit() } });

  companion object {
    const val MIN_LENGTH_CHARS = 8

    /** The rules [password] breaks, in declaration order; empty when it follows them all. */
    fun failedRules(password: String): List<PasswordRule> = entries.filterNot { it.isMet(password) }

    /**
     * [password] without its emojis, which a new password cannot contain. Removes every character
     * outside the basic plane (where most emojis live), the symbols from U+2000 up (☺, ♥, ✔, ⭐),
     * and the invisible characters that build emojis (joiner, emoji style, keycap). Letters, digits
     * and keyboard symbols such as "€", "°" or "©" are kept.
     */
    fun withoutEmojis(password: String): String =
        password.codePoints().filter { !isEmojiPart(it) }.toArray().let { String(it, 0, it.size) }

    private fun isEmojiPart(codePoint: Int): Boolean =
        Character.isSupplementaryCodePoint(codePoint) ||
            (codePoint >= FIRST_EMOJI_SYMBOL &&
                Character.getType(codePoint) == Character.OTHER_SYMBOL.toInt()) ||
            codePoint in EMOJI_BUILDERS

    private const val FIRST_EMOJI_SYMBOL = 0x2000

    /** Zero-width joiner, emoji-style variation selector and combining keycap. */
    private val EMOJI_BUILDERS = setOf(0x200D, 0xFE0F, 0x20E3)
  }
}
