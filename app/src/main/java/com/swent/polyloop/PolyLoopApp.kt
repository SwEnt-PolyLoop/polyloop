// Edited with Claude.

package com.swent.polyloop

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.swent.polyloop.model.RepositoryProvider
import com.swent.polyloop.ui.browse.BrowseScreen
import com.swent.polyloop.ui.browse.BrowseViewModel
import com.swent.polyloop.ui.dummy.DummyScreen
import com.swent.polyloop.ui.navigation.NavigationActions
import com.swent.polyloop.ui.navigation.PolyLoopTopBar
import com.swent.polyloop.ui.navigation.Screen

/**
 * The app's navigation: the top bar and a NavHost holding every route. Screens that are not built
 * yet show [DummyScreen]; replace each one with the real screen as it is built.
 *
 * @param isAdmin whether the top-bar menu lists Admin court ruling.
 * @param isSignedIn whether a user is already signed in; decides the start destination. Read once,
 *   on first composition. Tests can pass it directly instead of signing in.
 */
@Composable
fun PolyLoopApp(
    navController: NavHostController = rememberNavController(),
    isAdmin: Boolean = false,
    isSignedIn: Boolean = RepositoryProvider.authRepository.getCurrentUser() != null,
) {
  val navigationActions = remember(navController) { NavigationActions(navController) }
  val currentBackStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = currentBackStackEntry?.destination?.route

  // Computed once: changing a NavHost's start destination later does not navigate.
  val startDestination = remember { if (isSignedIn) Screen.Browse.route else Screen.Auth.route }

  Scaffold(
      topBar = {
        if (currentRoute != null && currentRoute != Screen.Auth.route) {
          PolyLoopTopBar(
              destinations = Screen.topLevelDestinations(isAdmin),
              onDestinationClick = navigationActions::navigateTo,
              onProfileClick = { navigationActions.navigateTo(Screen.Profile) },
          )
        }
      }
  ) { innerPadding ->
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = Modifier.padding(innerPadding),
    ) {
      composable(Screen.Auth.route) {
        DummyScreen(Screen.Auth.route, onContinue = navigationActions::navigateAfterSignIn)
      }

      composable(Screen.Browse.route) {
        // Scoped to this back-stack entry: cleared when Browse leaves the back stack.
        val browseViewModel: BrowseViewModel =
            viewModel(factory = BrowseViewModel.Factory(RepositoryProvider.listingRepository))
        BrowseScreen(
            viewModel = browseViewModel,
            onListingClick = { id -> navController.navigate(Screen.ListingDetail.createRoute(id)) },
        )
      }
      composable(Screen.CreateListing.route) { DummyScreen(Screen.CreateListing.route) }
      composable(Screen.MyRentals.route) { DummyScreen(Screen.MyRentals.route) }
      composable(Screen.ChatList.route) { DummyScreen(Screen.ChatList.route) }
      composable(Screen.Wallet.route) { DummyScreen(Screen.Wallet.route) }
      composable(Screen.Profile.route) { DummyScreen(Screen.Profile.route) }
      composable(Screen.AdminCourtRuling.route) { DummyScreen(Screen.AdminCourtRuling.route) }

      composable(Screen.ListingDetail.route, stringArgument(Screen.ARG_LISTING_ID)) { entry ->
        DummyScreen(Screen.ListingDetail.createRoute(entry.stringArg(Screen.ARG_LISTING_ID)))
      }
      composable(Screen.EditListing.route, stringArgument(Screen.ARG_LISTING_ID)) { entry ->
        DummyScreen(Screen.EditListing.createRoute(entry.stringArg(Screen.ARG_LISTING_ID)))
      }
      composable(Screen.RentalDetail.route, stringArgument(Screen.ARG_RENTAL_ID)) { entry ->
        DummyScreen(Screen.RentalDetail.createRoute(entry.stringArg(Screen.ARG_RENTAL_ID)))
      }
      composable(Screen.Chat.route, stringArgument(Screen.ARG_RENTAL_ID)) { entry ->
        DummyScreen(Screen.Chat.createRoute(entry.stringArg(Screen.ARG_RENTAL_ID)))
      }
      composable(Screen.Handover.route, stringArgument(Screen.ARG_RENTAL_ID)) { entry ->
        DummyScreen(Screen.Handover.createRoute(entry.stringArg(Screen.ARG_RENTAL_ID)))
      }
      composable(Screen.Dispute.route, stringArgument(Screen.ARG_RENTAL_ID)) { entry ->
        DummyScreen(Screen.Dispute.createRoute(entry.stringArg(Screen.ARG_RENTAL_ID)))
      }
      composable(Screen.UserProfile.route, stringArgument(Screen.ARG_USER_ID)) { entry ->
        DummyScreen(Screen.UserProfile.createRoute(entry.stringArg(Screen.ARG_USER_ID)))
      }
    }
  }
}

private fun stringArgument(name: String): List<NamedNavArgument> =
    listOf(navArgument(name) { type = NavType.StringType })

private fun NavBackStackEntry.stringArg(name: String): String =
    requireNotNull(arguments?.getString(name)) { "Missing navigation argument $name" }
