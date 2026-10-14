// Edited with Claude.

package com.swent.polyloop.resources

import com.swent.polyloop.model.listing.ListingCategory

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
    const val browse_clear_search = "browse_clear_search"
    const val browse_loading = "browse_loading"
    const val browse_error = "browse_error"
    const val browse_retry_button = "browse_retry_button"
    const val browse_empty = "browse_empty"

    fun browseCategoryChip(category: ListingCategory) = "browse_category_chip_${category.name}"

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
    const val listing_detail_loading = "listing_detail_loading"
    const val listing_detail_error = "listing_detail_error"
    const val listing_detail_retry_button = "listing_detail_retry_button"
    const val listing_detail_not_found = "listing_detail_not_found"

    const val auth_name_field = "auth_name_field"
    const val auth_email_field = "auth_email_field"
    const val auth_password_field = "auth_password_field"
    const val auth_login_tab = "auth_login_tab"
    const val auth_signup_tab = "auth_signup_tab"
    const val auth_login_button = "auth_login_button"
    const val auth_signup_button = "auth_signup_button"
    const val auth_name_error = "auth_name_error"
    const val auth_email_error = "auth_email_error"
    const val auth_password_error = "auth_password_error"
    const val auth_error = "auth_error"

    const val profile_screen = "profile_screen"
    const val profile_avatar = "profile_avatar"
    const val profile_name = "profile_name"
    const val profile_rating = "profile_rating"
    const val profile_listings_title = "profile_listings_title"
    const val profile_listings_empty = "profile_listings_empty"
    const val profile_sign_out_button = "profile_sign_out_button"
    const val profile_loading = "profile_loading"
    const val profile_error = "profile_error"
    const val profile_retry_button = "profile_retry_button"
    const val profile_not_found = "profile_not_found"

    const val top_bar = "top_bar"
    const val top_bar_menu_button = "top_bar_menu_button"
    const val top_bar_profile_button = "top_bar_profile_button"

    fun topBarMenuItem(route: String) = "top_bar_menu_item_$route"

    const val dummy_screen = "dummy_screen"
    const val dummy_screen_text = "dummy_screen_text"
    const val dummy_screen_continue_button = "dummy_screen_continue_button"
  }
}
