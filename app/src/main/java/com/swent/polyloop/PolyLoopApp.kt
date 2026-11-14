// Edited with Claude.

package com.swent.polyloop

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
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
import com.swent.polyloop.ui.chat.ChatScreen
import com.swent.polyloop.ui.chat.ChatViewModel
import com.swent.polyloop.ui.chatlist.ChatListScreen
import com.swent.polyloop.ui.chatlist.ChatListViewModel
import com.swent.polyloop.ui.createlisting.CreateListingScreen
import com.swent.polyloop.ui.createlisting.CreateListingViewModel
import com.swent.polyloop.ui.dispute.DisputeScreen
import com.swent.polyloop.ui.dispute.DisputeViewModel
import com.swent.polyloop.ui.dummy.DummyScreen
import com.swent.polyloop.ui.handover.HandoverScreen
import com.swent.polyloop.ui.handover.HandoverViewModel
import com.swent.polyloop.ui.listingdetail.ListingDetailScreen
import com.swent.polyloop.ui.listingdetail.ListingDetailViewModel
import com.swent.polyloop.ui.myrentals.MyRentalsScreen
import com.swent.polyloop.ui.myrentals.MyRentalsViewModel
import com.swent.polyloop.ui.navigation.DrawerViewModel
import com.swent.polyloop.ui.navigation.NavigationActions
import com.swent.polyloop.ui.navigation.PolyLoopDrawerContent
import com.swent.polyloop.ui.navigation.PolyLoopTopBar
import com.swent.polyloop.ui.navigation.Screen
import com.swent.polyloop.ui.profile.EditProfileScreen
import com.swent.polyloop.ui.profile.EditProfileViewModel
import com.swent.polyloop.ui.profile.ProfileScreen
import com.swent.polyloop.ui.profile.ProfileViewModel
import com.swent.polyloop.ui.rentaldetail.RentalDetailScreen
import com.swent.polyloop.ui.rentaldetail.RentalDetailViewModel
import com.swent.polyloop.ui.reviewrequest.ReviewRequestScreen
import com.swent.polyloop.ui.reviewrequest.ReviewRequestViewModel
import com.swent.polyloop.ui.wallet.WalletScreen
import com.swent.polyloop.ui.wallet.WalletViewModel
import kotlinx.coroutines.launch

