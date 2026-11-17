// Made with Claude.

package com.swent.polyloop.ui.handover

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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

/** The card where the borrower scans the lender's QR code at pickup (Figma 15 and 15.10). */
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
          isPickupDay = true,
          photos = photos(3),
          codeFingerprint = "fingerprint",
      )

  private var scans = 0

  private fun setContent(uiState: HandoverUiState) {
    composeTestRule.setContent {
      HandoverContent(
          uiState = uiState,
          onRetry = {},
          onAddPhoto = {},
          onRetryUpload = {},
          onScan = { scans++ },
      )
    }
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true)

  private fun button() = node(C.Tag.handover_scan_button).performScrollTo()

  @Test
  fun theBorrowerCanScanTheLendersCode() {
    setContent(ready)
    node(C.Tag.handover_scan_title).assertTextEquals("Scan Loca's code")
    button().assertIsEnabled().performClick()
    assertEquals(1, scans)
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
  }

  @Test
  fun theLenderAtPickupHasNoScanCard() {
    setContent(ready.copy(isLender = true))
    node(C.Tag.handover_scan_card).assertDoesNotExist()
  }

  @Test
  fun noScanCardOutsideThePickupDay() {
    setContent(ready.copy(isPickupDay = false))
    node(C.Tag.handover_scan_card).assertDoesNotExist()
  }
}
