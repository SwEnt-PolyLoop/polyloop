// Made with Claude.

package com.swent.polyloop.ui.myrentals

import androidx.lifecycle.ViewModel
import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.auth.FakeAuthRepository
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.model.listing.ListingStatus
import com.swent.polyloop.model.rental.FakeRentalRepository
import com.swent.polyloop.model.rental.Rental
import com.swent.polyloop.model.rental.RentalRepository
import com.swent.polyloop.model.rental.RentalStatus
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MyRentalsViewModelTest {

  private val me = AuthUser(uid = "me", email = "me@epfl.ch", name = "Me")
  private val createdAt = Instant.parse("2026-10-13T10:00:00Z")

  private fun rental(
      id: String,
      ownerId: String,
      borrowerId: String,
      status: RentalStatus = RentalStatus.REQUESTED,
      createdAt: Instant = this.createdAt,
  ) =
      Rental(
          id = id,
          listingId = "listing-$id",
          listingTitle = "Item $id",
          ownerId = ownerId,
          ownerName = "Owner of $id",
          borrowerId = borrowerId,
          borrowerName = "Borrower of $id",
          startDate = LocalDate.of(2026, 10, 20),
          endDate = LocalDate.of(2026, 10, 22),
          status = status,
          pricePerDay = 5,
          totalPrice = 15,
          pickupArea = "Ecublens",
          dropOffArea = "Renens",
          createdAt = createdAt,
      )

  private val borrowed = rental("tent", ownerId = "vida", borrowerId = "me")
  private val lent = rental("drill", ownerId = "me", borrowerId = "loca")
  private val someoneElses = rental("guitar", ownerId = "vida", borrowerId = "loca")

  private lateinit var rentals: FakeRentalRepository
  private lateinit var auth: FakeAuthRepository

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    rentals = FakeRentalRepository(rentals = listOf(borrowed, lent, someoneElses))
    auth = FakeAuthRepository(signedInUser = me)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun viewModel(repository: RentalRepository = rentals) =
      MyRentalsViewModel(repository, auth)

  @Test
  fun initLoadsWhatIBorrowAndWhatILend() {
    val state = viewModel().uiState.value

    assertFalse(state.isLoading)
    assertNull(state.errorMsg)
    assertEquals(listOf("tent"), state.borrowing.map { it.rentalId })
    assertEquals(listOf("drill"), state.lending.map { it.rentalId })
  }

  @Test
  fun borrowedRowShowsTheLenderAndTheDates() {
    val row = viewModel().uiState.value.borrowing.single()

    assertEquals(
        RentalRow(
            rentalId = "tent",
            itemTitle = "Item tent",
            otherPersonName = "Owner of tent",
            startDate = LocalDate.of(2026, 10, 20),
            returnDate = LocalDate.of(2026, 10, 22),
            location = "Ecublens",
            status = RentalStatus.REQUESTED,
        ),
        row,
    )
  }

  @Test
  fun lentRowShowsTheBorrower() {
    assertEquals("Borrower of drill", viewModel().uiState.value.lending.single().otherPersonName)
  }

  @Test
  fun locationIsThePickupAreaBeforePickupAndTheDropOffAreaAfter() {
    val before =
        RentalStatus.entries.filter {
          it in
              setOf(
                  RentalStatus.REQUESTED,
                  RentalStatus.ACCEPTED,
                  RentalStatus.DECLINED,
                  RentalStatus.CANCELLED,
              )
        }
    RentalStatus.entries.forEach { status ->
      rentals = FakeRentalRepository(listOf(rental("tent", "vida", "me", status = status)))
      val expected = if (status in before) "Ecublens" else "Renens"
      assertEquals(
          "location for $status",
          expected,
          viewModel().uiState.value.borrowing.single().location,
      )
    }
  }

  @Test
  fun listsAreNewestFirst() {
    val older = rental("old", "vida", "me", createdAt = createdAt.minusSeconds(60))
    rentals = FakeRentalRepository(listOf(older, borrowed))

    assertEquals(listOf("tent", "old"), viewModel().uiState.value.borrowing.map { it.rentalId })
  }

  @Test
  fun newRequestAppearsWithoutReloading() = runTest {
    val vm = viewModel()
    val listing =
        Listing(
            id = "bike",
            ownerId = "me",
            title = "Bike",
            description = "City bike.",
            category = ListingCategory.SPORTS_OUTDOOR,
            photoUrls = List(3) { "https://example.com/$it.jpg" },
            pricePerDay = 4,
            itemValue = 200,
            availableFrom = LocalDate.of(2026, 10, 1),
            availableTo = LocalDate.of(2026, 12, 31),
            status = ListingStatus.PUBLISHED,
        )

    rentals.requestRental(
        listing,
        "Vida",
        AuthUser("loca", "loca@epfl.ch", "Loca"),
        LocalDate.of(2026, 11, 1),
        LocalDate.of(2026, 11, 2),
    )

    assertEquals(2, vm.uiState.value.lending.size)
    assertEquals("Loca", vm.uiState.value.lending.first().otherPersonName)
  }

  @Test
  fun noRentalsGivesEmptyListsWithoutError() {
    rentals = FakeRentalRepository()

    val state = viewModel().uiState.value

    assertFalse(state.isLoading)
    assertNull(state.errorMsg)
    assertTrue(state.borrowing.isEmpty())
    assertTrue(state.lending.isEmpty())
  }

  @Test
  fun staysLoadingUntilTheRentalsArrive() {
    val silent =
        object : RentalRepository by rentals {
          override fun observeRentalsAsBorrower(uid: String): Flow<List<Rental>> =
              MutableSharedFlow()
        }

    val state = viewModel(silent).uiState.value

    assertTrue(state.isLoading)
    assertNull(state.errorMsg)
  }

  @Test
  fun failureShowsTheErrorAndRetryLoadsAgain() {
    rentals.failure = IllegalStateException("No connection")
    val vm = viewModel()

    assertEquals("No connection", vm.uiState.value.errorMsg)
    assertFalse(vm.uiState.value.isLoading)

    rentals.failure = null
    vm.retry()

    assertNull(vm.uiState.value.errorMsg)
    assertEquals(listOf("tent"), vm.uiState.value.borrowing.map { it.rentalId })
  }

  @Test
  fun failureWithoutMessageGivesABlankError() {
    rentals.failure = IllegalStateException()

    assertEquals("", viewModel().uiState.value.errorMsg)
  }

  @Test
  fun nobodySignedInGivesABlankErrorAndNoRows() {
    auth.signedInUser = null

    val state = viewModel().uiState.value

    assertFalse(state.isLoading)
    assertEquals("", state.errorMsg)
    assertTrue(state.borrowing.isEmpty())
    assertTrue(state.lending.isEmpty())
  }

  @Test
  fun incomingRequestsComeFirstInLending() {
    val newerActive = rental("bike", "me", "loca", RentalStatus.ACTIVE, createdAt.plusSeconds(60))
    rentals = FakeRentalRepository(listOf(lent, newerActive))

    assertEquals(listOf("drill", "bike"), viewModel().uiState.value.lending.map { it.rentalId })
  }

  @Test
  fun signingOutThenRetryingClearsTheRows() {
    val vm = viewModel()
    assertEquals(1, vm.uiState.value.borrowing.size)

    auth.signedInUser = null
    vm.retry()

    assertTrue(vm.uiState.value.borrowing.isEmpty())
    assertTrue(vm.uiState.value.lending.isEmpty())
    assertEquals("", vm.uiState.value.errorMsg)
  }

  @Test
  fun emptyOwnerNameStaysEmptyForTheScreenPlaceholder() {
    rentals = FakeRentalRepository(listOf(borrowed.copy(ownerName = "")))

    assertEquals("", viewModel().uiState.value.borrowing.single().otherPersonName)
  }

  @Test
  fun selectTabSwitchesTheShownRows() {
    val vm = viewModel()
    assertEquals(RentalsTab.BORROWING, vm.uiState.value.selectedTab)
    assertEquals(vm.uiState.value.borrowing, vm.uiState.value.selectedRows)

    vm.selectTab(RentalsTab.LENDING)

    assertEquals(RentalsTab.LENDING, vm.uiState.value.selectedTab)
    assertEquals(vm.uiState.value.lending, vm.uiState.value.selectedRows)
  }

  @Test
  fun factoryCreatesMyRentalsViewModel() {
    val vm = MyRentalsViewModel.Factory(rentals, auth).create(MyRentalsViewModel::class.java)

    assertEquals(listOf("tent"), vm.uiState.value.borrowing.map { it.rentalId })
  }

  @Test(expected = IllegalArgumentException::class)
  fun factoryRejectsOtherViewModels() {
    MyRentalsViewModel.Factory(rentals, auth).create(OtherViewModel::class.java)
  }

  private class OtherViewModel : ViewModel()
}
