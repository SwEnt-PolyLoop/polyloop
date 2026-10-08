// Made with Claude.

package com.swent.polyloop.model.listing

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchTextTest {

  @Test fun lowercasesText() = assertEquals("camping tent", normalizeForSearch("Camping TENT"))

  @Test fun removesAccents() = assertEquals("eeeacun", normalizeForSearch("éèêàçüñ"))

  @Test
  fun replacesLettersThatAreNotAccented() =
      assertEquals(
          "coeur strasse aesir oslo lodz",
          normalizeForSearch("Cœur Straße Æsir Øslo Łodz"),
      )

  @Test
  fun replacesHyphensWithSpaces() = assertEquals("velo cargo", normalizeForSearch("Vélo-cargo"))

  @Test
  fun replacesApostrophesWithSpaces() = assertEquals("l appareil", normalizeForSearch("l'appareil"))

  @Test
  fun replacesPunctuationAndEmojisWithSpaces() =
      assertEquals("tent 2 people", normalizeForSearch("Tent!! (2 people) 🏕️"))

  @Test
  fun collapsesAndTrimsSpaces() = assertEquals("tent 2", normalizeForSearch("  tent \t\n  2  "))

  @Test fun keepsDigits() = assertEquals("iphone 13", normalizeForSearch("iPhone 13"))

  @Test fun keepsLettersOfOtherScripts() = assertEquals("книга", normalizeForSearch("Книга"))

  @Test fun emptyTextStaysEmpty() = assertEquals("", normalizeForSearch(""))

  @Test fun onlySeparatorsBecomeEmpty() = assertEquals("", normalizeForSearch(" - ' ! "))
}
