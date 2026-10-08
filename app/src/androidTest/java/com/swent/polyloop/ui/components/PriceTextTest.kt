// Made with Claude.

package com.swent.polyloop.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swent.polyloop.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PriceTextTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  @Test
  fun displaysPriceFollowedByPerDay() {
    composeTestRule.setContent {
      PriceText(
          pricePerDay = 15,
          style = MaterialTheme.typography.bodyLarge,
          modifier = Modifier.testTag("price"),
      )
    }

    val expected =
        context.getString(R.string.listing_card_price, 15) +
            " " +
            context.getString(R.string.listing_card_per_day)
    composeTestRule.onNodeWithTag("price").assertIsDisplayed().assertTextEquals(expected)
  }
}
