// Made with Claude.

package com.swent.polyloop.ui.navigation

import androidx.annotation.StringRes
import com.swent.polyloop.R

/**
 * Every destination of the app (see docs/SCREENS.md).
 *
 * [route] is the pattern registered in the NavHost. Destinations that take an argument build their
 * concrete route with `createRoute(...)`.
 */
sealed class Screen(val route: String) {

  /** A destination listed in the top-bar menu, under [labelRes]. */
  sealed class TopLevel(route: String, @StringRes val labelRes: Int) : Screen(route)

  data object Auth : Screen("auth")

  data object Browse : TopLevel("browse", R.string.menu_browse)

  data object CreateListing : TopLevel("create_listing", R.string.menu_create_listing)

  data object MyRentals : TopLevel("my_rentals", R.string.menu_my_rentals)

  data object ChatList : TopLevel("chat_list", R.string.menu_chat_list)

  data object Wallet : TopLevel("wallet", R.string.menu_wallet)

  /** The signed-in user's own profile. */
  data object Profile : TopLevel("profile", R.string.menu_profile)

  data object AdminCourtRuling : TopLevel("admin_court_ruling", R.string.menu_admin_court_ruling)

  data object ListingDetail : Screen("listing_detail/{$ARG_LISTING_ID}") {
    fun createRoute(listingId: String) = "listing_detail/$listingId"
  }

  data object EditListing : Screen("edit_listing/{$ARG_LISTING_ID}") {
    fun createRoute(listingId: String) = "edit_listing/$listingId"
  }

  data object RentalDetail : Screen("rental_detail/{$ARG_RENTAL_ID}") {
    fun createRoute(rentalId: String) = "rental_detail/$rentalId"
  }

  /** Each rental has exactly one conversation, so a chat is identified by its rental. */
  data object Chat : Screen("chat/{$ARG_RENTAL_ID}") {
    fun createRoute(rentalId: String) = "chat/$rentalId"
  }

  /** One screen for both pickup and return; the phase comes from the rental's status. */
  data object Handover : Screen("handover/{$ARG_RENTAL_ID}") {
    fun createRoute(rentalId: String) = "handover/$rentalId"
  }

  data object Dispute : Screen("dispute/{$ARG_RENTAL_ID}") {
    fun createRoute(rentalId: String) = "dispute/$rentalId"
  }

  /** Someone else's profile, opened from Listing detail or Rental detail. */
  data object UserProfile : Screen("profile/{$ARG_USER_ID}") {
    fun createRoute(userId: String) = "profile/$userId"
  }

  companion object {
    const val ARG_LISTING_ID = "listingId"
    const val ARG_RENTAL_ID = "rentalId"
    const val ARG_USER_ID = "userId"

    /** The screens listed in the top-bar menu, in menu order. */
    fun topLevelDestinations(isAdmin: Boolean): List<TopLevel> =
        listOfNotNull(
            Browse,
            CreateListing,
            MyRentals,
            ChatList,
            Wallet,
            Profile,
            AdminCourtRuling.takeIf { isAdmin },
        )
  }
}
