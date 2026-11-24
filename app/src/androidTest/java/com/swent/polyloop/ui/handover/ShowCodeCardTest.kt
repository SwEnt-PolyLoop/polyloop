// Made with Claude.

package com.swent.polyloop.ui.handover

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swent.polyloop.R
import com.swent.polyloop.model.handover.HandoverPhase
import com.swent.polyloop.model.handover.HandoverPhoto
import com.swent.polyloop.resources.C
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The card where the person giving the item shows the QR code: the lender at pickup (Figma 14,
 * 14.10 to 14.13), the borrower at return (Figma 16).
 */
@RunWith(AndroidJUnit4::class)
class ShowCodeCardTest {

  @get:Rule val composeTestRule = createComposeRule()

  private fun photos(count: Int) =
      List(count) { HandoverPhoto("p$it", HandoverPhase.PICKUP, Instant.EPOCH, "") }

  /** The lender on the pickup day, with 3 photos and the code. */
  private val ready =
      HandoverUiState(
          isLoading = false,
          isLender = true,
          itemTitle = "Camping tent",
          otherPersonName = "Mara",
          phase = HandoverPhase.PICKUP,
          pickupDate = LocalDate.of(2026, 10, 6),
          serverToday = LocalDate.of(2026, 10, 6),
          photos = photos(3),
          code = "secret-code",
      )

  private var retries = 0

  private fun setContent(uiState: HandoverUiState) {
    composeTestRule.setContent {
      HandoverContent(
          uiState = uiState,
          onRetry = { retries++ },
          onAddPhoto = {},
          onRetryUpload = {},
      )
    }
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true)

  private fun assertMessage(text: String) =
      node(C.Tag.handover_code_message).performScrollTo().assertTextEquals(text)

  @Test
  fun theLenderSeesTheQrWithTheBorrowersName() {
    setContent(ready)
    node(C.Tag.handover_code_title).assertTextEquals("Show this code to Mara")
    node(C.Tag.handover_qr_code).performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("Your handover QR code").assertExists()
    node(C.Tag.handover_code_message).assertDoesNotExist()
  }

  @Test
  fun aBlankNameIsReplacedByTheRole() {
    setContent(ready.copy(otherPersonName = " "))
    node(C.Tag.handover_code_title).assertTextEquals("Show this code to the borrower")
  }

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  /** The borrower on the return day, with 3 photos and the code. */
  private val atReturn =
      ready.copy(
          isLender = false,
          phase = HandoverPhase.RETURN,
          returnDate = LocalDate.of(2026, 10, 8),
          serverToday = LocalDate.of(2026, 10, 8),
      )

  @Test
  fun atReturnTheBorrowerShowsTheQrToTheLender() {
    setContent(atReturn)
    node(C.Tag.handover_code_title)
        .assertTextEquals(context.getString(R.string.handover_show_code_title, "Mara"))
    node(C.Tag.handover_qr_code).performScrollTo().assertIsDisplayed()
  }

  @Test
  fun atReturnOfflineWithoutACopyAsksToConnectOnceForTheReturn() {
    setContent(atReturn.copy(isOffline = true, code = null, codeFailed = true))
    assertMessage(context.getString(R.string.handover_code_no_copy_return))
  }

  @Test
  fun noCodeCardOnceThePickupDayIsOver() {
    // Midnight passed: the day is still shown while it is read again, but the code is not.
    setContent(ready.copy(isDayOver = true))
    node(C.Tag.handover_code_card).assertDoesNotExist()
  }

  @Test
  fun theQrWaitsForThreePhotos() {
    setContent(ready.copy(photos = photos(1)))
    node(C.Tag.handover_qr_code).assertDoesNotExist()
    assertMessage("Take 2 more photos to show your code.")
  }

  @Test
  fun oneMissingPhotoIsSingular() {
    setContent(ready.copy(photos = photos(2)))
    assertMessage("Take 1 more photo to show your code.")
  }

  @Test
  fun missingPhotosComeBeforeAMissingCode() {
    setContent(ready.copy(photos = emptyList(), code = null, codeFailed = true))
    assertMessage("Take 3 more photos to show your code.")
    node(C.Tag.handover_code_retry_button).assertDoesNotExist()
  }

  @Test
  fun offlineTheQrStaysWithANote() {
    setContent(ready.copy(isOffline = true))
    node(C.Tag.handover_qr_code).assertExists()
    assertMessage("Once Mara has scanned, you're done. This updates when you're back online.")
  }

  @Test
  fun theOfflineNoteWithoutAName() {
    setContent(ready.copy(isOffline = true, otherPersonName = ""))
    assertMessage("Once they have scanned, you're done. This updates when you're back online.")
  }

  @Test
  fun offlineWithoutACopyAsksToConnectOnce() {
    setContent(ready.copy(isOffline = true, code = null, codeFailed = true))
    node(C.Tag.handover_qr_code).assertDoesNotExist()
    assertMessage("Connect to the internet once to prepare the pickup.")
    node(C.Tag.handover_code_retry_button).assertDoesNotExist()
  }

  @Test
  fun aCodeThatFailedOnlineCanBeRetried() {
    setContent(ready.copy(code = null, codeFailed = true))
    assertMessage("Couldn't load your code.")
    node(C.Tag.handover_code_retry_button).performScrollTo().performClick()
    assertEquals(1, retries)
  }

  @Test
  fun aSpinnerShowsWhileTheCodeIsRead() {
    setContent(ready.copy(code = null))
    node(C.Tag.handover_code_loading).assertExists()
    node(C.Tag.handover_code_message).assertDoesNotExist()
  }

  @Test
  fun theBorrowerAtPickupHasNoCodeCard() {
    setContent(ready.copy(isLender = false))
    node(C.Tag.handover_code_card).assertDoesNotExist()
  }

  @Test
  fun noCodeCardOutsideThePickupDay() {
    setContent(ready.copy(serverToday = LocalDate.of(2026, 10, 7)))
    node(C.Tag.handover_code_card).assertDoesNotExist()
  }

  @Test
  fun onceScannedTheLenderSeesPickedUpByTheBorrower() {
    setContent(ready.copy(isPickedUp = true))
    node(C.Tag.handover_code_picked_up)
        .performScrollTo()
        .assertTextEquals("Picked up by Mara · the rental has started")
    node(C.Tag.handover_qr_code).assertDoesNotExist()
    node(C.Tag.handover_code_title).assertDoesNotExist()
  }

  @Test
  fun pickedUpWithoutAName() {
    setContent(ready.copy(isPickedUp = true, otherPersonName = ""))
    node(C.Tag.handover_code_picked_up).assertTextEquals("Picked up · the rental has started")
  }
}
