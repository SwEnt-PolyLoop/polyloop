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

  /** Leaves sign-in for Browse. Sign-in is removed from the back stack so Back exits the app. */
  fun navigateAfterSignIn() {
    navController.navigate(Screen.Browse.route) { popUpTo(Screen.Auth.route) { inclusive = true } }
  }
}
