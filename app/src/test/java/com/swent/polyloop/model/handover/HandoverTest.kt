// Made with Claude.

package com.swent.polyloop.model.handover

import com.swent.polyloop.model.rental.RentalStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HandoverTest {

  @Test
  fun acceptedRentalTakesPickupPhotos() =
      assertEquals(HandoverPhase.PICKUP, HandoverPhase.fromStatus(RentalStatus.ACCEPTED))

  @Test
  fun everyOtherStatusTakesNoPhotos() {
    val others = RentalStatus.entries - RentalStatus.ACCEPTED
    others.forEach { assertNull("$it", HandoverPhase.fromStatus(it)) }
  }
}
