// Made with Claude.

package com.swent.polyloop.utils

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.polyloop.utils.FirebaseEmulator.AUTH_PORT
import com.swent.polyloop.utils.FirebaseEmulator.FIRESTORE_PORT
import com.swent.polyloop.utils.FirebaseEmulator.NOT_RUNNING
import com.swent.polyloop.utils.FirebaseEmulator.PAUSE_MS
import com.swent.polyloop.utils.FirebaseEmulator.STORAGE_PORT
import com.swent.polyloop.utils.FirebaseEmulator.TRIES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/** The emulator check, with a fake probe and no real wait (no emulator needed). */
@RunWith(AndroidJUnit4::class)
class FirebaseEmulatorTest {

  private val tries = mutableListOf<Int>()
  private val pauses = mutableListOf<Long>()

  /** A probe where every emulator is down. */
  private val down: (Int) -> Boolean = { port ->
    tries += port
    false
  }

  @Test
  fun anEmulatorThatAnswersOnTheThirdTryCountsAsRunning() {
    // Auth is asked first; the other ports answer at once.
    FirebaseEmulator.requireRunning(
        isUp = { port ->
          tries += port
          port != AUTH_PORT || tries.size == 3
        },
        pause = { pauses += it },
    )
    assertEquals(listOf(AUTH_PORT, AUTH_PORT, AUTH_PORT, FIRESTORE_PORT, STORAGE_PORT), tries)
    assertEquals(List(2) { PAUSE_MS }, pauses)
  }

  @Test
  fun anEmulatorThatNeverAnswersFailsAfterEveryTryWithTheSameMessage() {
    val error =
        assertThrows(AssertionError::class.java) {
          FirebaseEmulator.requireRunning(isUp = down, pause = { pauses += it })
        }
    assertEquals(NOT_RUNNING, error.message)
    assertEquals(List(TRIES) { AUTH_PORT }, tries)
    assertEquals(List(TRIES - 1) { PAUSE_MS }, pauses)
  }

  @Test
  fun aSingleTryAsksOnceWithoutPausing() {
    assertThrows(AssertionError::class.java) {
      FirebaseEmulator.requireRunning(isUp = down, pause = { pauses += it }, tries = 1)
    }
    assertEquals(listOf(AUTH_PORT), tries)
    assertEquals(emptyList<Long>(), pauses)
  }
}
