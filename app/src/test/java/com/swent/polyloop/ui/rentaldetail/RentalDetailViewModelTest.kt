// Made with Claude.

package com.swent.polyloop.ui.rentaldetail

import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.auth.FakeAuthRepository
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.model.listing.ListingStatus
import com.swent.polyloop.model.rental.FakeRentalRepository
import com.swent.polyloop.model.rental.Rental
import com.swent.polyloop.model.rental.RentalStatus
import com.swent.polyloop.ui.listingdetail.FakeListingRepository
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RentalDetailViewModelTest {

  private val lender = AuthUser(uid = "lender", email = "vida@epfl.ch", name = "Vida")
  private val borrower = AuthUser(uid = "borrower", email = "loca@epfl.ch", name = "Loca")

  private val listing =
      Listing(
          id = "listing-1",
          ownerId = "lender",
          title = "Camping tent",
          description = "Light 2-person tent.",
          category = ListingCategory.SPORTS_OUTDOOR,
          photoUrls = listOf("https://example.com/0.jpg"),
          pricePerDay = 5,
          itemValue = 120,
          availableFrom = LocalDate.of(2026, 10, 1),
          availableTo = LocalDate.of(2026, 12, 31),
          status = ListingStatus.PUBLISHED,
          pickupArea = "Ecublens",
          dropOffArea = "Renens",
      )

  private fun rental(id: String = "rental-1", status: RentalStatus = RentalStatus.REQUESTED) =
      Rental(
          id = id,
          listingId = listing.id,
          listingTitle = listing.title,
          ownerId = lender.uid,
          ownerName = lender.name,
          borrowerId = borrower.uid,
          borrowerName = borrower.name,
          startDate = LocalDate.of(2026, 10, 20),
          endDate = LocalDate.of(2026, 10, 22),
          status = status,
          pricePerDay = 5,
          totalPrice = 15,
          pickupArea = "Ecublens",
          dropOffArea = "Renens",
          createdAt = Instant.parse("2026-10-13T10:00:00Z"),
      )

  private lateinit var rentals: FakeRentalRepository
  private lateinit var auth: FakeAuthRepository

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    rentals = FakeRentalRepository(rentals = listOf(rental()))
    auth = FakeAuthRepository(signedInUser = lender)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun viewModel(rentalId: String = "rental-1") =
      RentalDetailViewModel(
          rentalId = rentalId,
          rentalRepository = rentals,
          listingRepository = FakeListingRepository(listings = listOf(listing)),
          authRepository = auth,
      )

  private fun RentalDetailViewModel.fillValidForm() {
    onPickupAddressChange("EPFL, Lausanne")
    onReturnAddressChange("Renens station")
    onDepositChange("50")
  }

  @Test
  fun lenderSeesTheRequestWithTheSuggestedDeposit() {
    val state = viewModel().uiState.value

    assertFalse(state.isLoading)
    assertNull(state.errorMsg)
    assertTrue(state.isLender)
    assertTrue(state.isRequestToAnswer)
    assertEquals("120", state.deposit)
  }

  @Test
  fun borrowerCannotAnswerTheRequest() {
    auth.signedInUser = borrower
    val state = viewModel().uiState.value

    assertFalse(state.isLender)
    assertFalse(state.isRequestToAnswer)
    assertFalse(state.canAccept)
    assertFalse(state.canDecline)
  }

  @Test
  fun acceptNeedsBothAddressesAndAWholeDepositAboveZero() {
    val vm = viewModel()
    assertFalse(vm.uiState.value.canAccept)

    vm.onPickupAddressChange("EPFL")
    vm.onReturnAddressChange("Renens")
    assertTrue(vm.uiState.value.canAccept)

    vm.onDepositChange("0")
    assertFalse(vm.uiState.value.canAccept)
    vm.onDepositChange("12.5")
    assertFalse(vm.uiState.value.canAccept)
    vm.onDepositChange("")
    assertFalse(vm.uiState.value.canAccept)
    vm.onDepositChange(" 30 ")
    assertTrue(vm.uiState.value.canAccept)
  }

  @Test
  fun acceptBlankAddressIsRefused() {
    val vm = viewModel()
    vm.fillValidForm()
    vm.onReturnAddressChange("   ")
    vm.accept()

    assertEquals(RentalStatus.REQUESTED, rentals.rentals.single().status)
    assertFalse(vm.uiState.value.isAnswered)
  }

  @Test
  fun acceptSavesTheTrimmedAddressesAndLeaves() {
    val vm = viewModel()
    vm.onPickupAddressChange("  EPFL, Lausanne ")
    vm.onReturnAddressChange("Renens station")
    vm.onDepositChange("50")
    vm.accept()

    val saved = rentals.rentals.single()
    assertEquals(RentalStatus.ACCEPTED, saved.status)
    assertEquals("EPFL, Lausanne", saved.pickupAddress)
    assertEquals("Renens station", saved.returnAddress)
    assertTrue(vm.uiState.value.isAnswered)
    assertEquals(RentalStatus.ACCEPTED, vm.uiState.value.rental?.status)
  }

  @Test
  fun declineSavesAndLeaves() {
    val vm = viewModel()
    vm.decline()

    assertEquals(RentalStatus.DECLINED, rentals.rentals.single().status)
    assertTrue(vm.uiState.value.isAnswered)
  }

  @Test
  fun failedAnswerKeepsTheFormAndShowsTheReason() {
    val vm = viewModel()
    vm.fillValidForm()
    rentals.failure = IOException("offline")
    vm.accept()

    val state = vm.uiState.value
    assertFalse(state.isAnswered)
    assertFalse(state.isAnswering)
    assertEquals("offline", state.answerErrorMsg)
    assertEquals("EPFL, Lausanne", state.pickupAddress)
  }

  @Test
  fun rentalThatIsNoLongerRequestedCannotBeAnswered() {
    rentals = FakeRentalRepository(rentals = listOf(rental(status = RentalStatus.DECLINED)))
    val vm = viewModel()
    vm.fillValidForm()

    assertFalse(vm.uiState.value.isRequestToAnswer)
    assertFalse(vm.uiState.value.canAccept)
    assertFalse(vm.uiState.value.canDecline)
  }

  @Test
  fun unknownRentalShowsAnErrorWithoutMessage() {
    val state = viewModel("missing").uiState.value

    assertNull(state.rental)
    assertEquals("", state.errorMsg)
  }

  @Test
  fun loadFailureShowsTheCause() {
    rentals.failure = IOException("no network")
    val state = viewModel().uiState.value

    assertNull(state.rental)
    assertEquals("no network", state.errorMsg)
  }

  @Test
  fun nobodySignedInShowsAnErrorWithoutMessage() {
    auth.signedInUser = null
    val state = viewModel().uiState.value

    assertNull(state.rental)
    assertEquals("", state.errorMsg)
  }
}