/**
 * The app's navigation: the navigation drawer, the top bar and a NavHost holding every route.
 * Screens that are not built yet show [DummyScreen]; replace each one with the real screen as it is
 * built.
 *
 * @param isAdmin whether the drawer lists Admin court ruling.
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

  // Lives as long as the app, shared by every screen's drawer.
  val drawerViewModel: DrawerViewModel =
      viewModel(
          factory =
              DrawerViewModel.Factory(
                  RepositoryProvider.profileRepository,
                  RepositoryProvider.authRepository,
              )
      )
  val drawerUiState by drawerViewModel.uiState.collectAsStateWithLifecycle()
  // Every sign-in and sign-out changes the route, so the header follows the signed-in user.
  LaunchedEffect(currentRoute) { drawerViewModel.refresh() }
  val drawerState = rememberDrawerState(DrawerValue.Closed)
  val scope = rememberCoroutineScope()
  // Added only while the drawer opens or is open, so it is registered after the NavHost's handler
  // and called first: Back closes the drawer instead of leaving the screen under it.
  if (drawerState.targetValue == DrawerValue.Open) {
    BackHandler { scope.launch { drawerState.close() } }
  }

  ModalNavigationDrawer(
      drawerState = drawerState,
      // Opened only by the menu button, so swipes on carousels never open it; swiping closes it.
      gesturesEnabled = drawerState.isOpen,
      drawerContent = {
        PolyLoopDrawerContent(
            profile = drawerUiState.profile,
            destinations = Screen.topLevelDestinations(isAdmin),
            currentRoute = currentRoute,
            onDestinationClick = { screen ->
              scope.launch { drawerState.close() }
              navigationActions.navigateTo(screen)
            },
        )
      },
  ) {
    PolyLoopScaffold(navController, navigationActions, currentRoute, startDestination) {
      scope.launch { drawerState.open() }
    }
  }
}

/** The top bar over a NavHost holding every route; [onMenuClick] opens the drawer. */
@Composable
private fun PolyLoopScaffold(
    navController: NavHostController,
    navigationActions: NavigationActions,
    currentRoute: String?,
    startDestination: String,
    onMenuClick: () -> Unit,
) {
  Scaffold(
      topBar = {
        if (currentRoute != null && currentRoute != Screen.Auth.route) {
          PolyLoopTopBar(
              onMenuClick = onMenuClick,
              onProfileClick = { navigationActions.navigateTo(Screen.Profile) },
          )
        }
      }
  ) { innerPadding ->
    NavHost(
        navController = navController,
        startDestination = startDestination,
        // The screens end above the keyboard instead of being pushed up by it (the manifest's
        // adjustResize stops that). The keyboard covers the navigation bar, which innerPadding
        // already leaves room for, so that part is not counted twice.
        modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding).imePadding(),
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
        val createListingViewModel: CreateListingViewModel =
            viewModel(
                factory =
                    CreateListingViewModel.Factory(
                        listingRepository = RepositoryProvider.listingRepository,
                        authRepository = RepositoryProvider.authRepository,
                    )
            )
        CreateListingScreen(
            viewModel = createListingViewModel,
            onListingPublished = { id ->
              // Leaves the filled form off the back stack, so Back cannot publish a duplicate.
              navController.navigate(Screen.ListingDetail.createRoute(id)) {
                popUpTo(Screen.CreateListing.route) { inclusive = true }
              }
            },
        )
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
            onReviewRequest = { id ->
              navController.navigate(Screen.ReviewRequest.createRoute(id))
            },
            onOpenChat = { id -> navController.navigate(Screen.Chat.createRoute(id)) },
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
      composable(Screen.Wallet.route) {
        val walletViewModel: WalletViewModel =
            viewModel(
                factory =
                    WalletViewModel.Factory(
                        walletRepository = RepositoryProvider.walletRepository,
                        authRepository = RepositoryProvider.authRepository,
                        connectivityRepository = RepositoryProvider.connectivityRepository,
                    )
            )
        WalletScreen(viewModel = walletViewModel)
      }
      composable(Screen.Profile.route) {
        ProfileRoute(userId = null, navController = navController, navigationActions)
      }
      composable(Screen.EditProfile.route) {
        val editProfileViewModel: EditProfileViewModel =
            viewModel(
                factory =
                    EditProfileViewModel.Factory(
                        profileRepository = RepositoryProvider.profileRepository,
                        authRepository = RepositoryProvider.authRepository,
                    )
            )
        // Back to the profile, which follows the repository live and so shows the new name and
        // photo. Pops Edit profile by its route, so a second call cannot also pop the profile.
        EditProfileScreen(
            viewModel = editProfileViewModel,
            onSaved = { navController.popBackStack(Screen.EditProfile.route, inclusive = true) },
        )
      }
      composable(Screen.AdminCourtRuling.route) { DummyScreen(Screen.AdminCourtRuling.route) }

      composable(Screen.ListingDetail.route, stringArgument(Screen.ARG_LISTING_ID)) { entry ->
        val listingDetailViewModel: ListingDetailViewModel =
            viewModel(
                factory =
                    ListingDetailViewModel.Factory(
                        listingId = entry.stringArg(Screen.ARG_LISTING_ID),
                        listingRepository = RepositoryProvider.listingRepository,
                        rentalRepository = RepositoryProvider.rentalRepository,
                        authRepository = RepositoryProvider.authRepository,
                        profileRepository = RepositoryProvider.profileRepository,
                        connectivityRepository = RepositoryProvider.connectivityRepository,
                        walletRepository = RepositoryProvider.walletRepository,
                    )
            )
        ListingDetailScreen(
            viewModel = listingDetailViewModel,
            // Their profile opens this screen too, so the two must not pile up in the back stack.
            // The second tap of a double tap comes from a screen already leaving, so it is ignored.
            onLenderClick = { ownerId ->
              if (entry.lifecycle.currentState == Lifecycle.State.RESUMED) {
                navigationActions.navigateToOrBackTo(Screen.UserProfile.createRoute(ownerId))
              }
            },
            onRequestSent = { id -> navController.navigate(Screen.Chat.createRoute(id)) },
        )
      }
      composable(Screen.EditListing.route, stringArgument(Screen.ARG_LISTING_ID)) { entry ->
        DummyScreen(Screen.EditListing.createRoute(entry.stringArg(Screen.ARG_LISTING_ID)))
      }
      composable(Screen.RentalDetail.route, stringArgument(Screen.ARG_RENTAL_ID)) { entry ->
        val rentalId = entry.stringArg(Screen.ARG_RENTAL_ID)
        val rentalDetailViewModel: RentalDetailViewModel =
            viewModel(
                factory =
                    RentalDetailViewModel.Factory(
                        rentalId = rentalId,
                        rentalRepository = RepositoryProvider.rentalRepository,
                        listingRepository = RepositoryProvider.listingRepository,
                        profileRepository = RepositoryProvider.profileRepository,
                        authRepository = RepositoryProvider.authRepository,
                        connectivityRepository = RepositoryProvider.connectivityRepository,
                        serverClock = RepositoryProvider.serverClock,
                    )
            )
        RentalDetailScreen(
            viewModel = rentalDetailViewModel,
            onChatClick = { navController.navigateOrGoBack(Screen.Chat.createRoute(rentalId)) },
            onPersonClick = { uid -> navController.navigate(Screen.UserProfile.createRoute(uid)) },
            // Single top: a fast double tap opens Handover once, not twice.
            onStartPickupClick = {
              navController.navigate(Screen.Handover.createRoute(rentalId)) {
                launchSingleTop = true
              }
            },
            onViewDisputeClick = { navController.navigate(Screen.Dispute.createRoute(rentalId)) },
            onReviewRequestClick = {
              navController.navigate(Screen.ReviewRequest.createRoute(rentalId)) {
                launchSingleTop = true
              }
            },
        )
      }
      composable(Screen.ReviewRequest.route, stringArgument(Screen.ARG_RENTAL_ID)) { entry ->
        val rentalId = entry.stringArg(Screen.ARG_RENTAL_ID)
        val reviewRequestViewModel: ReviewRequestViewModel =
            viewModel(
                factory =
                    ReviewRequestViewModel.Factory(
                        rentalId = rentalId,
                        rentalRepository = RepositoryProvider.rentalRepository,
                        listingRepository = RepositoryProvider.listingRepository,
                        profileRepository = RepositoryProvider.profileRepository,
                        authRepository = RepositoryProvider.authRepository,
                        connectivityRepository = RepositoryProvider.connectivityRepository,
                    )
            )
        ReviewRequestScreen(
            viewModel = reviewRequestViewModel,
            onOfferSent = {
              // The chat replaces this form, so Back cannot send a second answer. Opened from that
              // chat, the form just closes, so the chat is not stacked twice.
              val chat = Screen.Chat.createRoute(rentalId)
              if (navController.previousRoute() == chat) {
                navController.popBackStack()
              } else {
                navController.navigate(chat) {
                  popUpTo(Screen.ReviewRequest.route) { inclusive = true }
                }
              }
            },
            onDeclined = { navController.popBackStack() },
            onChatClick = { navController.navigateOrGoBack(Screen.Chat.createRoute(rentalId)) },
            onPersonClick = { uid -> navController.navigate(Screen.UserProfile.createRoute(uid)) },
        )
      }
      composable(Screen.Chat.route, stringArgument(Screen.ARG_RENTAL_ID)) { entry ->
        val rentalId = entry.stringArg(Screen.ARG_RENTAL_ID)
        val chatViewModel: ChatViewModel =
            viewModel(
                factory =
                    ChatViewModel.Factory(
                        rentalId = rentalId,
                        chatRepository = RepositoryProvider.chatRepository,
                        authRepository = RepositoryProvider.authRepository,
                        rentalRepository = RepositoryProvider.rentalRepository,
                        connectivityRepository = RepositoryProvider.connectivityRepository,
                    )
            )
        ChatScreen(
            viewModel = chatViewModel,
            onRentalDetailsClick = {
              navController.navigateOrGoBack(Screen.RentalDetail.createRoute(rentalId))
            },
            // Single top: a fast double tap opens Handover once, not twice.
            onStartPickupClick = {
              navController.navigate(Screen.Handover.createRoute(rentalId)) {
                launchSingleTop = true
              }
            },
            // Back to the form when the chat was opened from it, so no stale form stays behind.
            onReviewRequestClick = {
              navController.navigateOrGoBack(Screen.ReviewRequest.createRoute(rentalId))
            },
        )
      }
      composable(Screen.Handover.route, stringArgument(Screen.ARG_RENTAL_ID)) { entry ->
        val handoverViewModel: HandoverViewModel =
            viewModel(
                factory =
                    HandoverViewModel.Factory(
                        rentalId = entry.stringArg(Screen.ARG_RENTAL_ID),
                        rentalRepository = RepositoryProvider.rentalRepository,
                        handoverRepository = RepositoryProvider.handoverRepository,
                        authRepository = RepositoryProvider.authRepository,
                        connectivityRepository = RepositoryProvider.connectivityRepository,
                    )
            )
        HandoverScreen(viewModel = handoverViewModel)
      }
      composable(Screen.Dispute.route, stringArgument(Screen.ARG_RENTAL_ID)) { entry ->
        val disputeViewModel: DisputeViewModel =
            viewModel(
                factory =
                    DisputeViewModel.Factory(
                        rentalId = entry.stringArg(Screen.ARG_RENTAL_ID),
                        disputeRepository = RepositoryProvider.disputeRepository,
                        rentalRepository = RepositoryProvider.rentalRepository,
                        authRepository = RepositoryProvider.authRepository,
                        connectivityRepository = RepositoryProvider.connectivityRepository,
                    )
            )
        DisputeScreen(disputeViewModel)
      }
      composable(Screen.UserProfile.route, stringArgument(Screen.ARG_USER_ID)) { entry ->
        ProfileRoute(entry.stringArg(Screen.ARG_USER_ID), navController, navigationActions)
      }
    }
  }
}

