// Made with Claude.

package com.swent.polyloop.model.listing

import com.swent.polyloop.model.listing.ListingFilter.Companion.DESCRIPTION_WORD
import com.swent.polyloop.model.listing.ListingFilter.Companion.DESCRIPTION_WORD_START
import com.swent.polyloop.model.listing.ListingFilter.Companion.JOINED_TEXT
import com.swent.polyloop.model.listing.ListingFilter.Companion.TITLE_WORD
import com.swent.polyloop.model.listing.ListingFilter.Companion.TITLE_WORD_START
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListingFilterTest {

  private fun listing(
      id: String = "listing1",
      title: String = "Camping tent",
      description: String = "Light tent for two people.",
      category: ListingCategory = ListingCategory.SPORTS_OUTDOOR,
      pricePerDay: Int = 15,
  ) =
      Listing(
          id = id,
          ownerId = "user1",
          title = title,
          description = description,
          category = category,
          photoUrls = List(3) { "https://example.com/photo$it.jpg" },
          pricePerDay = pricePerDay,
          itemValue = 180,
          availableFrom = LocalDate.of(2026, 10, 6),
          availableTo = LocalDate.of(2026, 10, 20),
          status = ListingStatus.PUBLISHED,
      )

  private val tent = listing()

  // Filters without a query

  @Test fun emptyFilterKeepsListing() = assertEquals(0, ListingFilter().score(tent))

  @Test fun blankQueryKeepsListing() = assertEquals(0, ListingFilter(query = " - ").score(tent))

  @Test
  fun selectedCategoryKeepsListing() =
      assertEquals(
          0,
          ListingFilter(categories = setOf(ListingCategory.SPORTS_OUTDOOR)).score(tent),
      )

  @Test
  fun otherCategoryExcludesListing() =
      assertNull(ListingFilter(categories = setOf(ListingCategory.MUSIC)).score(tent))

  @Test
  fun severalCategoriesKeepListingOfAnyOfThem() =
      assertEquals(
          0,
          ListingFilter(categories = setOf(ListingCategory.MUSIC, ListingCategory.SPORTS_OUTDOOR))
              .score(tent),
      )

  @Test
  fun priceAboveMinimumKeepsListing() = assertEquals(0, ListingFilter(minPrice = 10).score(tent))

  @Test
  fun priceBelowMinimumExcludesListing() = assertNull(ListingFilter(minPrice = 16).score(tent))

  @Test
  fun priceBelowMaximumKeepsListing() = assertEquals(0, ListingFilter(maxPrice = 20).score(tent))

  @Test
  fun priceAboveMaximumExcludesListing() = assertNull(ListingFilter(maxPrice = 14).score(tent))

  @Test
  fun priceEqualToBothBoundsKeepsListing() =
      assertEquals(0, ListingFilter(minPrice = 15, maxPrice = 15).score(tent))

  @Test
  fun minimumAboveMaximumExcludesListing() =
      assertNull(ListingFilter(minPrice = 20, maxPrice = 10).score(tent))

  // Score of one term

  @Test
  fun wholeWordInTitleScoresTitleWord() =
      assertEquals(TITLE_WORD, ListingFilter(query = "camping").score(tent))

  @Test
  fun startOfWordInTitleScoresTitleWordStart() =
      assertEquals(TITLE_WORD_START, ListingFilter(query = "camp").score(tent))

  @Test
  fun wholeWordInDescriptionScoresDescriptionWord() =
      assertEquals(DESCRIPTION_WORD, ListingFilter(query = "light").score(tent))

  @Test
  fun startOfWordInDescriptionScoresDescriptionWordStart() =
      assertEquals(DESCRIPTION_WORD_START, ListingFilter(query = "peop").score(tent))

  @Test
  fun termInBothTitleAndDescriptionKeepsBestScore() =
      assertEquals(TITLE_WORD, ListingFilter(query = "tent").score(tent))

  @Test
  fun termAcrossWordsScoresJoinedText() =
      assertEquals(
          JOINED_TEXT,
          ListingFilter(query = "tshirt").score(listing(title = "T-shirt", description = "Blue")),
      )

  @Test
  fun partlyTypedTermAcrossWordsScoresJoinedText() =
      assertEquals(
          JOINED_TEXT,
          ListingFilter(query = "mountainbi")
              .score(listing(title = "Mountain bike", description = "Blue")),
      )

  @Test
  fun termAcrossWordsOfDescriptionScoresJoinedText() =
      assertEquals(
          JOINED_TEXT,
          ListingFilter(query = "tshirt").score(listing(title = "Top", description = "A T-shirt")),
      )

  @Test
  fun termInsideWordIsNotMatched() =
      assertNull(ListingFilter(query = "ring").score(listing(title = "Spring", description = "")))

  @Test
  fun termStartingInsideWordIsNotMatched() =
      assertNull(
          ListingFilter(query = "tainbike")
              .score(listing(title = "Mountain bike", description = ""))
      )

  @Test
  fun shortTermIsNotSearchedAcrossWords() =
      assertNull(ListingFilter(query = "tes").score(listing(title = "T est", description = "")))

  @Test fun missingTermExcludesListing() = assertNull(ListingFilter(query = "guitar").score(tent))

  @Test
  fun termIgnoresAccentsAndCase() =
      assertEquals(
          TITLE_WORD,
          ListingFilter(query = "VELO").score(listing(title = "Vélo-cargo", description = "")),
      )

  // Several terms

  @Test
  fun scoresOfSeveralTermsAddUp() =
      assertEquals(
          TITLE_WORD + DESCRIPTION_WORD,
          ListingFilter(query = "camping light").score(tent),
      )

  @Test
  fun oneMissingTermExcludesListing() =
      assertNull(ListingFilter(query = "camping guitar").score(tent))

  @Test
  fun repeatedTermCountsOnce() =
      assertEquals(TITLE_WORD, ListingFilter(query = "camping Camping").score(tent))

  @Test
  fun oneLetterTermsAreIgnoredNextToLongerOnes() =
      assertEquals(TITLE_WORD, ListingFilter(query = "a camping").score(tent))

  @Test
  fun englishStopWordsAreIgnored() =
      assertEquals(
          TITLE_WORD + DESCRIPTION_WORD,
          ListingFilter(query = "tent with the people").score(tent),
      )

  @Test
  fun frenchStopWordsAreIgnored() =
      assertEquals(
          TITLE_WORD + DESCRIPTION_WORD,
          ListingFilter(query = "perceuse pour la mallette")
              .score(listing(title = "Perceuse", description = "Livrée en mallette")),
      )

  @Test
  fun queryOfOnlyStopWordsIsSearched() =
      assertEquals(DESCRIPTION_WORD, ListingFilter(query = "for").score(tent))

  @Test
  fun queryOfOnlyOneLetterTermsIsSearched() =
      assertEquals(TITLE_WORD_START, ListingFilter(query = "c").score(tent))

  @Test
  fun queryAndFiltersMustAllPass() {
    val filter =
        ListingFilter(
            query = "tent",
            categories = setOf(ListingCategory.SPORTS_OUTDOOR),
            minPrice = 10,
            maxPrice = 20,
        )
    assertEquals(TITLE_WORD, filter.score(tent))
    assertNull(filter.score(tent.copy(category = ListingCategory.MUSIC)))
    assertNull(filter.score(tent.copy(pricePerDay = 25)))
    assertNull(filter.score(tent.copy(title = "Sleeping bag", description = "Warm")))
  }

  // search

  @Test
  fun searchRanksTitleMatchesFirst() {
    val inDescription = listing(id = "a", title = "Sleeping bag", description = "Fits in a tent")
    val inTitle = listing(id = "b", title = "Tent", description = "Waterproof")
    assertEquals(
        listOf(inTitle, inDescription),
        listOf(inDescription, inTitle).search(ListingFilter(query = "tent")),
    )
  }

  @Test
  fun searchKeepsOrderOfEqualScores() {
    val first = listing(id = "a")
    val second = listing(id = "b")
    assertEquals(listOf(first, second), listOf(first, second).search(ListingFilter("tent")))
  }

  @Test
  fun searchWithoutQueryKeepsOrderAndAppliesFilters() {
    val cheap = listing(id = "a", pricePerDay = 5)
    val expensive = listing(id = "b", pricePerDay = 50)
    val guitar = listing(id = "c", category = ListingCategory.MUSIC, pricePerDay = 10)
    assertEquals(
        listOf(cheap, guitar),
        listOf(cheap, expensive, guitar).search(ListingFilter(maxPrice = 20)),
    )
  }

  @Test
  fun searchWithoutMatchReturnsEmptyList() =
      assertEquals(emptyList<Listing>(), listOf(tent).search(ListingFilter(query = "guitar")))

  @Test
  fun searchFindsTextWrittenDifferently() {
    val bike = listing(id = "a", title = "Vélo-cargo", description = "")
    val shirt = listing(id = "b", title = "T-shirt EPFL", description = "")
    val heart = listing(id = "c", title = "Cœur en peluche", description = "")
    val listings = listOf(bike, shirt, heart)
    assertEquals(listOf(bike), listings.search(ListingFilter(query = "velo")))
    assertEquals(listOf(shirt), listings.search(ListingFilter(query = "tshirt")))
    assertEquals(listOf(heart), listings.search(ListingFilter(query = "coeur")))
  }
}
