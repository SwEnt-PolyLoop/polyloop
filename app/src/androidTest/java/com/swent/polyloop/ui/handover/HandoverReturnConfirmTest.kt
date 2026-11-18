// Made with Claude.

package com.swent.polyloop.ui.handover

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.auth.FakeAuthRepository
import com.swent.polyloop.model.connectivity.FakeConnectivityRepository
import com.swent.polyloop.model.handover.FakeHandoverRepository
import com.swent.polyloop.model.handover.FakeHandoverRepository.StoredPhoto
import com.swent.polyloop.model.handover.HandoverPhase
import com.swent.polyloop.model.handover.HandoverPhoto
import com.swent.polyloop.model.rental.FakeRentalRepository
import com.swent.polyloop.model.rental.InMemoryServerClockStore
import com.swent.polyloop.model.rental.Rental
import com.swent.polyloop.model.rental.RentalStatus
import com.swent.polyloop.model.rental.ServerClock
import com.swent.polyloop.resources.C
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The lender's check of the returned item on Handover, and the borrower waiting for it. */
@RunWith(AndroidJUnit4::class)
class HandoverReturnConfirmTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val returnDay = LocalDate.of(2026, 10, 8)

  private val check =
      HandoverUiState(
          isLoading = false,
          isLender = true,
          itemTitle = "Camping tent",
          otherPersonName = "Loca",
          isReturned = true,
          deposit = 80,
          pickupPhotos = photos(3, HandoverPhase.PICKUP),
          photos = photos(4, HandoverPhase.RETURN),
      )

  private var retries = 0

  // The state shown; a test can change it after setContent and the screen follows.
  private var uiState by mutableStateOf(check)

  private fun setContent(initial: HandoverUiState) {
    uiState = initial
    composeTestRule.setContent {
      HandoverContent(
          uiState = uiState,
          onRetry = { retries++ },
          onAddPhoto = {},
          onRetryUpload = {},
          zone = ZoneOffset.UTC,
      )
    }
  }

  /** [count] photos of [phase], one minute apart from 16:31 UTC. */
  private fun photos(count: Int, phase: HandoverPhase) =
      List(count) {
        HandoverPhoto(
            "$phase$it",
            phase,
            Instant.parse("2026-10-08T16:31:00Z").plusSeconds(60L * it),
            "https://example.com/$phase$it.jpg",
        )
      }

  private fun text(tag: String, expected: String) =
      composeTestRule.onNodeWithTag(tag).assertTextEquals(expected)

  // ---------- the lender's check ----------

  @Test
  fun theLenderSeesTheScanAndTheirPhotosBeforeAndAfter() {
    setContent(check)

    text(C.Tag.handover_title, "Return")
    text(C.Tag.handover_other_person, "from Loca")
    text(C.Tag.handover_return_scanned, "Return scanned")
    composeTestRule.onAllNodesWithTag(C.Tag.handover_before_photo).assertCountEquals(3)
    composeTestRule.onAllNodesWithTag(C.Tag.handover_after_photo).assertCountEquals(4)
  }

  @Test
  fun aSectionWithoutPhotosSaysSo() {
    setContent(check.copy(pickupPhotos = emptyList()))

    composeTestRule.onNodeWithTag(C.Tag.handover_no_before_photos).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.handover_no_after_photos).assertDoesNotExist()
  }

  @Test
  fun photosThatCannotBeLoadedShowAnErrorWhoseRetryRetries() {
    setContent(check.copy(photosFailed = true))

    composeTestRule.onNodeWithTag(C.Tag.handover_photos_error).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.handover_before_photo).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.handover_photos_retry_button).performClick()
    assertEquals(1, retries)
  }

  @Test
  fun theBorrowerWaitsForTheLendersCheck() {
    setContent(check.copy(isLender = false, otherPersonName = "Mara"))

    composeTestRule
        .onNodeWithTag(C.Tag.handover_waiting_check)
        .assertTextEquals(
            "The item is being checked against the pickup photos. The deposit is released once " +
                "Mara confirms everything is fine."
        )
    composeTestRule.onNodeWithTag(C.Tag.handover_return_check).assertDoesNotExist()
  }

}
