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
import com.swent.polyloop.model.connectivity.FakeConnectivityRepository
import com.swent.polyloop.model.dispute.FakeDisputeRepository
import com.swent.polyloop.model.handover.FakeHandoverRepository
import com.swent.polyloop.model.handover.HandoverPhase
import com.swent.polyloop.model.rental.FakeRentalRepository
import com.swent.polyloop.model.rental.InMemoryServerClockStore
import com.swent.polyloop.model.rental.Rental
import com.swent.polyloop.model.rental.RentalStatus
import com.swent.polyloop.model.rental.ServerClock
import com.swent.polyloop.resources.C
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
              HandoverUiState(
                  isLoading = false,
                  phase = HandoverPhase.PICKUP,
                  pickupDate = pickupDay,
                  serverToday = pickupDay,
              ),
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
    // The server's day is the pickup day.
    val rentals = FakeRentalRepository(listOf(rental))
    rentals.serverNow = pickupDay.atTime(12, 0).atZone(ZoneId.of("Europe/Zurich")).toInstant()
    val viewModel =
        HandoverViewModel(
            rental.id,
            rentals,
            FakeHandoverRepository(currentUid = "borrower"),
            FakeDisputeRepository(currentUid = { "borrower" }),
            FakeAuthRepository(signedInUser = AuthUser("borrower", "loca@epfl.ch", "Loca")),
            FakeConnectivityRepository(),
            ServerClock(rentals, InMemoryServerClockStore(), bootCount = { 0 }),
        )
    composeTestRule.setContent { HandoverScreen(viewModel) }
    // The server's day is read off the main thread, so the camera tiles come once it is known.
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag(C.Tag.handover_add_photo).fetchSemanticsNodes().isNotEmpty()
    }

    composeTestRule.onAllNodesWithTag(C.Tag.handover_add_photo)[0].performClick()

    composeTestRule.onNodeWithTag(C.Tag.create_listing_camera_shutter).assertIsDisplayed()
  }
}
