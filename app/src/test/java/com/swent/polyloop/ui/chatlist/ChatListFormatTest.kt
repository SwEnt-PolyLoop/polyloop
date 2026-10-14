// Made with Claude.

package com.swent.polyloop.ui.chatlist

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatListFormatTest {

  private val zurich = ZoneId.of("Europe/Zurich")

  /** Formats [at] as seen on [now], both written in Zurich time, e.g. "2026-10-13T15:02". */
  private fun format(at: String, now: String, zone: ZoneId = zurich) =
      formatMessageTime(
          at = LocalDateTime.parse(at).atZone(zone).toInstant(),
          now = LocalDateTime.parse(now).atZone(zone).toInstant(),
          zone = zone,
          locale = Locale.ENGLISH,
      )

  // ---------- formatMessageTime ----------

  @Test
  fun messageFromTodayShowsTheTime() =
      assertEquals("15:02", format("2026-10-13T15:02", now = "2026-10-13T18:00"))

  @Test
  fun messageJustAfterMidnightIsStillToday() =
      assertEquals("00:01", format("2026-10-13T00:01", now = "2026-10-13T23:59"))

  @Test
  fun messageFromLateYesterdayShowsTheDay() =
      assertEquals("12 Oct", format("2026-10-12T23:59", now = "2026-10-13T00:01"))

  @Test
  fun olderMessageShowsTheDay() =
      assertEquals("1 Aug", format("2026-08-01T09:30", now = "2026-10-13T12:00"))

  @Test
  fun messageFromAnotherYearShowsTheDay() =
      assertEquals("31 Dec", format("2025-12-31T20:00", now = "2026-01-01T10:00"))

  @Test
  fun todayDependsOnTheTimeZone() {
    // 23:30 UTC on 12 Oct is already 13 Oct, 01:30 in Zurich.
    val at = Instant.parse("2026-10-12T23:30:00Z")
    val now = Instant.parse("2026-10-13T08:00:00Z")

    assertEquals("01:30", formatMessageTime(at, now, zurich, Locale.ENGLISH))
    assertEquals("12 Oct", formatMessageTime(at, now, ZoneId.of("UTC"), Locale.ENGLISH))
  }

  @Test
  fun timeUses24Hours() =
      assertEquals("21:45", format("2026-10-13T21:45", now = "2026-10-13T22:00"))

  // ---------- initialOf ----------

  @Test fun initialIsTheFirstLetterInUpperCase() = assertEquals("A", initialOf("ana"))

  @Test fun initialIgnoresLeadingSpaces() = assertEquals("B", initialOf("  Ben"))

  @Test
  fun blankNameGivesAQuestionMark() {
    assertEquals("?", initialOf(""))
    assertEquals("?", initialOf("   "))
  }

  @Test fun accentedInitialIsKept() = assertEquals("É", initialOf("élodie"))
}
