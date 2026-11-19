// Made with Claude.

package com.swent.polyloop.ui.handover

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.polyloop.model.handover.HandoverPhase
import com.swent.polyloop.model.handover.HandoverPhoto
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.components.formatDay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HandoverScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val pickupDay = LocalDate.of(2026, 10, 6)

  // On the pickup day by the server's clock.
  private val pickup =
      HandoverUiState(
          isLoading = false,
          itemTitle = "Camping tent",
          otherPersonName = "Mara",
          phase = HandoverPhase.PICKUP,
          pickupDate = pickupDay,
          serverToday = pickupDay,
      )

  private val returnDay = LocalDate.of(2026, 10, 8)

  // On the return day by the server's clock.
  private val returning =
      pickup.copy(phase = HandoverPhase.RETURN, returnDate = returnDay, serverToday = returnDay)

  private var retries = 0
  private var cameraOpenings = 0
  private var uploadRetries = 0
  private var serverTodayRetries = 0

  // The state shown; a test can change it after setContent and the screen follows.
  private var uiState by mutableStateOf(pickup)

  private fun setContent(initial: HandoverUiState = pickup) {
    uiState = initial
    composeTestRule.setContent {
      HandoverContent(
          uiState = uiState,
          onRetry = { retries++ },
          onAddPhoto = { cameraOpenings++ },
          onRetryUpload = { uploadRetries++ },
          onRetryServerToday = { serverTodayRetries++ },
          zone = ZoneOffset.UTC,
      )
    }
  }

  /** [count] pickup photos taken one minute apart from 14:02 UTC. */
  private fun photos(count: Int) =
      List(count) {
        HandoverPhoto(
            "p$it",
            HandoverPhase.PICKUP,
            Instant.parse("2026-10-06T14:02:00Z").plusSeconds(60L * it),
            "https://example.com/p$it.jpg",
        )
      }

  /** [count] return photos taken one minute apart from 16:31 UTC. */
  private fun returnPhotos(count: Int) =
      List(count) {
        HandoverPhoto(
            "r$it",
            HandoverPhase.RETURN,
            Instant.parse("2026-10-08T16:31:00Z").plusSeconds(60L * it),
            "https://example.com/r$it.jpg",
        )
      }

  private fun text(tag: String, expected: String) =
      composeTestRule.onNodeWithTag(tag).assertTextEquals(expected)

  private fun assertTiles(photos: Int, empty: Int) {
    composeTestRule.onAllNodesWithTag(C.Tag.handover_photo).assertCountEquals(photos)
    composeTestRule.onAllNodesWithTag(C.Tag.handover_add_photo).assertCountEquals(empty)
  }

  private fun assertShown(tag: String) = composeTestRule.onNodeWithTag(tag).assertIsDisplayed()

  private fun assertAbsent(tag: String) = composeTestRule.onNodeWithTag(tag).assertDoesNotExist()

  @Test
  fun showsASpinnerWhileLoading() {
    setContent(HandoverUiState())

    assertShown(C.Tag.handover_loading)
    assertAbsent(C.Tag.handover_error)
  }

  @Test
  fun aFailedLoadShowsAnErrorWhoseRetryButtonRetries() {
    setContent(HandoverUiState(isLoading = false, loadFailed = true))

    assertShown(C.Tag.handover_error)
    composeTestRule.onNodeWithTag(C.Tag.handover_retry_button).performClick()
    assertEquals(1, retries)
  }

  @Test
  fun aMissingRentalSaysSo() {
    setContent(HandoverUiState(isLoading = false, notFound = true))

    assertShown(C.Tag.handover_not_found)
  }

  @Test
  fun aRentalThatTakesNoPhotosHasNothingToHandOver() {
    setContent(pickup.copy(phase = null))

    assertShown(C.Tag.handover_nothing)
    assertAbsent(C.Tag.handover_loading)
  }

  // ---------- header ----------

  @Test
  fun theBorrowerSeesPickupYouBorrowTheItemAndTheLender() {
    setContent()

    composeTestRule.onNodeWithTag(C.Tag.handover_title).assertTextEquals("Pickup")
    composeTestRule.onNodeWithTag(C.Tag.handover_role_pill).assertTextEquals("You borrow")
    composeTestRule.onNodeWithTag(C.Tag.handover_item).assertTextEquals("Camping tent")
    composeTestRule.onNodeWithTag(C.Tag.handover_other_person).assertTextEquals("from Mara")
  }

  @Test
  fun theBorrowerSeesTheirDepositHeldInTheFooter() {
    setContent(pickup.copy(deposit = 80))

    composeTestRule
        .onNodeWithTag(C.Tag.handover_deposit_label)
        .assertTextEquals("Your deposit, held")
    composeTestRule.onNodeWithTag(C.Tag.handover_deposit).assertTextEquals("80 PP")
  }

  @Test
  fun theLenderSeesTheDepositHeldInTheFooter() {
    setContent(pickup.copy(isLender = true, deposit = 80))

    composeTestRule.onNodeWithTag(C.Tag.handover_deposit_label).assertTextEquals("Deposit held")
    composeTestRule.onNodeWithTag(C.Tag.handover_deposit).assertTextEquals("80 PP")
  }

  @Test
  fun anUnknownDepositShowsADashInTheFooter() {
    setContent(pickup.copy(deposit = 0))

    composeTestRule
        .onNodeWithTag(C.Tag.handover_deposit_label)
        .assertTextEquals("Your deposit, held")
    composeTestRule.onNodeWithTag(C.Tag.handover_deposit).assertTextEquals("—")
  }

  @Test
  fun theLenderSeesYouLendAndTheBorrower() {
    setContent(pickup.copy(isLender = true, otherPersonName = "Loca"))

    composeTestRule.onNodeWithTag(C.Tag.handover_role_pill).assertTextEquals("You lend")
    composeTestRule.onNodeWithTag(C.Tag.handover_other_person).assertTextEquals("with Loca")
  }

  @Test
  fun aBlankNameShowsTheOtherPersonsRole() {
    setContent(pickup.copy(isLender = true, otherPersonName = " "))
    composeTestRule.onNodeWithTag(C.Tag.handover_other_person).assertTextEquals("with the borrower")
  }

  @Test
  fun aBlankLenderNameShowsTheLender() {
    setContent(pickup.copy(otherPersonName = ""))
    composeTestRule.onNodeWithTag(C.Tag.handover_other_person).assertTextEquals("from the lender")
  }

  // ---------- photos ----------

  @Test
  fun photosShowTheirTimeAndCameraTilesFillUpToThreeThenOneUntilTwenty() {
    setContent()
    composeTestRule.onNodeWithTag(C.Tag.handover_photos_title).assertTextEquals("Condition photos")
    composeTestRule.onNodeWithTag(C.Tag.handover_photos_range).assertTextEquals("3–20 photos")
    assertTiles(photos = 0, empty = 3)

    uiState = pickup.copy(photos = photos(2))
    assertTiles(photos = 2, empty = 1)
    composeTestRule.onAllNodesWithTag(C.Tag.handover_photo)[1].assertTextEquals("14:03")
    composeTestRule.onNodeWithContentDescription("Pickup photo, 14:02").assertIsDisplayed()

    uiState = pickup.copy(photos = photos(5))
    assertTiles(photos = 5, empty = 1)

    uiState = pickup.copy(photos = photos(HandoverPhoto.MAX_PHOTOS))
    assertTiles(photos = 20, empty = 0)
  }

  @Test
  fun outsideThePickupDayPhotosStayButNoneCanBeAdded() {
    setContent(pickup.copy(serverToday = pickupDay.plusDays(1), photos = photos(1)))

    composeTestRule
        .onNodeWithTag(C.Tag.handover_not_pickup_day)
        .assertTextEquals("Pickup is on 6 Oct")
    assertAbsent(C.Tag.handover_pickup_needs_connection)
    assertAbsent(C.Tag.handover_pickup_day_retry)
    assertTiles(photos = 1, empty = 0)
    assertAbsent(C.Tag.handover_photos_status)
  }

  @Test
  fun anUnknownServerDayAsksToConnectAndTakesNoPhoto() {
    setContent(pickup.copy(serverToday = null, photos = photos(1)))

    composeTestRule
        .onNodeWithTag(C.Tag.handover_pickup_needs_connection)
        .assertTextEquals("Connect to the internet to check the pickup day.")
    assertAbsent(C.Tag.handover_not_pickup_day)
    assertTiles(photos = 1, empty = 0)
    assertAbsent(C.Tag.handover_photos_status)
  }

  @Test
  fun anUnknownServerDayCanBeReadAgainWithRetryWhileOnline() {
    setContent(pickup.copy(serverToday = null))

    composeTestRule
        .onNodeWithTag(C.Tag.handover_pickup_day_retry)
        .assertTextEquals("Retry")
        .assertIsEnabled()
        .performClick()
    assertEquals(1, serverTodayRetries)

    uiState = pickup.copy(serverToday = null, isOffline = true)
    composeTestRule.onNodeWithTag(C.Tag.handover_pickup_day_retry).assertIsNotEnabled()
  }

  @Test
  fun whileTheServerDayIsReadNoNoteShowsAndNoPhotoCanBeTaken() {
    setContent(pickup.copy(serverToday = null, isReadingServerToday = true))

    assertAbsent(C.Tag.handover_pickup_needs_connection)
    assertAbsent(C.Tag.handover_pickup_day_retry)
    assertAbsent(C.Tag.handover_not_pickup_day)
    assertTiles(photos = 0, empty = 0)

    uiState = pickup
    assertTiles(photos = 0, empty = 3)
  }

  @Test
  fun theStatusSaysHowManyPhotosAreMissingThenThatTheyAreDone() {
    val status = composeTestRule.onNodeWithTag(C.Tag.handover_photos_status)
    setContent(pickup.copy(photos = photos(1)))
    status.assertTextEquals("Take 2 more photos")

    uiState = pickup.copy(photos = photos(2))
    status.assertTextEquals("Take 1 more photo")

    uiState = pickup.copy(photos = photos(3))
    status.assertTextEquals("Pickup photos done")
    assertAbsent(C.Tag.handover_not_pickup_day)
  }

  @Test
  fun thePhotoBeingUploadedTakesATileWithASpinner() {
    setContent(pickup.copy(photos = photos(1), isUploading = true))

    assertShown(C.Tag.handover_uploading)
    assertTiles(photos = 1, empty = 1)
  }

  @Test
  fun aPhotoThatFailedToUploadIsRetriedWhenTappedAndNoNewOneCanBeTaken() {
    setContent(pickup.copy(failedPhoto = FailedPhoto("file:///photo.jpg", HandoverPhase.PICKUP)))

    composeTestRule
        .onNodeWithContentDescription("Photo not uploaded. Tap to try again.")
        .assertIsDisplayed()
    assertTiles(photos = 0, empty = 0)
    composeTestRule.onNodeWithTag(C.Tag.handover_failed_photo).performClick()
    assertEquals(1, uploadRetries)
  }

  @Test
  fun tappingAPhotoShowsItFullScreenUntilItIsClosed() {
    setContent(pickup.copy(photos = photos(2)))

    composeTestRule.onAllNodesWithTag(C.Tag.handover_photo)[1].performClick()
    assertShown(C.Tag.handover_photo_viewer)
    composeTestRule
        .onNodeWithTag(C.Tag.handover_photo_viewer_label)
        .assertTextEquals("Pickup · 14:03")

    composeTestRule.onNodeWithTag(C.Tag.handover_photo_viewer_close).performClick()
    assertAbsent(C.Tag.handover_photo_viewer)
  }

  @Test
  fun aRefusedCameraPermissionExplainsHowToAllowIt() {
    composeTestRule.setContent {
      HandoverContent(
          uiState = pickup,
          onRetry = {},
          onAddPhoto = {},
          onRetryUpload = {},
          cameraPermissionGranted = false,
      )
    }

    assertShown(C.Tag.create_listing_camera_permission_message)
  }

  @Test
  fun photosThatCannotBeLoadedShowAnErrorWithRetryInsteadOfTheTiles() {
    setContent(pickup.copy(photosFailed = true))

    assertShown(C.Tag.handover_photos_error)
    assertTiles(photos = 0, empty = 0)
    assertAbsent(C.Tag.handover_photos_status)
    composeTestRule.onNodeWithTag(C.Tag.handover_photos_retry_button).performClick()
    assertEquals(1, retries)
  }

  @Test
  fun offlineTheBannerShowsAndTheCameraTilesDoNothing() {
    setContent(pickup.copy(isOffline = true))

    assertShown(C.Tag.offline_banner)
    composeTestRule.onAllNodesWithTag(C.Tag.handover_add_photo)[0].performClick()
    assertEquals(0, cameraOpenings)
  }

  @Test
  fun backIsBlockedOnlyWhileAPhotoUploads() {
    var backs = 0
    lateinit var backDispatcher: OnBackPressedDispatcher
    uiState = pickup.copy(isUploading = true)
    composeTestRule.setContent {
      backDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
      BackHandler { backs++ } // stands for the navigation, which would leave the screen
      HandoverContent(uiState = uiState, onRetry = {}, onAddPhoto = {}, onRetryUpload = {})
    }

    // Through the dispatcher, as in EditProfileScreenTest: Espresso.pressBack() waits for focus.
    composeTestRule.runOnUiThread { backDispatcher.onBackPressed() }
    composeTestRule.runOnIdle { assertEquals(0, backs) }

    uiState = pickup
    composeTestRule.waitForIdle()
    composeTestRule.runOnUiThread { backDispatcher.onBackPressed() }
    composeTestRule.runOnIdle { assertEquals(1, backs) }
  }

  @Test
  fun backIsBlockedWhileTheScanIsSent() {
    var backs = 0
    lateinit var backDispatcher: OnBackPressedDispatcher
    uiState = pickup.copy(isLender = false, isSendingScan = true)
    composeTestRule.setContent {
      backDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
      BackHandler { backs++ } // stands for the navigation, which would leave the screen
      HandoverContent(uiState = uiState, onRetry = {}, onAddPhoto = {}, onRetryUpload = {})
    }

    composeTestRule.runOnUiThread { backDispatcher.onBackPressed() }
    composeTestRule.runOnIdle { assertEquals(0, backs) }

    // Once the server answered (here with a wrong code), Back leaves again.
    uiState = uiState.copy(isSendingScan = false, scanError = ScanError.WRONG_CODE)
    composeTestRule.waitForIdle()
    composeTestRule.runOnUiThread { backDispatcher.onBackPressed() }
    composeTestRule.runOnIdle { assertEquals(1, backs) }
  }

  @Test
  fun aCameraTileOpensTheCameraOnlyWhenAPhotoCanBeAdded() {
    setContent(pickup.copy(isUploading = true))
    assertAbsent(C.Tag.offline_banner)
    composeTestRule.onAllNodesWithTag(C.Tag.handover_add_photo)[0].performClick()
    assertEquals(0, cameraOpenings)

    uiState = pickup
    composeTestRule.onAllNodesWithTag(C.Tag.handover_add_photo)[2].performClick()
    assertEquals(1, cameraOpenings)
  }

  // ---------- the return ----------

  @Test
  fun theBorrowerReturnsTheItemToTheLender() {
    setContent(returning.copy(photos = returnPhotos(3)))

    text(C.Tag.handover_title, "Return")
    text(C.Tag.handover_other_person, "to Mara")
    text(C.Tag.handover_photos_status, "Return photos done")
    composeTestRule.onNodeWithContentDescription("Return photo, 16:31").assertIsDisplayed()
  }

  @Test
  fun theLenderGetsTheItemBackFromTheBorrower() {
    setContent(returning.copy(isLender = true, otherPersonName = "Loca"))

    text(C.Tag.handover_other_person, "from Loca")
    assertTiles(photos = 0, empty = 3)
  }

  @Test
  fun blankNamesAtReturnShowTheOtherPersonsRole() {
    setContent(returning.copy(otherPersonName = ""))
    text(C.Tag.handover_other_person, "to the lender")

    uiState = returning.copy(otherPersonName = "", isLender = true)
    text(C.Tag.handover_other_person, "from the borrower")
  }

  @Test
  fun beforeTheReturnDayTheDayIsShownAndNoPhotoCanBeAdded() {
    setContent(returning.copy(serverToday = returnDay.minusDays(1)))

    text(C.Tag.handover_not_return_day, "Return is on ${formatDay(returnDay)}")
    assertTiles(photos = 0, empty = 0)
  }

  @Test
  fun anUnknownServerDayAtReturnAsksToConnectToCheckTheReturnDay() {
    setContent(returning.copy(serverToday = null))

    text(C.Tag.handover_return_needs_connection, "Connect to the internet to check the return day.")
    assertTiles(photos = 0, empty = 0)
  }

  @Test
  fun aReturnPhotoShownFullScreenSaysReturn() {
    setContent(returning.copy(photos = returnPhotos(1)))

    composeTestRule.onNodeWithTag(C.Tag.handover_photo).performClick()
    text(C.Tag.handover_photo_viewer_label, "Return · 16:31")
  }

  @Test
  fun aDepositUsedUpByLateFeesShowsZeroNotUnknown() {
    setContent(returning.copy(deposit = 80, lateFees = 80))

    text(C.Tag.handover_deposit, "0 PP")
  }
}
