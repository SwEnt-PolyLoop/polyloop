// Made with Claude.

package com.swent.polyloop.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.polyloop.model.rental.RentalStatus
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.navigation.Screen
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Losing the connection: Firestore is cut off the network, so what stays readable comes from its
 * cache, as on a phone without a connection.
 */
@RunWith(AndroidJUnit4::class)
class OfflineEndToEndTest : EndToEndTestBase() {

  @Test
  fun offlineKeepsMyRentalsAndTheWalletAndBrowseReloadsOnceOnline() {
    seedProfile(lenderId, LENDER_NAME)
    val listing = seedListing()
    val uid = signInWithoutTheUi()
    val rentalId =
        seedRental(
            listing,
            uid,
            USER_NAME,
            LENDER_NAME,
            firstDay,
            firstDay.plusDays(1),
            RentalStatus.REQUESTED,
        )
    seedWallet(uid, BALANCE)
    setApp()
    val card = C.Tag.listing_card_ + listing.id
    val row = C.Tag.my_rentals_row_ + rentalId

    // Online: the screens load once, which fills Firestore's cache.
    searchOnBrowse()
    waitForTag(card)
    openFromMenu(Screen.MyRentals)
    composeTestRule.onNodeWithTag(C.Tag.my_rentals_tab_borrowing).performClick()
    waitForTag(row)
    openFromMenu(Screen.Wallet)
    waitForText(C.Tag.wallet_available, BALANCE.toString())

    // Offline: Browse shows the banner and an empty state that leads to My rentals.
    goOffline()
    openFromMenu(Screen.Browse)
    waitForTag(C.Tag.browse_offline)
    composeTestRule.onNodeWithTag(C.Tag.offline_banner).assertIsDisplayed()
    composeTestRule.onNodeWithTag(card).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.browse_offline_my_rentals_button).performClick()

    // My rentals and the wallet still read from the cache; recharging is disabled.
    assertRoute(Screen.MyRentals)
    composeTestRule.onNodeWithTag(C.Tag.my_rentals_tab_borrowing).performClick()
    waitForTag(row)
    openFromMenu(Screen.Wallet)
    waitForText(C.Tag.wallet_available, BALANCE.toString())
    composeTestRule.onNodeWithTag(C.Tag.offline_banner).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.wallet_recharge_button).assertIsNotEnabled()

    // A listing published meanwhile, which only a new load from the server can show.
    val publishedOffline = seedListing(title = "Kettle $titleWord")

    // Back online: the banner goes and Browse loads the listings again.
    goOnline()
    openFromMenu(Screen.Browse)
    searchOnBrowse()
    waitForTag(C.Tag.listing_card_ + publishedOffline.id)
    composeTestRule.onNodeWithTag(C.Tag.offline_banner).assertDoesNotExist()
  }

  private fun searchOnBrowse() {
    waitForTag(C.Tag.browse_search_bar)
    composeTestRule.onNodeWithTag(C.Tag.browse_search_bar).performTextReplacement(titleWord)
  }

  private companion object {
    const val BALANCE = 50
  }
}
