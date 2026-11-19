// Made with Claude.

package com.swent.polyloop.ui.handover

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.polyloop.model.handover.HandoverPhase
import com.swent.polyloop.model.handover.HandoverPhoto
import com.swent.polyloop.resources.C
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The card where the borrower scans the lender's QR code at pickup (Figma 15, 15.10 to 15.15). */
@RunWith(AndroidJUnit4::class)
class ScanCodeCardTest {

  @get:Rule val composeTestRule = createComposeRule()

  private fun photos(count: Int) =
      List(count) { HandoverPhoto("p$it", HandoverPhase.PICKUP, Instant.EPOCH, "") }

  /** The borrower on the pickup day, online, with 3 photos and the fingerprint. */
  private val ready =
      HandoverUiState(
          isLoading = false,
          itemTitle = "Camping tent",
          otherPersonName = "Loca",
          phase = HandoverPhase.PICKUP,
          pickupDate = LocalDate.of(2026, 10, 6),
          serverToday = LocalDate.of(2026, 10, 6),
          photos = photos(3),
          codeFingerprint = "fingerprint",
      )

  private var scans = 0
  private var retries = 0

  private fun setContent(uiState: HandoverUiState) {
    composeTestRule.setContent {
      HandoverContent(
          uiState = uiState,
          onRetry = { retries++ },
          onAddPhoto = {},
          onRetryUpload = {},
          onScan = { scans++ },
      )
    }
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true)

  private fun button() = node(C.Tag.handover_scan_button).performScrollTo()

  private fun assertMessage(text: String) =
      node(C.Tag.handover_scan_message).performScrollTo().assertTextEquals(text)

  @Test
  fun theBorrowerCanScanTheLendersCode() {
    setContent(ready)
    node(C.Tag.handover_scan_title).assertTextEquals("Scan Loca's code")
    button().assertIsEnabled().performClick()
    assertEquals(1, scans)
    node(C.Tag.handover_scan_message).assertDoesNotExist()
  }

  @Test
  fun aBlankNameIsReplacedByTheRole() {
    setContent(ready.copy(otherPersonName = ""))
    node(C.Tag.handover_scan_title).assertTextEquals("Scan the lender's code")
  }

  @Test
  fun atReturnTheLenderScansTheBorrowersCode() {
    setContent(ready.copy(isLender = true, phase = HandoverPhase.RETURN, otherPersonName = ""))
    node(C.Tag.handover_scan_title).assertTextEquals("Scan the borrower's code")
    node(C.Tag.handover_code_card).assertDoesNotExist()
  }

  @Test
  fun theScanWaitsForThreePhotos() {
    setContent(ready.copy(photos = photos(1)))
    button().assertIsNotEnabled()
    assertMessage("Take 2 more photos first.")
  }

  @Test
  fun oneMissingPhotoIsSingular() {
    setContent(ready.copy(photos = photos(2)))
    assertMessage("Take 1 more photo first.")
  }

  @Test
  fun whileSendingTheButtonShowsSendingAndIsOff() {
    setContent(ready.copy(isSendingScan = true))
    button().assertIsNotEnabled()
    composeTestRule.onNodeWithText("Sending…", useUnmergedTree = true).assertIsDisplayed()
  }

  @Test
  fun aWrongCodeNamesTheLenderAndTheButtonStaysOn() {
    setContent(ready.copy(scanError = ScanError.WRONG_CODE))
    assertMessage("This isn't the right code. Scan Loca's code on their Handover screen.")
    button().assertIsEnabled()
  }

  @Test
  fun aWrongCodeWithoutAName() {
    setContent(ready.copy(scanError = ScanError.WRONG_CODE, otherPersonName = ""))
    assertMessage("This isn't the right code. Scan the code on their Handover screen.")
  }

  @Test
  fun aScanNotSentCanBeScannedAgain() {
    setContent(ready.copy(scanError = ScanError.NOT_SENT))
    assertMessage("The scan couldn't be sent. Try again in a moment.")
    button().assertIsEnabled()
  }

  @Test
  fun aScannerThatCannotOpenSaysSoAndTheButtonStaysOn() {
    setContent(ready.copy(scanError = ScanError.SCANNER_UNAVAILABLE))
    assertMessage("The scanner couldn't open. Try again in a moment.")
    button().assertIsEnabled()
  }

  @Test
  fun aRentalThatChangedTurnsTheScanOff() {
    setContent(ready.copy(scanError = ScanError.RENTAL_CHANGED))
    assertMessage(
        "This rental changed and can't be picked up any more. Open the rental to see where it stands."
    )
    button().assertIsNotEnabled()
  }

  @Test
  fun offlineTheScanWaitsForTheConnection() {
    setContent(ready.copy(isOffline = true))
    button().assertIsNotEnabled()
    assertMessage("Scanning offline is coming soon.")
  }

  @Test
  fun offlineWithoutACopyAsksToConnectOnce() {
    setContent(ready.copy(isOffline = true, codeFingerprint = null, codeFailed = true))
    button().assertIsNotEnabled()
    assertMessage("Connect to the internet once to prepare the pickup.")
  }

  @Test
  fun aFingerprintThatFailedOnlineCanBeRetried() {
    setContent(ready.copy(codeFingerprint = null, codeFailed = true))
    button().assertIsNotEnabled()
    assertMessage("Couldn't prepare the scan.")
    node(C.Tag.handover_scan_retry_button).performScrollTo().performClick()
    assertEquals(1, retries)
  }

  @Test
  fun missingPhotosComeFirst() {
    setContent(ready.copy(photos = emptyList(), isOffline = true, scanError = ScanError.NOT_SENT))
    assertMessage("Take 3 more photos first.")
  }

  @Test
  fun onceConfirmedOnlyPickedUpShows() {
    setContent(ready.copy(scanConfirmed = true))
    node(C.Tag.handover_scan_confirmed)
        .performScrollTo()
        .assertTextEquals("Picked up · the rental has started")
    node(C.Tag.handover_scan_button).assertDoesNotExist()
    node(C.Tag.handover_scan_title).assertDoesNotExist()
  }

  @Test
  fun theLenderAtPickupHasNoScanCard() {
    setContent(ready.copy(isLender = true))
    node(C.Tag.handover_scan_card).assertDoesNotExist()
  }

  @Test
  fun noScanCardOutsideThePickupDay() {
    setContent(ready.copy(serverToday = LocalDate.of(2026, 10, 7)))
    node(C.Tag.handover_scan_card).assertDoesNotExist()
  }

  @Test
  fun aPickupSeenFromTheServerAlsoShowsPickedUp() {
    // No scan from this phone (scanConfirmed is false): the rental became ACTIVE on the server.
    setContent(ready.copy(isPickedUp = true))
    node(C.Tag.handover_scan_confirmed)
        .performScrollTo()
        .assertTextEquals("Picked up · the rental has started")
    node(C.Tag.handover_scan_button).assertDoesNotExist()
  }
}