/**
 * The Profile screen, for the own profile ([userId] null or the user's own id) or someone else's.
 */
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
                  rentalRepository = RepositoryProvider.rentalRepository,
                  authRepository = RepositoryProvider.authRepository,
                  connectivityRepository = RepositoryProvider.connectivityRepository,
              )
      )
  ProfileScreen(
      viewModel = profileViewModel,
      onListingClick = { id -> navController.navigate(Screen.ListingDetail.createRoute(id)) },
      onEditProfile = { navController.navigate(Screen.EditProfile.route) },
      onSignedOut = navigationActions::navigateAfterSignOut,
  )
}

private fun stringArgument(name: String): List<NamedNavArgument> =
    listOf(navArgument(name) { type = NavType.StringType })

private fun NavBackStackEntry.stringArg(name: String): String =
    requireNotNull(arguments?.getString(name)) { "Missing navigation argument $name" }

/**
 * Opens [route] (a route with a rental id, such as Chat or Rental detail), or goes back when the
 * screen right behind is that same route for the same rental.
 */
private fun NavHostController.navigateOrGoBack(route: String) {
  // Going back and forth between Chat and Rental detail would otherwise stack a new copy of each
  // screen on every hop, and Back would replay them all.
  if (previousRoute() == route) popBackStack() else navigate(route)
}

/** The route of the screen right behind, with its rental id, or null if it has none. */
private fun NavHostController.previousRoute(): String? {
  val previous = previousBackStackEntry
  // The entry keeps the route pattern (e.g. "chat/{rentalId}"), so its rental id is put back in.
  return previous?.arguments?.getString(Screen.ARG_RENTAL_ID)?.let { rentalId ->
    previous.destination.route?.replace("{${Screen.ARG_RENTAL_ID}}", rentalId)
  }
}
