// Made with Claude.

package com.swent.polyloop.utils

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/** The emulator check, with a fake probe and no real wait (no emulator needed). */
@RunWith(AndroidJUnit4::class)
class FirebaseEmulatorTest {

  private val tries = mutableListOf<Int>()
  private val pauses = mutableListOf<Long>()

  @Test
  fun anEmulatorThatAnswersOnTheThirdTryCountsAsRunning() {
    // Auth (9099) is asked first; the other ports answer at once.
    FirebaseEmulator.requireRunning(
        isUp = { port ->
          tries += port
          port != 9099 || tries.size == 3
        },
        pause = { pauses += it },
    )
    assertEquals(listOf(9099, 9099, 9099, 8080, 9199), tries)
    assertEquals(listOf(1000L, 1000L), pauses)
  }

  @Test
  fun anEmulatorThatNeverAnswersFailsAfterFiveTriesWithTheSameMessage() {
    val error =
        assertThrows(AssertionError::class.java) {
          FirebaseEmulator.requireRunning(
              isUp = {
                tries += it
                false
              },
              pause = { pauses += it },
          )
        }
    assertEquals(
        "The Firebase emulators are not running. Start them with: firebase emulators:start",
        error.message,
    )
    assertEquals(List(5) { 9099 }, tries)
    assertEquals(List(4) { 1000L }, pauses)
  }
}
