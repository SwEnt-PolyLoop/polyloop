// Made with Claude.

package com.swent.polyloop.ui.navigation

import androidx.navigation.NavHostController

/** The navigation operations screens are allowed to perform, wrapping a [NavHostController]. */
class NavigationActions(private val navController: NavHostController) {

  /**
   * Opens a top-level destination (a menu entry).
   *
   * Top-level destinations share one back stack rooted at Browse, so Back from any of them returns
   * to Browse, and selecting the current one again does nothing. Screens opened on top (e.g. a
   * listing detail) are dropped, not saved, so selecting Browse always shows Browse itself.
   */
  fun navigateTo(screen: Screen.TopLevel) {
    if (navController.currentDestination?.route == screen.route) return
    navController.navigate(screen.route) {
      // Browse itself stays (not inclusive), so its ViewModel survives and is reused.
      popUpTo(Screen.Browse.route)
      launchSingleTop = true
    }
  }

  /** Opens a concrete route built with a destination's `createRoute(...)`. */
  fun navigateTo(route: String) {
    navController.navigate(route)
  }

  /**
   * Goes back to [route], built with a destination's `createRoute(...)`, if it is further back in
   * the back stack, dropping the screens on top of it; opens it otherwise. So going back and forth
   * between two screens that open each other does not pile up entries, each keeping its ViewModel
   * alive. If [route] is already on top it opens again, so callers must ignore taps from a screen
   * that is leaving.
   */
  fun navigateToOrBackTo(route: String) {
    if (!navController.popBackStack(route, inclusive = false)) navController.navigate(route)
  }

  /** Leaves sign-in for Browse. Sign-in is removed from the back stack so Back exits the app. */
  fun navigateAfterSignIn() {
    navController.navigate(Screen.Browse.route) { popUpTo(Screen.Auth.route) { inclusive = true } }
  }

  /**
   * Leaves the app for sign-in after signing out. The whole back stack is cleared (whichever screen
   * the app started on), so Back exits the app instead of showing a signed-in screen.
   */
  fun navigateAfterSignOut() {
    navController.navigate(Screen.Auth.route) {
      popUpTo(navController.graph.id) { inclusive = true }
    }
  }
}
