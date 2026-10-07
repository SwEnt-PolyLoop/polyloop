// Edited with Claude.

package com.swent.polyloop.resources

// Like R, but C
object C {
  object Tag {
    const val main_screen_container = "main_screen_container"

    const val browse_screen = "browse_screen"
    const val browse_menu_button = "browse_menu_button"
    const val browse_profile_button = "browse_profile_button"
    const val browse_search_bar = "browse_search_bar"
    const val browse_category_chips = "browse_category_chips"
    const val browse_map_toggle = "browse_map_toggle"
    const val browse_list_toggle = "browse_list_toggle"
    const val browse_list = "browse_list"
    const val browse_title = "browse_title"
    const val browse_category_all = "browse_category_all"
    const val browse_loading = "browse_loading"
    const val browse_error = "browse_error"
    const val browse_retry_button = "browse_retry_button"
    const val browse_empty = "browse_empty"

    // Prefix: each card is tagged listing_card_ + listing.id
    const val listing_card_ = "listing_card_"
    const val listing_card_title = "listing_card_title"
    const val listing_card_price = "listing_card_price"
    const val listing_card_location = "listing_card_location"

    const val top_bar = "top_bar"
    const val top_bar_menu_button = "top_bar_menu_button"
    const val top_bar_profile_button = "top_bar_profile_button"

    fun topBarMenuItem(route: String) = "top_bar_menu_item_$route"

    const val dummy_screen = "dummy_screen"
    const val dummy_screen_text = "dummy_screen_text"
    const val dummy_screen_continue_button = "dummy_screen_continue_button"
  }
}
