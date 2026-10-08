// Made with Claude.

package com.swent.polyloop.model.listing

import java.text.Normalizer

/** Letters that are not an accented form of another letter, so removing accents keeps them. */
private val SPECIAL_LETTERS = mapOf('œ' to "oe", 'æ' to "ae", 'ß' to "ss", 'ø' to "o", 'ł' to "l")

private val ACCENT_MARKS = Regex("\\p{M}+")
private val NON_ALPHANUMERIC = Regex("[^\\p{L}\\p{N}]+")

/**
 * [text] reduced to what search compares: lowercase letters and digits without accents, separated
 * by single spaces. Accents are removed and any other character (hyphen, apostrophe, punctuation,
 * emoji) becomes a space, so "Vélo-cargo" and "velo cargo" compare equal.
 */
internal fun normalizeForSearch(text: String): String {
  val lowercase = text.lowercase()
  val withoutSpecialLetters = buildString {
    for (char in lowercase) append(SPECIAL_LETTERS[char] ?: char)
  }
  val withoutAccents =
      Normalizer.normalize(withoutSpecialLetters, Normalizer.Form.NFD).replace(ACCENT_MARKS, "")
  return withoutAccents.replace(NON_ALPHANUMERIC, " ").trim()
}
