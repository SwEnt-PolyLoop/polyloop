// Made with Claude.

package com.swent.polyloop.ui.handover

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
  // Shown by setContent; changing it redraws the card, so one test can go through several states.
  private var uiState by mutableStateOf(ready)

  private fun setContent(state: HandoverUiState) {
    uiState = state
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
  fun theButtonFollowsCanScan() {
    // Whether each state can scan is tested in HandoverViewModelTest; here, only the wiring.
    val states =
        listOf(
            ready,
            ready.copy(photos = photos(1)),
            ready.copy(isSendingScan = true),
            ready.copy(scanError = ScanError.WRONG_CODE),
            ready.copy(scanError = ScanError.RENTAL_CHANGED),
            ready.copy(isOffline = true),
            ready.copy(codeFingerprint = null, codeFailed = true),
        )
    setContent(ready)
    for (state in states) {
      uiState = state
      composeTestRule.waitForIdle()
      if (state.canScan) button().assertIsEnabled() else button().assertIsNotEnabled()
    }
  }

  @Test
  fun aBlankNameIsReplacedByTheRole() {
    setContent(ready.copy(otherPersonName = ""))
    node(C.Tag.handover_scan_title).assertTextEquals("Scan the lender's code")
  }

  @Test
  fun atReturnThereIsNoScanCardYet() {
    // The server makes no return code yet, so the lender has nothing to scan.
    setContent(ready.copy(isLender = true, phase = HandoverPhase.RETURN))
    node(C.Tag.handover_scan_card).assertDoesNotExist()
    node(C.Tag.handover_code_card).assertDoesNotExist()
  }

  @Test
  fun theScanWaitsForThreePhotos() {
    setContent(ready.copy(photos = photos(1)))
    assertMessage("Take 2 more photos first.")
  }

  @Test
  fun oneMissingPhotoIsSingular() {
    setContent(ready.copy(photos = photos(2)))
    assertMessage("Take 1 more photo first.")
  }

  @Test
  fun whileSendingTheButtonShowsSending() {
    setContent(ready.copy(isSendingScan = true))
    composeTestRule.onNodeWithText("Sending…", useUnmergedTree = true).assertIsDisplayed()
  }

  @Test
  fun aWrongCodeNamesTheLender() {
    setContent(ready.copy(scanError = ScanError.WRONG_CODE))
    assertMessage("This isn't the right code. Scan Loca's code on their Handover screen.")
  }

  @Test
  fun aWrongCodeWithoutAName() {
    setContent(ready.copy(scanError = ScanError.WRONG_CODE, otherPersonName = ""))
    assertMessage("This isn't the right code. Scan the code on their Handover screen.")
  }

  @Test
  fun aScanNotSentAsksToTryAgain() {
    setContent(ready.copy(scanError = ScanError.NOT_SENT))
    assertMessage("The scan couldn't be sent. Try again in a moment.")
  }

  @Test
  fun aRefusedScanSaysWhatToCheck() {
    setContent(ready.copy(scanError = ScanError.REFUSED))
    assertMessage(
        "The pickup couldn't be confirmed. Check that you have enough PolyPoints for this rental " +
            "and that today is the pickup day."
    )
  }

  @Test
  fun aScannerThatCannotOpenSaysSo() {
    setContent(ready.copy(scanError = ScanError.SCANNER_UNAVAILABLE))
    assertMessage("The scanner couldn't open. Try again in a moment.")
  }

  @Test
  fun aRentalThatChangedSaysWhereToLook() {
    setContent(ready.copy(scanError = ScanError.RENTAL_CHANGED))
    assertMessage(
        "This rental changed and can't be picked up any more. Open the rental to see where it stands."
    )
  }

  @Test
  fun offlineSaysScanningIsComingSoon() {
    setContent(ready.copy(isOffline = true))
    assertMessage("Scanning offline is coming soon.")
  }

  @Test
  fun offlineWithoutACopyAsksToConnectOnce() {
    setContent(ready.copy(isOffline = true, codeFingerprint = null, codeFailed = true))
    assertMessage("Connect to the internet once to prepare the pickup.")
  }

  @Test
  fun aFingerprintThatFailedOnlineCanBeRetried() {
    setContent(ready.copy(codeFingerprint = null, codeFailed = true))
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
  fun aScanSentAtMidnightKeepsItsCardUntilTheAnswer() {
    // The day ends while the scan is sent: the card stays, then shows the answer.
    setContent(ready.copy(isSendingScan = true, isDayOver = true))
    composeTestRule.onNodeWithText("Sending…", useUnmergedTree = true).assertIsDisplayed()
    uiState = uiState.copy(serverToday = LocalDate.of(2026, 10, 7), isDayOver = false)
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Sending…", useUnmergedTree = true).assertIsDisplayed()
    uiState = uiState.copy(isSendingScan = false, scanConfirmed = true)
    node(C.Tag.handover_scan_confirmed)
        .performScrollTo()
        .assertTextEquals("Picked up · the rental has started")
    // Without a scan, the next day has no card.
    uiState = uiState.copy(scanConfirmed = false)
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
