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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The lender's answer part of [RentalDetailViewModel]: the form checks and accept, decline and
 * overlap.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RentalDetailAnswerTest {

  private val lender = AuthUser(uid = "owner", email = "mara@epfl.ch", name = "Mara")

  private val listing =
      Listing(
          id = "listing1",
          ownerId = "owner",
          title = "Camping tent",
          description = "Light 2-person tent.",
          category = ListingCategory.SPORTS_OUTDOOR,
          photoUrls = List(3) { "https://example.com/$it.jpg" },
          pricePerDay = 15,
          itemValue = 180,
          availableFrom = LocalDate.of(2026, 10, 1),
          availableTo = LocalDate.of(2026, 12, 31),
          status = ListingStatus.PUBLISHED,
      )

  private lateinit var rentalRepository: FakeRentalRepository
  private lateinit var authRepository: FakeAuthRepository

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    authRepository = FakeAuthRepository(signedInUser = lender)
    rentalRepository =
        FakeRentalRepository(rentals = listOf(request("r1", "2026-10-10", "2026-10-12")))
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  /** A request by a borrower on [listing] from [start] to [end] (YYYY-MM-DD). */
  private fun request(
      id: String,
      start: String,
      end: String,
      listingId: String = listing.id,
  ) =
      Rental(
          id = id,
          listingId = listingId,
          listingTitle = listing.title,
          ownerId = "owner",
          ownerName = "Mara",
          borrowerId = "borrower",
          borrowerName = "Loca",
          startDate = LocalDate.parse(start),
          endDate = LocalDate.parse(end),
          status = RentalStatus.REQUESTED,
          pricePerDay = 15,
          totalPrice = 45,
          pickupArea = "Ecublens",
          dropOffArea = "Renens",
          createdAt = Instant.EPOCH,
      )

  private fun viewModel(id: String = "r1") =
      RentalDetailViewModel(
          rentalId = id,
          rentalRepository = rentalRepository,
          listingRepository = FakeListingRepository(listings = listOf(listing)),
          profileRepository = FakeProfileRepository(profiles = emptyList()),
          authRepository = authRepository,
      )

  @Test
  fun lenderSeesTheItemValueAsTheSuggestedDeposit() {
    assertEquals("180", viewModel().uiState.value.depositInput)
  }

  @Test
  fun acceptNeedsAWholeDepositAboveZeroAndBothAddresses() {
    val vm = viewModel()
    vm.updatePickupAddress("Route Cantonale 1")
    vm.updateReturnAddress("Rue de Lausanne 10")
    assertTrue(vm.uiState.value.canAccept)

    vm.updateDeposit("0")
    assertFalse(vm.uiState.value.canAccept)
    vm.updateDeposit("12.5")
    assertFalse(vm.uiState.value.canAccept)
    vm.updateDeposit("abc")
    assertFalse(vm.uiState.value.canAccept)

    vm.updateDeposit("50")
    vm.updateReturnAddress("   ")
    assertFalse(vm.uiState.value.canAccept)
  }

  @Test
  fun acceptStoresTheAnswerAndMarksTheRequestAnswered() {
    val vm = viewModel()
    vm.updateDeposit("  60 ")
    vm.updatePickupAddress("  Route Cantonale 1 ")
    vm.updateReturnAddress("Rue de Lausanne 10")

    vm.accept()

    val stored = rentalRepository.rentals.single()
    assertEquals(RentalStatus.ACCEPTED, stored.status)
    assertEquals(60, stored.deposit)
    assertEquals("Route Cantonale 1", stored.pickupAddress)
    assertEquals("Rue de Lausanne 10", stored.returnAddress)
    assertTrue(vm.uiState.value.answered)
    assertFalse(vm.uiState.value.isSubmitting)
  }

  @Test
  fun aFailedAcceptKeepsTheRequestAndShowsTheError() {
    val vm = viewModel()
    vm.updatePickupAddress("Route Cantonale 1")
    vm.updateReturnAddress("Rue de Lausanne 10")
    rentalRepository.failure = Exception("These dates are no longer free.")

    vm.accept()

    assertEquals("These dates are no longer free.", vm.uiState.value.actionError)
    assertFalse(vm.uiState.value.answered)
    assertEquals(RentalStatus.REQUESTED, rentalRepository.rentals.single().status)
  }

  @Test
  fun declineMarksTheRequestDeclinedAndAnswered() {
    val vm = viewModel()

    vm.decline()

    assertEquals(RentalStatus.DECLINED, rentalRepository.rentals.single().status)
    assertTrue(vm.uiState.value.answered)
  }

  @Test
  fun overlapCountIsTheOtherRequestsAcceptingWouldDecline() {
    // The request is 10 to 12 October, so 13 October is its buffer day.
    rentalRepository =
        FakeRentalRepository(
            rentals =
                listOf(
                    request("r1", "2026-10-10", "2026-10-12"),
                    request("touching", "2026-10-13", "2026-10-14"),
                    request("free-day", "2026-10-14", "2026-10-15"),
                    request("elsewhere", "2026-10-11", "2026-10-11", listingId = "other"),
                )
        )

    assertEquals(1, viewModel().uiState.value.overlapCount)
  }
}
