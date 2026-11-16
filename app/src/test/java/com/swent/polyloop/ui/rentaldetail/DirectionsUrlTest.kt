// Made with Claude.

package com.swent.polyloop.ui.rentaldetail

import org.junit.Assert.assertEquals
import org.junit.Test

/** The directions link of the Navigate button. */
class DirectionsUrlTest {

  private val prefix = "https://www.google.com/maps/dir/?api=1&destination="

  @Test
  fun directionsUrlEncodesSpacesAsPercent20AndAccentsAsUtf8() {
    assertEquals(
        prefix + "Chemin%20du%20L%C3%A9man%201%2C%20Ecublens",
        directionsUrl("Chemin du Léman 1, Ecublens"),
    )
  }

  @Test
  fun directionsUrlEncodesCharactersThatWouldEndOrSplitTheDestination() {
    // A raw "&" would start a new parameter, "#" a fragment, "+" would read as a space.
    assertEquals(
        prefix + "Rue%20A%20%26%20B%2F2%20%2310%2B",
        directionsUrl("Rue A & B/2 #10+"),
    )
  }
}
