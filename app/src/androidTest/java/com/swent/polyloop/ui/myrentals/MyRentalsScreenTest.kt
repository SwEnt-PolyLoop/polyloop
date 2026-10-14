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
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
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

  private fun setContent(
      state: MyRentalsUiState,
      onTabSelected: (RentalsTab) -> Unit = {},
      onRentalClick: (String) -> Unit = {},
      onRetry: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      MyRentalsContent(
          uiState = state,
          onTabSelected = onTabSelected,
          onRentalClick = onRentalClick,
          onRetry = onRetry,
          today = TODAY,
      )
    }
  }

  private fun inRow(id: String, tag: String) =
      composeTestRule.onNode(
          hasTestTag(tag) and hasAnyAncestor(hasTestTag(C.Tag.my_rentals_row_ + id)),
          useUnmergedTree = true,
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
  fun lentRowNamesTheBorrower() {
    setContent(
        MyRentalsUiState(
            selectedTab = RentalsTab.LENDING,
            lending = listOf(row("drill")),
            isLoading = false,
        )
    )

    inRow("drill", C.Tag.my_rentals_row_person)
        .assertTextEquals(context.getString(R.string.my_rentals_borrower, "Loca"))
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
    var state by mutableStateOf(MyRentalsUiState(isLoading = false))
    composeTestRule.setContent {
      MyRentalsContent(uiState = state, onTabSelected = {}, onRentalClick = {}, onRetry = {})
    }

    RentalStatus.entries.forEach { status ->
      state = MyRentalsUiState(borrowing = listOf(row("tent", status = status)), isLoading = false)
      composeTestRule.waitForIdle()
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
    composeTestRule.onNodeWithTag(C.Tag.my_rentals_row_ + "drill").assertIsDisplayed()
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
  }
}
