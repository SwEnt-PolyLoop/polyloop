// Made with Claude.

package com.swent.polyloop.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenTest {

  @Test
  fun topLevelDestinationsFollowMenuOrderWithoutAdminScreen() {
    assertEquals(
        listOf(
            Screen.Browse,
            Screen.CreateListing,
            Screen.MyRentals,
            Screen.ChatList,
            Screen.Wallet,
            Screen.Profile,
        ),
        Screen.topLevelDestinations(isAdmin = false),
    )
  }

  @Test
  fun topLevelDestinationsEndWithAdminScreenForAdmins() {
    assertEquals(
        Screen.topLevelDestinations(isAdmin = false) + Screen.AdminCourtRuling,
        Screen.topLevelDestinations(isAdmin = true),
    )
  }

  @Test
  fun createRouteFillsInTheArgument() {
    assertEquals("listing_detail/l1", Screen.ListingDetail.createRoute("l1"))
    assertEquals("edit_listing/l1", Screen.EditListing.createRoute("l1"))
    assertEquals("rental_detail/r1", Screen.RentalDetail.createRoute("r1"))
    assertEquals("chat/r1", Screen.Chat.createRoute("r1"))
    assertEquals("handover/r1", Screen.Handover.createRoute("r1"))
    assertEquals("dispute/r1", Screen.Dispute.createRoute("r1"))
    assertEquals("profile/u1", Screen.UserProfile.createRoute("u1"))
  }
}
