// Made with Claude.

package com.swent.polyloop.ui.myrentals

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swent.polyloop.R
import com.swent.polyloop.model.auth.AuthRepository
import com.swent.polyloop.model.auth.AuthResult
import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.rental.FakeRentalRepository
import com.swent.polyloop.model.rental.Rental
import com.swent.polyloop.model.rental.RentalStatus
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.components.formatDay
import com.swent.polyloop.ui.components.formatDayRange
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MyRentalsScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  private fun row(
      id: String,
      status: RentalStatus = RentalStatus.REQUESTED,
      person: String = "Loca",
      location: String = "Renens",
      returnDate: LocalDate = LocalDate.of(2026, 10, 22),
      isPickupArea: Boolean = true,
      showReturnDate: Boolean = true,
  ) =
      RentalRow(
          rentalId = id,
          itemTitle = "Item $id",
          otherPersonName = person,
          startDate = LocalDate.of(2026, 10, 20),
          returnDate = returnDate,
          location = location,
          status = status,
          isPickupArea = isPickupArea,
          showReturnDate = showReturnDate,
      )

  /** A pending request for "Camping tent" from Loca, 15 to 17 Oct 2026, 45 PP reserved. */
  private fun request(id: String, person: String = "Loca") =
      RentalRow(
          rentalId = id,
          itemTitle = "Camping tent",
          otherPersonName = person,
          startDate = LocalDate.of(2026, 10, 15),
          returnDate = LocalDate.of(2026, 10, 17),
          location = "Ecublens",
          status = RentalStatus.REQUESTED,
          isPickupArea = true,
          showReturnDate = true,
          totalPrice = 45,
      )

  private fun setContent(
      state: MyRentalsUiState,
      onTabSelected: (RentalsTab) -> Unit = {},
      onRentalClick: (String) -> Unit = {},
      onRetry: () -> Unit = {},
      onReviewRequest: (String) -> Unit = {},
  ) {
    composeTestRule.setContent {
      MyRentalsContent(
          uiState = state,
          onTabSelected = onTabSelected,
          onRentalClick = onRentalClick,
          onRetry = onRetry,
          onReviewRequest = onReviewRequest,
          today = TODAY,
      )
    }
  }

  private fun inRow(id: String, tag: String) =
      composeTestRule.onNode(
          hasTestTag(tag) and hasAnyAncestor(hasTestTag(C.Tag.my_rentals_row_ + id)),
          useUnmergedTree = true,
      )

  private fun inRequest(id: String, tag: String) =
      composeTestRule.onNode(
          hasTestTag(tag) and hasAnyAncestor(hasTestTag(C.Tag.my_rentals_request_ + id)),
          useUnmergedTree = true,
      )

  /** Shows the content with a state the test can change; the returned function sets it. */
  private fun setMutableContent(): (MyRentalsUiState) -> Unit {
    var state by mutableStateOf(MyRentalsUiState(isLoading = false))
    composeTestRule.setContent {
      MyRentalsContent(
          uiState = state,
          onTabSelected = {},
          onRentalClick = {},
          onRetry = {},
          today = TODAY,
      )
    }
    return {
      state = it
      composeTestRule.waitForIdle()
    }
  }

  private fun lending(
      requests: List<RentalRow> = emptyList(),
      current: List<RentalRow> = emptyList(),
  ) =
      MyRentalsUiState(
          selectedTab = RentalsTab.LENDING,
          lendingRequests = requests,
          lendingCurrent = current,
          isLoading = false,
      )

  @Test
  fun showsTitleAndBothTabsWithBorrowingSelected() {
    setContent(MyRentalsUiState(isLoading = false))

    composeTestRule.onNodeWithTag(C.Tag.my_rentals_title).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.my_rentals_tab_borrowing).assertIsSelected()
    composeTestRule.onNodeWithTag(C.Tag.my_rentals_tab_lending).assertIsDisplayed()
  }

  @Test
  fun showsLoadingIndicator() {
    setContent(MyRentalsUiState(isLoading = true))

    composeTestRule.onNodeWithTag(C.Tag.my_rentals_loading).assertIsDisplayed()
  }

  @Test
  fun rowsStayVisibleWhileReloading() {
    setContent(MyRentalsUiState(borrowing = listOf(row("tent")), isLoading = true))

    composeTestRule.onNodeWithTag(C.Tag.my_rentals_loading).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.my_rentals_row_ + "tent").assertIsDisplayed()
  }

  @Test
  fun errorWithRowsKeepsTheRowsUnderIt() {
    var retries = 0
    setContent(
        MyRentalsUiState(
            borrowing = listOf(row("tent")),
            isLoading = false,
            errorMsg = "No connection",
        ),
        onRetry = { retries++ },
    )

    composeTestRule.onNodeWithTag(C.Tag.my_rentals_error).assertTextEquals("No connection")
    composeTestRule.onNodeWithTag(C.Tag.my_rentals_row_ + "tent").assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.my_rentals_retry_button).performClick()
    assertEquals(1, retries)
  }

  @Test
  fun showsErrorMessageAndRetryCallsBack() {
    var retries = 0
    setContent(
        MyRentalsUiState(isLoading = false, errorMsg = "No connection"),
        onRetry = { retries++ },
    )

    composeTestRule.onNodeWithTag(C.Tag.my_rentals_error).assertTextEquals("No connection")
    composeTestRule.onNodeWithTag(C.Tag.my_rentals_retry_button).performClick()
    assertEquals(1, retries)
  }

  @Test
  fun blankErrorShowsTheGenericText() {
    setContent(MyRentalsUiState(isLoading = false, errorMsg = ""))

    composeTestRule
        .onNodeWithTag(C.Tag.my_rentals_error)
        .assertTextEquals(context.getString(R.string.my_rentals_error_fallback))
  }

  @Test
  fun emptyBorrowingShowsItsEmptyText() {
    setContent(MyRentalsUiState(isLoading = false))

    composeTestRule
        .onNodeWithTag(C.Tag.my_rentals_empty)
        .assertTextEquals(context.getString(R.string.my_rentals_empty_borrowing))
  }

  @Test
  fun emptyLendingShowsItsEmptyText() {
    setContent(
        MyRentalsUiState(
            selectedTab = RentalsTab.LENDING,
            borrowing = listOf(row("tent")),
            isLoading = false,
        )
    )

    composeTestRule
        .onNodeWithTag(C.Tag.my_rentals_empty)
        .assertTextEquals(context.getString(R.string.my_rentals_empty_lending))
    composeTestRule.onNodeWithTag(C.Tag.my_rentals_section_requests).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.my_rentals_section_current).assertDoesNotExist()
  }

  @Test
  fun borrowedRowShowsItemLenderReturnDateLocationAndStatus() {
    setContent(MyRentalsUiState(borrowing = listOf(row("tent")), isLoading = false))

    inRow("tent", C.Tag.my_rentals_row_title).assertTextEquals("Item tent")
    inRow("tent", C.Tag.my_rentals_row_person)
        .assertTextEquals(context.getString(R.string.my_rentals_lender, "Loca"))
    inRow("tent", C.Tag.my_rentals_row_return)
        .assertTextEquals(
            context.getString(R.string.my_rentals_return_by, formatDay(LocalDate.of(2026, 10, 22)))
        )
    inRow("tent", C.Tag.my_rentals_row_location)
        .assertTextEquals(context.getString(R.string.my_rentals_pickup_at, "Renens"))
    inRow("tent", C.Tag.my_rentals_row_status)
        .assertTextEquals(context.getString(R.string.rental_status_requested))
  }

  @Test
  fun pickedUpItemShowsTheDropOffArea() {
    setContent(
        MyRentalsUiState(
            borrowing = listOf(row("tent", status = RentalStatus.ACTIVE, isPickupArea = false)),
            isLoading = false,
        )
    )

    inRow("tent", C.Tag.my_rentals_row_location)
        .assertTextEquals(context.getString(R.string.my_rentals_drop_off_at, "Renens"))
  }

  @Test
  fun returnDateIsHiddenWhenTheRowSaysSo() {
    setContent(
        MyRentalsUiState(
            borrowing = listOf(row("tent", status = RentalStatus.CLOSED, showReturnDate = false)),
            isLoading = false,
        )
    )

    inRow("tent", C.Tag.my_rentals_row_return).assertDoesNotExist()
  }

  @Test
  fun returnDateInAnotherYearShowsTheYear() {
    val nextYear = LocalDate.of(2027, 1, 5)
    setContent(
        MyRentalsUiState(borrowing = listOf(row("tent", returnDate = nextYear)), isLoading = false)
    )

    inRow("tent", C.Tag.my_rentals_row_return)
        .assertTextEquals(
            context.getString(R.string.my_rentals_return_by, formatDay(nextYear, withYear = true))
        )
  }

  @Test
  fun currentCardNamesTheBorrowerWithoutPrefix() {
    setContent(lending(current = listOf(row("drill", status = RentalStatus.ACTIVE))))

    inRow("drill", C.Tag.my_rentals_row_person).assertTextEquals("Loca")
  }

  @Test
  fun acceptedCurrentCardShowsThePickupDayAndArea() {
    val accepted =
        row("drill", status = RentalStatus.ACCEPTED, location = "Renens")
            .copy(startDate = LocalDate.of(2026, 10, 22))
    setContent(lending(current = listOf(accepted)))

    inRow("drill", C.Tag.my_rentals_row_line)
        .assertTextEquals("Pickup ${formatDay(LocalDate.of(2026, 10, 22))} · Renens")
  }

  @Test
  fun activeAndOverdueCurrentCardsShowTheReturnDayAndArea() {
    val setState = setMutableContent()

    listOf(RentalStatus.ACTIVE, RentalStatus.OVERDUE).forEach { status ->
      val row =
          row(
              "drill",
              status = status,
              location = "EPFL",
              returnDate = LocalDate.of(2026, 10, 9),
              isPickupArea = false,
          )
      setState(lending(current = listOf(row)))
      inRow("drill", C.Tag.my_rentals_row_line)
          .assertTextEquals("Return by ${formatDay(LocalDate.of(2026, 10, 9))} · EPFL")
    }
  }

  @Test
  fun currentCardDateInAnotherYearShowsTheYear() {
    val nextYear = LocalDate.of(2027, 1, 5)
    val active =
        row("drill", status = RentalStatus.ACTIVE, location = "EPFL", returnDate = nextYear)
    setContent(lending(current = listOf(active)))

    inRow("drill", C.Tag.my_rentals_row_line)
        .assertTextEquals("Return by ${formatDay(nextYear, withYear = true)} · EPFL")
  }

  @Test
  fun problemCurrentCardShowsOnlyTheArea() {
    val setState = setMutableContent()

    listOf(RentalStatus.DISPUTED, RentalStatus.FROZEN, RentalStatus.LOST).forEach { status ->
      setState(lending(current = listOf(row("drill", status = status, location = "EPFL"))))
      inRow("drill", C.Tag.my_rentals_row_line).assertTextEquals("EPFL")
    }
  }

  @Test
  fun finishedCurrentCardsHaveNoLine() {
    val setState = setMutableContent()

    listOf(
            RentalStatus.RETURNED,
            RentalStatus.CLOSED,
            RentalStatus.DECLINED,
            RentalStatus.CANCELLED,
        )
        .forEach { status ->
          setState(lending(current = listOf(row("drill", status = status))))
          inRow("drill", C.Tag.my_rentals_row_line).assertDoesNotExist()
        }
  }

  @Test
  fun blankAreaLeavesOutTheAreaAndAnEmptyLineIsNotShown() {
    val active = row("drill", status = RentalStatus.ACTIVE, location = "", isPickupArea = false)
    val disputed = row("bike", status = RentalStatus.DISPUTED, location = "")
    setContent(lending(current = listOf(active, disputed)))

    inRow("drill", C.Tag.my_rentals_row_line)
        .assertTextEquals("Return by ${formatDay(LocalDate.of(2026, 10, 22))}")
    inRow("bike", C.Tag.my_rentals_row_line).assertDoesNotExist()
  }

  @Test
  fun tappingACurrentCardOpensTheRental() {
    val opened = mutableListOf<String>()
    setContent(
        lending(current = listOf(row("drill", status = RentalStatus.ACTIVE))),
        onRentalClick = { opened += it },
    )

    composeTestRule.onNodeWithTag(C.Tag.my_rentals_row_ + "drill").performClick()

    assertEquals(listOf("drill"), opened)
  }

  @Test
  fun borrowingTabIsUnchanged() {
    // The same request on both tabs: Borrowing must still show it on the old card.
    setContent(
        MyRentalsUiState(
            borrowing = listOf(row("tent")),
            lendingRequests = listOf(request("tent")),
            isLoading = false,
        )
    )

    inRow("tent", C.Tag.my_rentals_row_person)
        .assertTextEquals(context.getString(R.string.my_rentals_lender, "Loca"))
    composeTestRule.onNodeWithTag(C.Tag.my_rentals_request_ + "tent").assertDoesNotExist()
  }

  @Test
  fun requestCardShowsTitleBorrowerDatesBadgeAndReservedLine() {
    setContent(lending(requests = listOf(request("tent"))))

    inRequest("tent", C.Tag.my_rentals_row_title).assertTextEquals("Camping tent")
    inRequest("tent", C.Tag.my_rentals_row_person).assertTextEquals("Loca · $TENT_DATES")
    inRequest("tent", C.Tag.my_rentals_row_status)
        .assertTextEquals(context.getString(R.string.my_rentals_badge_new_request))
    inRequest("tent", C.Tag.my_rentals_row_reserved)
        .assertTextEquals("45 PP reserved · waiting for your answer")
    inRequest("tent", C.Tag.my_rentals_review_request_button).assertIsDisplayed()
  }

  @Test
  fun reviewRequestButtonCallsBackWithTheRentalId() {
    val reviewed = mutableListOf<String>()
    val opened = mutableListOf<String>()
    setContent(
        lending(requests = listOf(request("tent"))),
        onRentalClick = { opened += it },
        onReviewRequest = { reviewed += it },
    )

    inRequest("tent", C.Tag.my_rentals_review_request_button).performClick()

    assertEquals(listOf("tent"), reviewed)
    assertEquals(emptyList<String>(), opened)
  }

  @Test
  fun tappingARequestCardOpensTheRental() {
    val opened = mutableListOf<String>()
    setContent(lending(requests = listOf(request("tent"))), onRentalClick = { opened += it })

    composeTestRule.onNodeWithTag(C.Tag.my_rentals_request_ + "tent").performClick()

    assertEquals(listOf("tent"), opened)
  }

  @Test
  fun blankBorrowerShowsPlaceholderOnRequestCard() {
    setContent(lending(requests = listOf(request("tent", person = ""))))

    val unknown = context.getString(R.string.my_rentals_unknown_person)
    inRequest("tent", C.Tag.my_rentals_row_person).assertTextEquals("$unknown · $TENT_DATES")
  }

  @Test
  fun onlyRequestsShowsOnlyTheRequestsHeader() {
    setContent(lending(requests = listOf(request("tent"))))

    composeTestRule
        .onNodeWithTag(C.Tag.my_rentals_section_requests)
        .assertTextEquals(context.getString(R.string.my_rentals_section_requests))
    composeTestRule.onNodeWithTag(C.Tag.my_rentals_section_current).assertDoesNotExist()
  }

  @Test
  fun onlyCurrentShowsOnlyTheCurrentHeader() {
    setContent(lending(current = listOf(row("drill", status = RentalStatus.ACTIVE))))

    composeTestRule
        .onNodeWithTag(C.Tag.my_rentals_section_current)
        .assertTextEquals(context.getString(R.string.my_rentals_section_current))
    composeTestRule.onNodeWithTag(C.Tag.my_rentals_section_requests).assertDoesNotExist()
  }

  @Test
  fun bothSectionsShowRequestsAboveCurrent() {
    setContent(
        lending(
            requests = listOf(request("tent")),
            current = listOf(row("drill", status = RentalStatus.ACTIVE)),
        )
    )

    val top = { tag: String ->
      composeTestRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top
    }
    val requestsHeader = top(C.Tag.my_rentals_section_requests)
    val request = top(C.Tag.my_rentals_request_ + "tent")
    val currentHeader = top(C.Tag.my_rentals_section_current)
    val current = top(C.Tag.my_rentals_row_ + "drill")
    assertTrue(requestsHeader < request)
    assertTrue(request < currentHeader)
    assertTrue(currentHeader < current)
  }

  @Test
  fun missingNameShowsThePlaceholder() {
    setContent(MyRentalsUiState(borrowing = listOf(row("tent", person = "")), isLoading = false))

    val unknown = context.getString(R.string.my_rentals_unknown_person)
    inRow("tent", C.Tag.my_rentals_row_person)
        .assertTextEquals(context.getString(R.string.my_rentals_lender, unknown))
  }

  @Test
  fun blankLocationIsNotShown() {
    setContent(MyRentalsUiState(borrowing = listOf(row("tent", location = "")), isLoading = false))

    inRow("tent", C.Tag.my_rentals_row_location).assertDoesNotExist()
  }

  @Test
  fun everyStatusShowsItsLabel() {
    val setState = setMutableContent()

    RentalStatus.entries.forEach { status ->
      setState(
          MyRentalsUiState(borrowing = listOf(row("tent", status = status)), isLoading = false)
      )
      inRow("tent", C.Tag.my_rentals_row_status)
          .assertTextEquals(context.getString(status.labelRes))
    }
  }

  @Test
  fun clickingTabsAndRowsCallsBack() {
    val tabs = mutableListOf<RentalsTab>()
    val clicked = mutableListOf<String>()
    setContent(
        MyRentalsUiState(borrowing = listOf(row("tent")), isLoading = false),
        onTabSelected = { tabs += it },
        onRentalClick = { clicked += it },
    )

    composeTestRule.onNodeWithTag(C.Tag.my_rentals_tab_lending).performClick()
    composeTestRule.onNodeWithTag(C.Tag.my_rentals_row_ + "tent").performClick()

    assertEquals(listOf(RentalsTab.LENDING), tabs)
    assertEquals(listOf("tent"), clicked)
  }

  @Test
  fun screenWithViewModelSwitchesBetweenBorrowedAndLentRentals() {
    val me = AuthUser(uid = "me", email = "me@epfl.ch", name = "Me")
    val repository =
        FakeRentalRepository(
            rentals =
                listOf(
                    rental("tent", ownerId = "vida", borrowerId = "me"),
                    rental("drill", ownerId = "me", borrowerId = "loca"),
                )
        )
    val viewModel = MyRentalsViewModel(repository, SignedInAuthRepository(me))
    composeTestRule.setContent { MyRentalsScreen(viewModel = viewModel) }

    composeTestRule.onNodeWithTag(C.Tag.my_rentals_row_ + "tent").assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.my_rentals_tab_lending).performClick()
    composeTestRule.onNodeWithTag(C.Tag.my_rentals_request_ + "drill").assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.my_rentals_row_ + "tent").assertDoesNotExist()
  }

  private fun rental(id: String, ownerId: String, borrowerId: String) =
      Rental(
          id = id,
          listingId = "listing-$id",
          listingTitle = "Item $id",
          ownerId = ownerId,
          ownerName = "Owner",
          borrowerId = borrowerId,
          borrowerName = "Borrower",
          startDate = LocalDate.of(2026, 10, 20),
          endDate = LocalDate.of(2026, 10, 22),
          status = RentalStatus.REQUESTED,
          pricePerDay = 5,
          totalPrice = 15,
          pickupArea = "Ecublens",
          dropOffArea = "Renens",
          createdAt = Instant.parse("2026-10-13T10:00:00Z"),
      )

  /** An [AuthRepository] where [user] is signed in; the other calls are not used here. */
  private class SignedInAuthRepository(private val user: AuthUser) : AuthRepository {
    override suspend fun signUp(name: String, email: String, password: String) =
        AuthResult.Success(Unit)

    override suspend fun signIn(email: String, password: String): AuthResult<AuthUser> =
        AuthResult.Success(user)

    override suspend fun resendVerificationEmail(email: String, password: String) =
        AuthResult.Success(Unit)

    override fun getCurrentUser(): AuthUser = user

    override fun signOut() {}
  }

  private companion object {
    val TODAY: LocalDate = LocalDate.of(2026, 10, 14)
    val TENT_DATES = formatDayRange(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 17), TODAY)
  }
}
