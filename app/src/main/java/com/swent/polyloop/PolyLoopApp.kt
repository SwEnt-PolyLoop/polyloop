// Edited with Claude.

package com.swent.polyloop

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.swent.polyloop.ui.authentication.AuthScreen
import com.swent.polyloop.ui.authentication.AuthViewModel
import com.swent.polyloop.ui.authentication.CheckInboxScreen
import com.swent.polyloop.ui.browse.BrowseScreen
import com.swent.polyloop.ui.browse.BrowseViewModel
import com.swent.polyloop.ui.chatlist.ChatListScreen
import com.swent.polyloop.ui.chatlist.ChatListViewModel
import com.swent.polyloop.ui.createlisting.CreateListingScreen
import com.swent.polyloop.ui.createlisting.CreateListingViewModel
import com.swent.polyloop.ui.dummy.DummyScreen
import com.swent.polyloop.ui.listingdetail.ListingDetailScreen
import com.swent.polyloop.ui.listingdetail.ListingDetailViewModel
import com.swent.polyloop.ui.myrentals.MyRentalsScreen
import com.swent.polyloop.ui.myrentals.MyRentalsViewModel
import com.swent.polyloop.ui.navigation.NavigationActions
import com.swent.polyloop.ui.navigation.PolyLoopTopBar
import com.swent.polyloop.ui.navigation.Screen
import com.swent.polyloop.ui.profile.ProfileScreen
import com.swent.polyloop.ui.profile.ProfileViewModel

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
        val authViewModel: AuthViewModel =
            viewModel(factory = AuthViewModel.Factory(RepositoryProvider.authRepository))
        val authState by authViewModel.uiState.collectAsStateWithLifecycle()
        // The route navigates, once: this entry stays composed while it fades out to Browse.
        LaunchedEffect(authState.isSignedIn) {
          if (authState.isSignedIn) navigationActions.navigateAfterSignIn()
        }
        // "Check your inbox" is a step of this screen, not a route: both share the ViewModel, which
        // holds the email and password needed to resend the email and to log in again.
        when {
          // Nothing while leaving for Browse, so the form does not flash during the fade.
          authState.isSignedIn -> Unit
          authState.isAwaitingVerification -> CheckInboxScreen(viewModel = authViewModel)
          else -> AuthScreen(viewModel = authViewModel)
        }
      }

      composable(Screen.Browse.route) {
        // Scoped to this back-stack entry: cleared when Browse leaves the back stack.
        val browseViewModel: BrowseViewModel =
            viewModel(
                factory =
                    BrowseViewModel.Factory(
                        RepositoryProvider.listingRepository,
                        RepositoryProvider.authRepository,
                        RepositoryProvider.connectivityRepository,
                    )
            )
        BrowseScreen(
            viewModel = browseViewModel,
            onListingClick = { id -> navController.navigate(Screen.ListingDetail.createRoute(id)) },
            onOpenMyRentals = { navigationActions.navigateTo(Screen.MyRentals) },
        )
      }
      composable(Screen.CreateListing.route) {
        val createListingViewModel: CreateListingViewModel = viewModel()
        CreateListingScreen(viewModel = createListingViewModel)
      }
      composable(Screen.MyRentals.route) {
        val myRentalsViewModel: MyRentalsViewModel =
            viewModel(
                factory =
                    MyRentalsViewModel.Factory(
                        rentalRepository = RepositoryProvider.rentalRepository,
                        authRepository = RepositoryProvider.authRepository,
                    )
            )
        MyRentalsScreen(
            viewModel = myRentalsViewModel,
            onRentalClick = { id -> navController.navigate(Screen.RentalDetail.createRoute(id)) },
        )
      }
      composable(Screen.ChatList.route) {
        val chatListViewModel: ChatListViewModel =
            viewModel(
                factory =
                    ChatListViewModel.Factory(
                        RepositoryProvider.chatRepository,
                        RepositoryProvider.authRepository,
                    )
            )
        ChatListScreen(
            viewModel = chatListViewModel,
            onConversationClick = { rentalId ->
              navController.navigate(Screen.Chat.createRoute(rentalId))
            },
        )
      }
      composable(Screen.Wallet.route) { DummyScreen(Screen.Wallet.route) }
      composable(Screen.Profile.route) {
        ProfileRoute(userId = null, navController = navController, navigationActions)
      }
      composable(Screen.AdminCourtRuling.route) { DummyScreen(Screen.AdminCourtRuling.route) }

      composable(Screen.ListingDetail.route, stringArgument(Screen.ARG_LISTING_ID)) { entry ->
        val listingDetailViewModel: ListingDetailViewModel =
            viewModel(
                factory =
                    ListingDetailViewModel.Factory(
                        listingId = entry.stringArg(Screen.ARG_LISTING_ID),
                        listingRepository = RepositoryProvider.listingRepository,
                    )
            )
        ListingDetailScreen(
            viewModel = listingDetailViewModel,
            onLenderClick = { ownerId ->
              navController.navigate(Screen.UserProfile.createRoute(ownerId))
            },
        )
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
        ProfileRoute(entry.stringArg(Screen.ARG_USER_ID), navController, navigationActions)
      }
    }
  }
}

/** The Profile screen, for the own profile ([userId] null) or someone else's. */
@Composable
private fun ProfileRoute(
    userId: String?,
    navController: NavHostController,
    navigationActions: NavigationActions,
) {
  // Scoped to this back-stack entry: cleared when the profile leaves the back stack.
  val profileViewModel: ProfileViewModel =
      viewModel(
          factory =
              ProfileViewModel.Factory(
                  userId = userId,
                  profileRepository = RepositoryProvider.profileRepository,
                  listingRepository = RepositoryProvider.listingRepository,
                  authRepository = RepositoryProvider.authRepository,
              )
      )
  ProfileScreen(
      viewModel = profileViewModel,
      onListingClick = { id -> navController.navigate(Screen.ListingDetail.createRoute(id)) },
      onSignedOut = navigationActions::navigateAfterSignOut,
  )
}

private fun stringArgument(name: String): List<NamedNavArgument> =
    listOf(navArgument(name) { type = NavType.StringType })

private fun NavBackStackEntry.stringArg(name: String): String =
    requireNotNull(arguments?.getString(name)) { "Missing navigation argument $name" }
