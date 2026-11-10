// Made with Claude.

package com.swent.polyloop.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swent.polyloop.R
import com.swent.polyloop.ui.theme.PolyLoopTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RatingLineTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  private fun setContent(averageRating: Double, reviewCount: Int) {
    composeTestRule.setContent {
      PolyLoopTheme { RatingLine(averageRating, reviewCount, Modifier.testTag(TAG)) }
    }
  }

  @Test
  fun showsTheAverageAndTheNumberOfReviews() {
    setContent(averageRating = 4.6, reviewCount = 5)

    composeTestRule
        .onNodeWithTag(TAG)
        .assertTextEquals(context.resources.getQuantityString(R.plurals.profile_rating, 5, 4.6, 5))
  }

  @Test
  fun noReviewsShowsNoReviewsYet() {
    setContent(averageRating = 0.0, reviewCount = 0)

    composeTestRule
        .onNodeWithTag(TAG)
        .assertTextEquals(context.getString(R.string.profile_no_reviews))
  }

  private companion object {
    const val TAG = "rating_line"
  }
}
