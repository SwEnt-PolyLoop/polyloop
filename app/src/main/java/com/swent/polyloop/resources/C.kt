// Edited with Claude.

package com.swent.polyloop.resources

// Like R, but C
object C {
  object Tag {
    const val greeting = "main_screen_greeting"
    const val greeting_robo = "second_screen_greeting"

    const val main_screen_container = "main_screen_container"
    const val second_screen_container = "second_screen_container"

    const val top_bar = "top_bar"
    const val top_bar_menu_button = "top_bar_menu_button"
    const val top_bar_profile_button = "top_bar_profile_button"

    fun topBarMenuItem(route: String) = "top_bar_menu_item_$route"

    const val dummy_screen = "dummy_screen"
    const val dummy_screen_text = "dummy_screen_text"
    const val dummy_screen_continue_button = "dummy_screen_continue_button"
  }
}
