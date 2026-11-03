// Made with Claude.

package com.swent.polyloop.ui.rentaldetail

import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.auth.FakeAuthRepository
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.model.listing.ListingStatus
import com.swent.polyloop.model.profile.FakeProfileRepository
import com.swent.polyloop.model.rental.FakeRentalRepository
import com.swent.polyloop.model.rental.Rental
import com.swent.polyloop.model.rental.RentalStatus
import com.swent.polyloop.ui.listingdetail.FakeListingRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
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

  private val rental =
      Rental(
          id = "rental1",
          listingId = "listing1",
          listingTitle = "Camping tent, 2 people",
          ownerId = "owner",
          ownerName = "Mara",
          borrowerId = "borrower",
          borrowerName = "Loca",
          startDate = LocalDate.of(2026, 10, 6),
          endDate = LocalDate.of(2026, 10, 8),
          status = RentalStatus.REQUESTED,
          pricePerDay = 15,
          totalPrice = 45,
          pickupArea = "Ecublens",
          dropOffArea = "Renens",
          createdAt = Instant.parse("2026-10-01T09:30:00Z"),
      )

  private val listing =
      Listing(
          id = "listing1",
          ownerId = "owner",
          title = "Camping tent, 2 people",
          description = "Light 2-person tent, used three times.",
          category = ListingCategory.SPORTS_OUTDOOR,
          photoUrls = List(3) { "https://example.com/photo$it.jpg" },
          pricePerDay = 15,
          itemValue = 180,
          availableFrom = LocalDate.of(2026, 10, 6),
          availableTo = LocalDate.of(2026, 10, 20),
          status = ListingStatus.PUBLISHED,
      )

  private val owner = AuthUser(uid = "owner", email = "mara@epfl.ch", name = "Mara")
  private val borrower = AuthUser(uid = "borrower", email = "loca@epfl.ch", name = "Loca")

  private lateinit var rentalRepository: FakeRentalRepository
  private lateinit var listingRepository: FakeListingRepository
  private lateinit var profileRepository: FakeProfileRepository
  private lateinit var authRepository: FakeAuthRepository

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    rentalRepository = FakeRentalRepository(rentals = listOf(rental))
    listingRepository = FakeListingRepository(listings = listOf(listing))
    profileRepository = FakeProfileRepository()
    authRepository = FakeAuthRepository(signedInUser = borrower)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun viewModel(id: String = rental.id) =
      RentalDetailViewModel(
          id,
          rentalRepository,
          listingRepository,
          profileRepository,
          authRepository,
      )

  @Test
  fun showsLoadingThenHoldsTheRental() {
    val gate = CompletableDeferred<Unit>()
    rentalRepository.gate = gate
    val vm = viewModel()
    assertTrue(vm.uiState.value.isLoading)

    gate.complete(Unit)

    val state = vm.uiState.value
    assertFalse(state.isLoading)
    assertEquals(rental.listingTitle, state.itemTitle)
    assertFalse(state.notFound)
    assertNull(state.errorMsg)
  }

  @Test
  fun mapsEachFieldFromTheRental() {
    val state = viewModel().uiState.value

    assertTrue(state.isSupported)
    assertEquals("Camping tent, 2 people", state.itemTitle)
    assertEquals(RentalStatus.REQUESTED, state.status)
    assertEquals(Instant.parse("2026-10-01T09:30:00Z"), state.sentAt)
    assertEquals(LocalDate.of(2026, 10, 6), state.startDate)
    assertEquals(LocalDate.of(2026, 10, 8), state.endDate)
    assertEquals(45, state.rentalPrice)
    assertEquals("Ecublens", state.pickupArea)
  }

  @Test
  fun isLenderOnlyForTheOwner() {
    assertFalse(viewModel().uiState.value.isLender)

    authRepository.signedInUser = owner
    assertTrue(viewModel().uiState.value.isLender)
  }

  @Test
  fun missingRentalIsNotFound() {
    val state = viewModel(id = "unknown").uiState.value

    assertTrue(state.notFound)
    assertFalse(state.isLoading)
    assertNull(state.errorMsg)
  }

  @Test
  fun repositoryFailureSetsErrorMsg() {
    rentalRepository.failure = IllegalStateException("Network down")
    val state = viewModel().uiState.value
    assertEquals("Network down", state.errorMsg)
    assertFalse(state.isLoading)

    // Blank, not null: the screen shows its generic error text.
    rentalRepository.failure = RuntimeException()
    assertEquals("", viewModel().uiState.value.errorMsg)
  }

  @Test
  fun retryClearsErrorAndLoadsAgain() {
    rentalRepository.failure = IllegalStateException("Network down")
    val vm = viewModel()

    rentalRepository.failure = null
    vm.retry()

    val state = vm.uiState.value
    assertNull(state.errorMsg)
    assertFalse(state.isLoading)
    assertEquals(rental.listingTitle, state.itemTitle)
  }

  @Test
  fun nonRequestedRentalIsUnsupportedAndKeepsOnlyItsTitle() {
    rentalRepository = FakeRentalRepository(listOf(rental.copy(status = RentalStatus.ACCEPTED)))

    val state = viewModel().uiState.value

    assertEquals(
        RentalDetailUiState(isSupported = false, itemTitle = rental.listingTitle),
        state,
    )
  }

  @Test
  fun suggestedDepositIsTheListingsItemValue() {
    assertEquals(180, viewModel().uiState.value.suggestedDeposit)
  }

  @Test
  fun missingListingLeavesTheSuggestedDepositOut() {
    listingRepository.listings = emptyList()

    val state = viewModel().uiState.value

    assertNull(state.suggestedDeposit)
    assertNull(state.errorMsg)
    assertFalse(state.isLoading)
  }

  @Test
  fun failedListingReadLeavesTheSuggestedDepositOutWithoutError() {
    listingRepository.failure = IllegalStateException("Network down")

    val state = viewModel().uiState.value

    assertNull(state.suggestedDeposit)
    assertNull(state.errorMsg)
    assertFalse(state.isLoading)
    assertEquals(rental.listingTitle, state.itemTitle)
  }

  @Test
  fun staysLoadingUntilTheListingIsRead() {
    val gate = CompletableDeferred<Unit>()
    listingRepository.gate = gate
    val vm = viewModel()
    assertTrue(vm.uiState.value.isLoading)

    gate.complete(Unit)

    assertFalse(vm.uiState.value.isLoading)
    assertEquals(180, vm.uiState.value.suggestedDeposit)
  }

  @Test
  fun unsupportedRentalDoesNotReadTheListing() {
    rentalRepository = FakeRentalRepository(listOf(rental.copy(status = RentalStatus.ACCEPTED)))

    viewModel()

    assertEquals(0, listingRepository.getListingCalls)
  }
}
