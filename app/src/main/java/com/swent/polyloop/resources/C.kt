// Edited with Claude.

package com.swent.polyloop.resources

// Like R, but C
object C {
  object Tag {
    const val main_screen_container = "main_screen_container"

    const val browse_screen = "browse_screen"
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
    const val listing_card_photo = "listing_card_photo"
    const val listing_card_title = "listing_card_title"
    const val listing_card_price = "listing_card_price"
    const val listing_card_location = "listing_card_location"

    const val listing_detail_screen = "listing_detail_screen"
    const val listing_detail_photos = "listing_detail_photos"
    // Prefix: each pager page is tagged listing_detail_photo_ + its index
    const val listing_detail_photo_ = "listing_detail_photo_"
    const val listing_detail_photo_badge = "listing_detail_photo_badge"
    const val listing_detail_title = "listing_detail_title"
    const val listing_detail_price = "listing_detail_price"
    const val listing_detail_description = "listing_detail_description"
    const val listing_detail_lender_card = "listing_detail_lender_card"
    const val listing_detail_lender_avatar = "listing_detail_lender_avatar"
    const val listing_detail_lender_name = "listing_detail_lender_name"
    const val listing_detail_lender_rating = "listing_detail_lender_rating"
    const val listing_detail_deposit = "listing_detail_deposit"
    const val listing_detail_availability = "listing_detail_availability"
    const val listing_detail_deposit_note = "listing_detail_deposit_note"
    const val listing_detail_pickup_area = "listing_detail_pickup_area"
    const val listing_detail_pickup_caption = "listing_detail_pickup_caption"
    const val listing_detail_request_button = "listing_detail_request_button"

    const val top_bar = "top_bar"
    const val top_bar_menu_button = "top_bar_menu_button"
    const val top_bar_profile_button = "top_bar_profile_button"

    fun topBarMenuItem(route: String) = "top_bar_menu_item_$route"

    const val dummy_screen = "dummy_screen"
    const val dummy_screen_text = "dummy_screen_text"
    const val dummy_screen_continue_button = "dummy_screen_continue_button"
  }
}
