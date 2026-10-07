// Made with Claude.

package com.swent.polyloop.model.listing

/**
 * What the borrower searches for on Browse. Each empty field keeps every listing.
 *
 * Every word of [query] must appear in the title or the description; accents, case, hyphens and
 * punctuation are ignored (see [normalizeForSearch]). Words found in the title rank higher than
 * words found in the description.
 *
 * @property categories the categories to keep; empty keeps all of them.
 * @property minPrice the lowest price per day to keep, in PP, inclusive.
 * @property maxPrice the highest price per day to keep, in PP, inclusive.
 */
data class ListingFilter(
    val query: String = "",
    val categories: Set<ListingCategory> = emptySet(),
    val minPrice: Int? = null,
    val maxPrice: Int? = null,
) {
  private val terms: List<String> = searchTerms(query)

  /**
   * How well [listing] matches the query, higher is better, or null if it does not pass the filter.
   * A listing passing a filter without a query scores 0.
   */
  internal fun score(listing: Listing): Int? {
    if (categories.isNotEmpty() && listing.category !in categories) return null
    if (minPrice != null && listing.pricePerDay < minPrice) return null
    if (maxPrice != null && listing.pricePerDay > maxPrice) return null
    if (terms.isEmpty()) return 0

    val title = SearchableText(listing.title)
    val description = SearchableText(listing.description)
    return terms.sumOf { term -> termScore(term, title, description) ?: return null }
  }

  companion object {
    internal const val TITLE_WORD = 5
    internal const val TITLE_WORD_START = 3
    internal const val DESCRIPTION_WORD = 2
    internal const val DESCRIPTION_WORD_START = 1
    internal const val JOINED_TEXT = 1

    /**
     * Shorter terms are not searched in the text with its spaces removed: a short term would match
     * across word boundaries by chance.
     */
    internal const val MIN_JOINED_TERM_LENGTH = 4
  }
}

/**
 * The listings passing [filter], best match first. Listings with the same score keep their order,
 * so without a query the list order is unchanged.
 */
fun List<Listing>.search(filter: ListingFilter): List<Listing> {
  val scored = mapNotNull { listing -> filter.score(listing)?.let { score -> listing to score } }
  return scored.sortedByDescending { (_, score) -> score }.map { (listing, _) -> listing }
}

/** A text as search reads it: its normalized words, and those words joined without spaces. */
private class SearchableText(text: String) {
  val words: List<String> = normalizeForSearch(text).split(' ').filter { it.isNotEmpty() }
  val joined: String = words.joinToString("")
}

/**
 * The distinct normalized words of [query]. One-letter words are dropped, since they match almost
 * everything, unless the query has nothing else.
 */
private fun searchTerms(query: String): List<String> {
  val words = normalizeForSearch(query).split(' ').filter { it.isNotEmpty() }.distinct()
  return words.filter { it.length > 1 }.ifEmpty { words }
}

/** The best score of [term] in the listing's [title] or [description], or null if absent. */
private fun termScore(term: String, title: SearchableText, description: SearchableText): Int? =
    when {
      term in title.words -> ListingFilter.TITLE_WORD
      title.words.any { it.startsWith(term) } -> ListingFilter.TITLE_WORD_START
      term in description.words -> ListingFilter.DESCRIPTION_WORD
      description.words.any { it.startsWith(term) } -> ListingFilter.DESCRIPTION_WORD_START
      term.length >= ListingFilter.MIN_JOINED_TERM_LENGTH &&
          (term in title.joined || term in description.joined) -> ListingFilter.JOINED_TEXT
      else -> null
    }
