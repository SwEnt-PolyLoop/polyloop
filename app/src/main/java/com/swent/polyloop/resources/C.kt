// Edited with Claude.

package com.swent.polyloop.resources

import com.swent.polyloop.model.listing.ListingCategory
import java.time.LocalDate

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

    const val create_listing_photos_counter = "create_listing_photos_counter"
    const val create_listing_photos_hint = "create_listing_photos_hint"
    // Prefix: each photo is tagged create_listing_photo_ + its index
    const val create_listing_photo_ = "create_listing_photo_"
    // Prefix: each photo's remove button is tagged create_listing_remove_photo_ + its index
    const val create_listing_remove_photo_ = "create_listing_remove_photo_"
    const val create_listing_add_photo = "create_listing_add_photo"
    const val create_listing_title_field = "create_listing_title_field"
    const val create_listing_title_error = "create_listing_title_error"
    const val create_listing_category_field = "create_listing_category_field"
    // Prefix: each dropdown option is tagged create_listing_category_option_ + the category name
    const val create_listing_category_option_ = "create_listing_category_option_"
    const val create_listing_availability_field = "create_listing_availability_field"
    const val create_listing_publish_button = "create_listing_publish_button"
    const val create_listing_publishing = "create_listing_publishing"

    const val date_picker_sheet = "date_picker_sheet"
    const val date_picker_month_title = "date_picker_month_title"
    const val date_picker_available = "date_picker_available"
    const val date_picker_prev_month = "date_picker_prev_month"
    const val date_picker_next_month = "date_picker_next_month"
    const val date_picker_legend = "date_picker_legend"
    const val date_picker_error = "date_picker_error"
    const val date_picker_summary = "date_picker_summary"
    const val date_picker_summary_total = "date_picker_summary_total"
    const val date_picker_balance = "date_picker_balance"
    const val date_picker_balance_error = "date_picker_balance_error"
    const val date_picker_confirm_button = "date_picker_confirm_button"

    fun datePickerDay(date: LocalDate) = "date_picker_day_$date"

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
    const val auth_check_inbox_screen = "auth_check_inbox_screen"
    const val auth_check_inbox_body = "auth_check_inbox_body"
    const val auth_verified_button = "auth_verified_button"
    const val auth_resend_button = "auth_resend_button"
    const val auth_email_resent = "auth_email_resent"
    const val auth_back_to_form_button = "auth_back_to_form_button"

    const val chat_list_screen = "chat_list_screen"
    const val chat_list_title = "chat_list_title"
    const val chat_list_loading = "chat_list_loading"
    const val chat_list_error = "chat_list_error"
    const val chat_list_retry_button = "chat_list_retry_button"
    const val chat_list_empty = "chat_list_empty"
    const val chat_list_list = "chat_list_list"
    // Prefix: each row is tagged chat_list_row_ + rentalId
    const val chat_list_row_ = "chat_list_row_"
    const val chat_list_row_avatar = "chat_list_row_avatar"
    const val chat_list_row_name = "chat_list_row_name"
    const val chat_list_row_time = "chat_list_row_time"
    const val chat_list_row_item = "chat_list_row_item"
    const val chat_list_row_message = "chat_list_row_message"

    const val top_bar = "top_bar"
    const val top_bar_menu_button = "top_bar_menu_button"
    const val top_bar_profile_button = "top_bar_profile_button"

    fun topBarMenuItem(route: String) = "top_bar_menu_item_$route"

    const val my_rentals_screen = "my_rentals_screen"
    const val my_rentals_title = "my_rentals_title"
    const val my_rentals_tab_borrowing = "my_rentals_tab_borrowing"
    const val my_rentals_tab_lending = "my_rentals_tab_lending"
    const val my_rentals_list = "my_rentals_list"
    const val my_rentals_loading = "my_rentals_loading"
    const val my_rentals_error = "my_rentals_error"
    const val my_rentals_retry_button = "my_rentals_retry_button"
    const val my_rentals_empty = "my_rentals_empty"
    // Prefix: each row is tagged my_rentals_row_ + rental id
    const val my_rentals_row_ = "my_rentals_row_"
    const val my_rentals_row_title = "my_rentals_row_title"
    const val my_rentals_row_person = "my_rentals_row_person"
    const val my_rentals_row_return = "my_rentals_row_return"
    const val my_rentals_row_location = "my_rentals_row_location"
    const val my_rentals_row_status = "my_rentals_row_status"

    const val dummy_screen = "dummy_screen"
    const val dummy_screen_text = "dummy_screen_text"
    const val dummy_screen_continue_button = "dummy_screen_continue_button"
  }
}
