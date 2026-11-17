// Made with Claude.

package com.swent.polyloop.ui.rentaldetail

import androidx.lifecycle.ViewModel
import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.auth.FakeAuthRepository
import com.swent.polyloop.model.connectivity.FakeConnectivityRepository
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.model.listing.ListingStatus
import com.swent.polyloop.model.location.MapPoint
import com.swent.polyloop.model.profile.FakeProfileRepository
import com.swent.polyloop.model.profile.UserProfile
import com.swent.polyloop.model.rental.FakeRentalRepository
import com.swent.polyloop.model.rental.InMemoryServerClockStore
import com.swent.polyloop.model.rental.Rental
import com.swent.polyloop.model.rental.RentalRepository
import com.swent.polyloop.model.rental.RentalStatus
import com.swent.polyloop.model.rental.ServerClock
import com.swent.polyloop.ui.listingdetail.FakeListingRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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

  private val ownerProfile =
      UserProfile(
          uid = "owner",
          name = "Mara Rossi",
          email = "mara@epfl.ch",
          averageRating = 4.5,
          reviewCount = 12,
      )
  private val borrowerProfile =
      UserProfile(
          uid = "borrower",
          name = "Loca Meier",
          email = "loca@epfl.ch",
          averageRating = 3.0,
          reviewCount = 2,
      )

  private lateinit var rentalRepository: FakeRentalRepository
  private lateinit var listingRepository: FakeListingRepository
  private lateinit var profileRepository: FakeProfileRepository
  private lateinit var authRepository: FakeAuthRepository
  private lateinit var connectivityRepository: FakeConnectivityRepository

  // Also runs the server clock's reads, so they finish before each assertion.
  private val testDispatcher = UnconfinedTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    rentalRepository = FakeRentalRepository(rentals = listOf(rental))
    listingRepository = FakeListingRepository(listings = listOf(listing))
    profileRepository = FakeProfileRepository(profiles = listOf(ownerProfile, borrowerProfile))
    authRepository = FakeAuthRepository(signedInUser = borrower)
    connectivityRepository = FakeConnectivityRepository()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun serverClock(repository: RentalRepository = rentalRepository) =
      ServerClock(
          repository,
          InMemoryServerClockStore(),
          elapsedRealtime = { 0L },
          bootCount = { 0 },
          ioDispatcher = testDispatcher,
      )

  private fun viewModel(id: String = rental.id) =
      RentalDetailViewModel(
          id,
          rentalRepository,
          listingRepository,
          profileRepository,
          authRepository,
          connectivityRepository,
          serverClock(),
      )

  private fun factory() =
      RentalDetailViewModel.Factory(
          rental.id,
          rentalRepository,
          listingRepository,
          profileRepository,
          authRepository,
          connectivityRepository,
          serverClock(),
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
  fun statusNotBuiltYetIsUnsupportedAndKeepsOnlyItsTitle() {
    rentalRepository = FakeRentalRepository(listOf(rental.copy(status = RentalStatus.RETURNED)))

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
  fun pickupPointIsTheListings() {
    val point = MapPoint(latitude = 46.52, longitude = 6.57)
    listingRepository.listings = listOf(listing.copy(pickupPoint = point))

    assertEquals(point, viewModel().uiState.value.pickupPoint)
  }

  @Test
  fun listingNotGeocodedYetLeavesThePickupPointOut() {
    assertNull(viewModel().uiState.value.pickupPoint)
  }

  @Test
  fun missingListingLeavesThePickupPointOut() {
    listingRepository.listings = emptyList()

    assertNull(viewModel().uiState.value.pickupPoint)
  }

  @Test
  fun failedListingReadLeavesThePickupPointOut() {
    listingRepository.failure = IllegalStateException("Network down")

    assertNull(viewModel().uiState.value.pickupPoint)
  }

  @Test
  fun blankRentalAreaFallsBackToTheListings() {
    rentalRepository = FakeRentalRepository(listOf(rental.copy(pickupArea = "")))
    listingRepository.listings = listOf(listing.copy(pickupArea = "Ecublens"))

    assertEquals("Ecublens", viewModel().uiState.value.pickupArea)
  }

  @Test
  fun rentalAreaIsKeptWhenTheListingsIsDifferent() {
    listingRepository.listings = listOf(listing.copy(pickupArea = "Renens"))

    assertEquals("Ecublens", viewModel().uiState.value.pickupArea)
  }

  @Test
  fun blankRentalAreaStaysBlankWithoutTheListing() {
    rentalRepository = FakeRentalRepository(listOf(rental.copy(pickupArea = "")))
    listingRepository.listings = emptyList()

    assertEquals("", viewModel().uiState.value.pickupArea)
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
    rentalRepository = FakeRentalRepository(listOf(rental.copy(status = RentalStatus.RETURNED)))

    viewModel()

    assertEquals(0, listingRepository.getListingCalls)
  }

  @Test
  fun borrowerSeesTheOwnerWithTheirRating() {
    assertEquals(
        OtherPerson(uid = "owner", name = "Mara Rossi", averageRating = 4.5, reviewCount = 12),
        viewModel().uiState.value.otherPerson,
    )
  }

  @Test
  fun lenderSeesTheBorrowerWithTheirRating() {
    authRepository.signedInUser = owner

    assertEquals(
        OtherPerson(uid = "borrower", name = "Loca Meier", averageRating = 3.0, reviewCount = 2),
        viewModel().uiState.value.otherPerson,
    )
  }

  @Test
  fun profileWithNoReviewsKeepsZeroRatingAndCount() {
    profileRepository =
        FakeProfileRepository(listOf(ownerProfile.copy(averageRating = 0.0, reviewCount = 0)))

    val person = viewModel().uiState.value.otherPerson

    // 0 means no reviews yet; only an unreadable profile gives null.
    assertEquals(0.0, person?.averageRating)
    assertEquals(0, person?.reviewCount)
  }

  @Test
  fun missingProfileFallsBackToTheRentalsNameWithoutRating() {
    profileRepository = FakeProfileRepository()

    val state = viewModel().uiState.value

    assertEquals(
        OtherPerson(uid = "owner", name = "Mara", averageRating = null, reviewCount = null),
        state.otherPerson,
    )
    assertNull(state.errorMsg)
  }

  @Test
  fun failedProfileReadFallsBackToTheRentalsNameWithoutError() {
    profileRepository.failure = IllegalStateException("Network down")

    val state = viewModel().uiState.value

    assertEquals(
        OtherPerson(uid = "owner", name = "Mara", averageRating = null, reviewCount = null),
        state.otherPerson,
    )
    assertNull(state.errorMsg)
    assertFalse(state.isLoading)
  }

  @Test
  fun blankProfileNameFallsBackToTheRentalsCopy() {
    profileRepository = FakeProfileRepository(listOf(ownerProfile.copy(name = " ")))

    val person = viewModel().uiState.value.otherPerson

    assertEquals("Mara", person?.name)
    assertEquals(4.5, person?.averageRating)
  }

  @Test
  fun staysLoadingUntilTheProfileIsRead() {
    val gate = CompletableDeferred<Unit>()
    profileRepository.gate = gate
    val vm = viewModel()
    assertTrue(vm.uiState.value.isLoading)

    gate.complete(Unit)

    assertFalse(vm.uiState.value.isLoading)
    assertEquals("Mara Rossi", vm.uiState.value.otherPerson?.name)
  }

  @Test
  fun factoryCreatesViewModelForTheRental() {
    val vm = factory().create(RentalDetailViewModel::class.java)

    assertEquals(rental.listingTitle, vm.uiState.value.itemTitle)
  }

  @Test
  fun isOfflineAtStartWhenThePhoneIsOffline() {
    connectivityRepository.setOnline(false)

    assertTrue(viewModel().uiState.value.isOffline)
  }

  @Test
  fun isOfflineFollowsTheConnectionAndKeepsTheRental() {
    val vm = viewModel()

    connectivityRepository.setOnline(false)
    assertTrue(vm.uiState.value.isOffline)
    assertEquals(rental.listingTitle, vm.uiState.value.itemTitle)

    connectivityRepository.setOnline(true)
    assertFalse(vm.uiState.value.isOffline)
    assertEquals(rental.listingTitle, vm.uiState.value.itemTitle)
  }

  @Test
  fun retryKeepsTheOfflineState() {
    val vm = viewModel()
    connectivityRepository.setOnline(false)

    vm.retry()

    assertTrue(vm.uiState.value.isOffline)
    assertEquals(rental.listingTitle, vm.uiState.value.itemTitle)
  }

  @Test
  fun retryOfAnUnsupportedRentalKeepsTheOfflineState() {
    rentalRepository = FakeRentalRepository(listOf(rental.copy(status = RentalStatus.RETURNED)))
    val vm = viewModel()
    connectivityRepository.setOnline(false)

    vm.retry()

    assertTrue(vm.uiState.value.isOffline)
    assertFalse(vm.uiState.value.isSupported)
  }

  @Test
  fun goingOfflineDuringALoadKeepsTheOfflineState() {
    val gate = CompletableDeferred<Unit>()
    listingRepository.gate = gate
    val vm = viewModel()

    connectivityRepository.setOnline(false)
    gate.complete(Unit)

    assertTrue(vm.uiState.value.isOffline)
    assertEquals(rental.listingTitle, vm.uiState.value.itemTitle)
  }

  @Test(expected = IllegalArgumentException::class)
  fun factoryRejectsOtherViewModels() {
    factory().create(OtherViewModel::class.java)
  }

  @Test
  fun retryCancelsAnOlderLoad() {
    val firstCall = CompletableDeferred<Rental>()
    val secondCall = CompletableDeferred<Rental>()
    val repository = OneDeferredPerCallRentalRepository(listOf(firstCall, secondCall))
    val vm =
        RentalDetailViewModel(
            rental.id,
            repository,
            listingRepository,
            profileRepository,
            authRepository,
            connectivityRepository,
            serverClock(repository),
        )
    vm.retry()

    secondCall.complete(rental.copy(listingTitle = "New"))
    // The first load answers last; had it not been cancelled, it would overwrite the newer one.
    firstCall.complete(rental.copy(listingTitle = "Old"))

    assertEquals("New", vm.uiState.value.itemTitle)
  }

  private class OtherViewModel : ViewModel()

  /** A [RentalRepository] whose n-th getRental call answers with the n-th of [answers]. */
  private class OneDeferredPerCallRentalRepository(
      private val answers: List<CompletableDeferred<Rental>>
  ) : RentalRepository by FakeRentalRepository() {
    private var calls = 0

    override suspend fun getRental(id: String): Result<Rental?> =
        Result.success(answers[calls++].await())

    override suspend fun requestRental(
        listing: Listing,
        ownerName: String,
        borrower: AuthUser,
        startDate: LocalDate,
        endDate: LocalDate,
    ): Result<Rental> = error("Not used")

    override fun observeRentalsAsBorrower(uid: String): Flow<List<Rental>> = error("Not used")

    override fun observeRentalsAsLender(uid: String): Flow<List<Rental>> = error("Not used")
  }

  // Opens the screen offline with a listing read that fails, then leaves the phone offline.
  private fun viewModelWithFailedListingRead(
      rentalToOpen: Rental = rental,
      listingToRead: Listing = listing,
  ): RentalDetailViewModel {
    rentalRepository = FakeRentalRepository(listOf(rentalToOpen))
    listingRepository.listings = listOf(listingToRead)
    listingRepository.failure = IllegalStateException("offline")
    connectivityRepository.setOnline(false)
    return viewModel(rentalToOpen.id)
  }

  private fun reconnect() {
    connectivityRepository.setOnline(false)
    connectivityRepository.setOnline(true)
  }

  @Test
  fun failedListingReadIsRepeatedWhenBackOnline() {
    val point = MapPoint(latitude = 46.52, longitude = 6.57)
    val vm = viewModelWithFailedListingRead(listingToRead = listing.copy(pickupPoint = point))
    assertNull(vm.uiState.value.pickupPoint)
    assertNull(vm.uiState.value.suggestedDeposit)

    listingRepository.failure = null
    connectivityRepository.setOnline(true)

    assertEquals(point, vm.uiState.value.pickupPoint)
    assertEquals(180, vm.uiState.value.suggestedDeposit)
  }

  @Test
  fun blankPickupAreaIsFilledFromTheListingOnReread() {
    val vm =
        viewModelWithFailedListingRead(
            rentalToOpen = rental.copy(pickupArea = ""),
            listingToRead = listing.copy(pickupArea = "Renens"),
        )
    assertEquals("", vm.uiState.value.pickupArea)

    listingRepository.failure = null
    connectivityRepository.setOnline(true)

    assertEquals("Renens", vm.uiState.value.pickupArea)
  }

  @Test
  fun nonBlankPickupAreaIsKeptOnReread() {
    val vm = viewModelWithFailedListingRead(listingToRead = listing.copy(pickupArea = "Renens"))

    listingRepository.failure = null
    connectivityRepository.setOnline(true)

    assertEquals("Ecublens", vm.uiState.value.pickupArea)
  }

  @Test
  fun rereadThatFailsAgainIsRetriedAtNextReconnection() {
    val vm = viewModelWithFailedListingRead()

    connectivityRepository.setOnline(true)
    assertEquals(2, listingRepository.getListingCalls)
    assertNull(vm.uiState.value.suggestedDeposit)

    listingRepository.failure = null
    reconnect()

    assertEquals(3, listingRepository.getListingCalls)
    assertEquals(180, vm.uiState.value.suggestedDeposit)
  }

  @Test
  fun successfulReadIsNotRepeatedWhenBackOnline() {
    val vm = viewModel()

    reconnect()

    assertEquals(1, listingRepository.getListingCalls)
    assertEquals(180, vm.uiState.value.suggestedDeposit)
  }

  @Test
  fun missingListingIsNotReread() {
    listingRepository.listings = emptyList()
    viewModel()

    reconnect()

    assertEquals(1, listingRepository.getListingCalls)
  }

  @Test
  fun laterReconnectionsDoNotReadAgainAfterASuccessfulReread() {
    viewModelWithFailedListingRead()
    listingRepository.failure = null
    connectivityRepository.setOnline(true)
    assertEquals(2, listingRepository.getListingCalls)

    reconnect()
    reconnect()

    assertEquals(2, listingRepository.getListingCalls)
  }

  @Test
  fun acceptedRentalDoesNotRereadTheListing() {
    viewModelWithFailedListingRead(rentalToOpen = rental.copy(status = RentalStatus.ACCEPTED))
    listingRepository.failure = null

    connectivityRepository.setOnline(true)

    assertEquals(1, listingRepository.getListingCalls)
  }

  @Test
  fun retryResetsTheFailureMarkerAndDoesNotLetARereadRunTwice() {
    val vm = viewModelWithFailedListingRead()
    listingRepository.failure = null
    val gate = CompletableDeferred<Unit>()
    listingRepository.gate = gate
    connectivityRepository.setOnline(true) // the re-read starts and waits on the gate
    vm.retry() // cancels it and loads again

    gate.complete(Unit)
    assertEquals(180, vm.uiState.value.suggestedDeposit)
    assertEquals(3, listingRepository.getListingCalls)

    reconnect() // the retry read the listing, so nothing is read again
    assertEquals(3, listingRepository.getListingCalls)
  }
}
