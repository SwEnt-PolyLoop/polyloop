// Made with Claude.

package com.swent.polyloop.ui.rentaldetail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swent.polyloop.R
import com.swent.polyloop.model.rental.RentalStatus
import com.swent.polyloop.resources.C
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The cancel row of Rental detail and its confirm sheet. The window goes by
 * [RentalDetailUiState.serverToday]; the phone's date ([phoneToday]) only decides whether the year
 * is written.
 */
@RunWith(AndroidJUnit4::class)
class RentalDetailCancelTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  private val pickupDay = LocalDate.of(2026, 10, 1)
  // Wed 30 Sep 2026.
  private val lastCancelDay = pickupDay.minusDays(1)
  private val phoneToday = LocalDate.of(2026, 9, 28)

  private val requested =
      RentalDetailUiState(
          isLender = false,
          itemTitle = "Ski helmet, size M",
          status = RentalStatus.REQUESTED,
          sentAt = Instant.parse("2026-09-27T10:00:00Z"),
          startDate = pickupDay,
          endDate = pickupDay.plusDays(7),
          rentalPrice = 120,
          suggestedDeposit = 180,
          pickupArea = "Ecublens",
          otherPerson =
              OtherPerson(uid = "lea", name = "Léa", averageRating = 4.6, reviewCount = 5),
          serverToday = LocalDate.of(2026, 9, 28),
      )

  private val accepted =
      requested.copy(
          status = RentalStatus.ACCEPTED,
          deposit = 60,
          pickupAddress = "Route Cantonale 1",
          returnAddress = "Rue de Lausanne 10",
      )

  private var cancelClicks = 0

  private fun setContent(uiState: RentalDetailUiState, today: LocalDate = phoneToday) {
    composeTestRule.setContent { Content(uiState, today) }
  }

  @Composable
  private fun Content(uiState: RentalDetailUiState, today: LocalDate = phoneToday) {
    RentalDetailContent(
        uiState = uiState,
        onRetry = {},
        onChatClick = {},
        onPersonClick = {},
        onStartPickupClick = {},
        onStartReturnClick = {},
        onViewDisputeClick = {},
        onCancelClick = { cancelClicks++ },
        today = today,
        zone = ZoneOffset.UTC,
        // English is fixed so the expected dates pass on an emulator in any language.
        locale = Locale.ENGLISH,
    )
  }

  private fun cancelButton() =
      composeTestRule.onNodeWithTag(C.Tag.rental_detail_cancel_button).performScrollTo()

  private fun assertCancelUntil(day: String) {
    composeTestRule
        .onNodeWithTag(C.Tag.rental_detail_cancel_until)
        .performScrollTo()
        .assertIsDisplayed()
        .assertTextEquals(context.getString(R.string.rental_detail_cancel_until, day))
  }

  private fun assertNoCancelUntil() {
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_cancel_until).assertDoesNotExist()
  }

  private fun assertNoCancelRow() {
    // Scrolls to the end of the column, where the row would be.
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_pickup_point_label).performScrollTo()
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_cancel_button).assertDoesNotExist()
    assertNoCancelUntil()
  }

  private fun assertSheetClosed() {
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_cancel_sheet).assertDoesNotExist()
  }

  // ---------- Requested ----------

  @Test
  fun theBorrowerCanCancelARequestUntilTheDayBeforePickup() {
    setContent(requested)

    assertCancelUntil("Wed 30 Sep")
    cancelButton()
        .assertTextEquals(context.getString(R.string.rental_detail_cancel_request))
        .assertIsEnabled()
  }

  @Test
  fun theLenderCanCancelARequestToo() {
    setContent(requested.copy(isLender = true))

    assertCancelUntil("Wed 30 Sep")
    cancelButton()
        .assertTextEquals(context.getString(R.string.rental_detail_cancel_request))
        .assertIsEnabled()
  }

  @Test
  fun theLastCancelDayShowsItsYearWhenNotThisYear() {
    val start = LocalDate.of(2027, 1, 6)
    setContent(
        requested.copy(
            startDate = start,
            endDate = start.plusDays(2),
            serverToday = LocalDate.of(2026, 12, 1),
        ),
        today = LocalDate.of(2026, 12, 1),
    )

    assertCancelUntil("Tue 5 Jan 2027")
  }

  @Test
  fun aRequestKeepsOnlyItsEnabledButtonOnThePickupDay() {
    setContent(requested.copy(serverToday = pickupDay))

    cancelButton().assertIsDisplayed().assertIsEnabled()
    assertNoCancelUntil()
  }

  @Test
  fun aRequestKeepsOnlyItsEnabledButtonAfterThePickupDay() {
    setContent(requested.copy(serverToday = pickupDay.plusDays(3)))

    cancelButton().assertIsDisplayed().assertIsEnabled()
    assertNoCancelUntil()
  }

  // ---------- Accepted ----------

  @Test
  fun theBorrowerCanCancelAnAcceptedRentalOnItsLastDay() {
    // The phone says the pickup day is past; only the server's date counts.
    setContent(accepted.copy(serverToday = lastCancelDay), today = pickupDay.plusDays(4))

    assertCancelUntil("Wed 30 Sep")
    cancelButton()
        .assertTextEquals(context.getString(R.string.rental_detail_cancel_rental))
        .assertIsEnabled()
  }

  @Test
  fun theLenderCanCancelAnAcceptedRentalOnItsLastDay() {
    setContent(accepted.copy(isLender = true, serverToday = lastCancelDay))

    assertCancelUntil("Wed 30 Sep")
    cancelButton()
        .assertTextEquals(context.getString(R.string.rental_detail_cancel_rental))
        .assertIsEnabled()
  }

  @Test
  fun anAcceptedRentalHasNoCancelRowOnItsPickupDay() {
    // The phone says it is still days before pickup; only the server's date counts.
    setContent(accepted.copy(serverToday = pickupDay), today = phoneToday)

    assertNoCancelRow()
  }

  @Test
  fun anAcceptedRentalHasNoCancelRowAfterItsPickupDay() {
    setContent(accepted.copy(serverToday = pickupDay.plusDays(2)), today = phoneToday)

    assertNoCancelRow()
  }

  // ---------- Disabled ----------

  @Test
  fun anUnknownServerDayDisablesTheButtonAndWritesNoDate() {
    setContent(accepted.copy(serverToday = null))

    cancelButton().assertIsDisplayed().assertIsNotEnabled()
    assertNoCancelUntil()
  }

  @Test
  fun readingTheServerDayDisablesTheButtonAndWritesNoDate() {
    setContent(requested.copy(serverToday = null, isReadingServerToday = true))

    cancelButton().assertIsDisplayed().assertIsNotEnabled()
    assertNoCancelUntil()
  }

  @Test
  fun theButtonIsDisabledOffline() {
    setContent(requested.copy(isOffline = true))

    cancelButton().assertIsNotEnabled()
    assertCancelUntil("Wed 30 Sep")
  }

  @Test
  fun theButtonIsDisabledWhileCancelling() {
    setContent(requested.copy(isCancelling = true))

    cancelButton().assertIsNotEnabled()
  }

  // ---------- The confirm sheet ----------

  @Test
  fun tappingCancelOpensTheSheetWithNoThenYes() {
    setContent(requested)

    cancelButton().performClick()

    composeTestRule.onNodeWithTag(C.Tag.rental_detail_cancel_sheet).assertIsDisplayed()
    composeTestRule
        .onNodeWithText(context.getString(R.string.rental_detail_cancel_confirm_title))
        .assertIsDisplayed()
    val no =
        composeTestRule
            .onNodeWithTag(C.Tag.rental_detail_cancel_no)
            .assertIsDisplayed()
            .assertTextEquals(context.getString(R.string.rental_detail_cancel_no))
    val yes =
        composeTestRule
            .onNodeWithTag(C.Tag.rental_detail_cancel_yes)
            .assertIsDisplayed()
            .assertTextEquals(context.getString(R.string.rental_detail_cancel_yes))
    assertTrue(no.getUnclippedBoundsInRoot().left < yes.getUnclippedBoundsInRoot().left)
    assertEquals(0, cancelClicks)
  }

  @Test
  fun noClosesTheSheetWithoutCancelling() {
    setContent(requested)
    cancelButton().performClick()

    composeTestRule.onNodeWithTag(C.Tag.rental_detail_cancel_no).performClick()

    assertSheetClosed()
    assertEquals(0, cancelClicks)
  }

  @Test
  fun yesClosesTheSheetAndCancelsOnce() {
    setContent(accepted.copy(serverToday = lastCancelDay))
    cancelButton().performClick()

    composeTestRule.onNodeWithTag(C.Tag.rental_detail_cancel_yes).performClick()

    assertSheetClosed()
    assertEquals(1, cancelClicks)
  }

  @Test
  fun aDisabledButtonOpensNoSheet() {
    setContent(requested.copy(isOffline = true))

    cancelButton().performClick()

    assertSheetClosed()
    assertEquals(0, cancelClicks)
  }

  @Test
  fun theSheetClosesWhenCancellingIsNoLongerPossible() {
    var uiState by mutableStateOf(requested)
    composeTestRule.setContent { Content(uiState) }
    cancelButton().performClick()
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_cancel_sheet).assertIsDisplayed()

    uiState = uiState.copy(isOffline = true)
    composeTestRule.waitForIdle()
    assertSheetClosed()

    // Back online, it does not open again by itself.
    uiState = uiState.copy(isOffline = false)
    composeTestRule.waitForIdle()
    assertSheetClosed()
    assertEquals(0, cancelClicks)
  }

  // ---------- Errors ----------

  @Test
  fun aFailedCancelShowsItsMessage() {
    setContent(requested.copy(cancelError = "The pickup day has come"))

    composeTestRule
        .onNodeWithTag(C.Tag.rental_detail_cancel_error)
        .performScrollTo()
        .assertIsDisplayed()
        .assertTextEquals("The pickup day has come")
  }

  @Test
  fun aFailedCancelWithoutAMessageShowsTheFallback() {
    setContent(requested.copy(cancelError = ""))

    composeTestRule
        .onNodeWithTag(C.Tag.rental_detail_cancel_error)
        .performScrollTo()
        .assertIsDisplayed()
        .assertTextEquals(context.getString(R.string.rental_detail_cancel_error_fallback))
  }

  @Test
  fun noErrorLineWithoutAFailure() {
    setContent(requested)

    cancelButton()
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_cancel_error).assertDoesNotExist()
  }
}
