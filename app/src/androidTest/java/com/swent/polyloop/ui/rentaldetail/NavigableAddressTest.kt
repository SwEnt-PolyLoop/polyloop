// Made with Claude.

package com.swent.polyloop.ui.rentaldetail

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swent.polyloop.R
import com.swent.polyloop.resources.C
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** An exact meeting point with its Navigate button. */
@RunWith(AndroidJUnit4::class)
class NavigableAddressTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  private val tag = C.Tag.rental_detail_pickup_address

  /**
   * Records the links it opens; its first [failures] calls throw [failure], by default what the
   * platform's handler throws when no app can open the link.
   */
  private class FakeUriHandler(
      private var failures: Int = 0,
      private val failure: (String) -> RuntimeException = {
        IllegalArgumentException("Can't open $it.")
      },
  ) : UriHandler {
    val opened = mutableListOf<String>()

    override fun openUri(uri: String) {
      if (failures > 0) {
        failures--
        throw failure(uri)
      }
      opened += uri
    }
  }

  private var address by mutableStateOf("")

  private fun setContent(initialAddress: String, uriHandler: UriHandler = FakeUriHandler()) {
    address = initialAddress
    composeTestRule.setContent {
      CompositionLocalProvider(LocalUriHandler provides uriHandler) {
        NavigableAddress(
            address = address,
            addressTag = tag,
            navigateTag = C.Tag.rental_detail_pickup_navigate,
            errorTag = C.Tag.rental_detail_pickup_navigate_error,
        )
      }
    }
  }

  @Test
  fun anAddressShowsItWithTheNavigateButton() {
    setContent("Route Cantonale 1, Ecublens")

    composeTestRule.onNodeWithTag(tag).assertTextEquals("Route Cantonale 1, Ecublens")
    composeTestRule
        .onNodeWithTag(C.Tag.rental_detail_pickup_navigate)
        .assertIsDisplayed()
        .assertIsEnabled()
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_pickup_navigate_error).assertDoesNotExist()
  }

  @Test
  fun aBlankAddressShowsThePlaceholderWithoutTheButton() {
    setContent("  ")

    composeTestRule
        .onNodeWithTag(tag)
        .assertTextEquals(context.getString(R.string.rental_detail_address_placeholder))
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_pickup_navigate).assertDoesNotExist()
  }

  @Test
  fun aLinkNoAppCanOpenShowsAnErrorUntilATapOpensIt() {
    val uriHandler = FakeUriHandler(failures = 1)
    setContent("Route Cantonale 1, Ecublens", uriHandler)

    composeTestRule.onNodeWithTag(C.Tag.rental_detail_pickup_navigate).performClick()
    composeTestRule
        .onNodeWithTag(C.Tag.rental_detail_pickup_navigate_error)
        .assertTextEquals(context.getString(R.string.rental_detail_navigate_failed))

    composeTestRule.onNodeWithTag(C.Tag.rental_detail_pickup_navigate).performClick()
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_pickup_navigate_error).assertDoesNotExist()
    assertEquals(listOf(directionsUrl("Route Cantonale 1, Ecublens")), uriHandler.opened)
  }

  @Test
  fun anAppThatRefusesToOpenTheLinkShowsTheError() {
    setContent(
        "Route Cantonale 1, Ecublens",
        FakeUriHandler(failures = 1) { SecurityException("Not exported: $it") },
    )

    composeTestRule.onNodeWithTag(C.Tag.rental_detail_pickup_navigate).performClick()

    composeTestRule
        .onNodeWithTag(C.Tag.rental_detail_pickup_navigate_error)
        .assertTextEquals(context.getString(R.string.rental_detail_navigate_failed))
  }

  @Test
  fun anotherAddressClearsTheErrorAboutTheOldOne() {
    setContent("Route Cantonale 1, Ecublens", FakeUriHandler(failures = 1))
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_pickup_navigate).performClick()
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_pickup_navigate_error).assertIsDisplayed()

    address = "Rue de Lausanne 10, Renens"

    composeTestRule.onNodeWithTag(C.Tag.rental_detail_pickup_navigate_error).assertDoesNotExist()
  }
}
