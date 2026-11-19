// Made with Claude.

package com.swent.polyloop.model.handover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HandoverPhotoSlotTest {

  @Test
  fun slotIdIsTheUidThePhaseAndTheNumber() {
    assertEquals("u1_PICKUP_0", photoSlotId("u1", HandoverPhase.PICKUP, 0))
    assertEquals("u1_RETURN_19", photoSlotId("u1", HandoverPhase.RETURN, 19))
  }

  @Test
  fun firstSlotIsFreeWithoutPhotos() {
    assertEquals(0, lowestFreeSlot("u1", HandoverPhase.PICKUP, emptyList()))
  }

  @Test
  fun lowestFreeSlotSkipsTheUsedOnes() {
    val ids = listOf("u1_PICKUP_0", "u1_PICKUP_1", "u1_PICKUP_3")

    assertEquals(2, lowestFreeSlot("u1", HandoverPhase.PICKUP, ids))
  }

  @Test
  fun otherPhasesUsersAndRandomIdsUseNoSlot() {
    val ids = listOf("u1_RETURN_0", "u2_PICKUP_0", "x7Kq9Zr2", "u1_PICKUP_00")

    assertEquals(0, lowestFreeSlot("u1", HandoverPhase.PICKUP, ids))
  }

  @Test
  fun lastSlotIsUsedBeforeNoneIsFree() {
    val ids = List(HandoverPhoto.MAX_PHOTOS - 1) { "u1_PICKUP_$it" }

    assertEquals(HandoverPhoto.MAX_PHOTOS - 1, lowestFreeSlot("u1", HandoverPhase.PICKUP, ids))
  }

  @Test
  fun noSlotIsFreeWhenAllAreUsed() {
    val ids = List(HandoverPhoto.MAX_PHOTOS) { "u1_PICKUP_$it" }

    assertNull(lowestFreeSlot("u1", HandoverPhase.PICKUP, ids))
  }
}
