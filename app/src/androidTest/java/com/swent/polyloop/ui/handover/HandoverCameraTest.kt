// Made with Claude.

package com.swent.polyloop.ui.handover

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.auth.FakeAuthRepository
import com.swent.polyloop.model.handover.FakeHandoverRepository
import com.swent.polyloop.model.handover.HandoverPhase
import com.swent.polyloop.model.rental.FakeRentalRepository
import com.swent.polyloop.model.rental.Rental
import com.swent.polyloop.model.rental.RentalStatus
import com.swent.polyloop.resources.C
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The in-app camera on Handover, with the camera permission granted. No photo is taken: the preview
 * needs a real camera, which the CI emulator may not have, so only the controls are checked.
 */
@RunWith(AndroidJUnit4::class)
class HandoverCameraTest {

  @get:Rule(order = 0) val permissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

  @get:Rule(order = 1) val composeTestRule = createComposeRule()

  private val pickupDay = LocalDate.of(2026, 10, 6)

  private var closes = 0

  private fun setContent(isCameraOpen: Boolean) {
    composeTestRule.setContent {
      HandoverContent(
          uiState =
              HandoverUiState(isLoading = false, phase = HandoverPhase.PICKUP, isPickupDay = true),
          onRetry = {},
          onAddPhoto = {},
          onRetryUpload = {},
          isCameraOpen = isCameraOpen,
          onCloseCamera = { closes++ },
      )
    }
  }

  @Test
  fun theCameraIsHiddenWhileClosed() {
    setContent(isCameraOpen = false)

    composeTestRule.onNodeWithTag(C.Tag.create_listing_camera).assertDoesNotExist()
  }

  @Test
  fun anOpenCameraShowsItsShutterAndClosesWithItsCloseButton() {
    setContent(isCameraOpen = true)

    composeTestRule.onNodeWithTag(C.Tag.create_listing_camera_shutter).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.create_listing_camera_close).performClick()
    assertEquals(1, closes)
  }

  @Test
  fun tappingACameraTileOnTheScreenOpensTheCamera() {
    val rental =
        Rental(
            id = "rental1",
            listingId = "listing1",
            listingTitle = "Camping tent",
            ownerId = "owner",
            ownerName = "Mara",
            borrowerId = "borrower",
            borrowerName = "Loca",
            startDate = pickupDay,
            endDate = pickupDay.plusDays(2),
            status = RentalStatus.ACCEPTED,
            pricePerDay = 15,
            totalPrice = 45,
            pickupArea = "Ecublens",
            dropOffArea = "Renens",
            createdAt = Instant.parse("2026-10-01T09:30:00Z"),
        )
    val viewModel =
        HandoverViewModel(
            rental.id,
            FakeRentalRepository(listOf(rental)),
            FakeHandoverRepository(currentUid = "borrower"),
            FakeAuthRepository(signedInUser = AuthUser("borrower", "loca@epfl.ch", "Loca")),
            today = { pickupDay },
        )
    composeTestRule.setContent { HandoverScreen(viewModel) }

    composeTestRule.onAllNodesWithTag(C.Tag.handover_add_photo)[0].performClick()

    composeTestRule.onNodeWithTag(C.Tag.create_listing_camera_shutter).assertIsDisplayed()
  }
}
