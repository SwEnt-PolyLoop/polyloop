// Made with Claude.

package com.swent.polyloop.ui.listingdetail

import androidx.lifecycle.ViewModel
import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.auth.FakeAuthRepository
import com.swent.polyloop.model.listing.BookedRange
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.model.listing.ListingStatus
import com.swent.polyloop.model.profile.FakeProfileRepository
import com.swent.polyloop.model.profile.UserProfile
import com.swent.polyloop.model.rental.FakeRentalRepository
import com.swent.polyloop.model.rental.RentalStatus
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
class ListingDetailViewModelTest {

  private val listing =
      Listing(
          id = "listing1",
          ownerId = "user1",
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

  private val oct6 = LocalDate.of(2026, 10, 6)
  private val oct8 = LocalDate.of(2026, 10, 8)
  private val today = LocalDate.of(2026, 10, 1)

  private val me = AuthUser(uid = "me", email = "loca@epfl.ch", name = "Loca")

  private lateinit var repository: FakeListingRepository
  private lateinit var rentalRepository: FakeRentalRepository
  private lateinit var authRepository: FakeAuthRepository
  private lateinit var profileRepository: FakeProfileRepository

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    repository = FakeListingRepository(listings = listOf(listing))
    rentalRepository = FakeRentalRepository()
    authRepository = FakeAuthRepository(signedInUser = me)
    profileRepository =
        FakeProfileRepository(profiles = listOf(UserProfile("owner", "Mara", "mara@epfl.ch")))
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun viewModel(id: String = listing.id) =
      ListingDetailViewModel(
          id,
          repository,
          rentalRepository,
          authRepository,
          profileRepository,
          today = { today },
      )

  private fun factory() =
      ListingDetailViewModel.Factory(
          listing.id,
          repository,
          rentalRepository,
          authRepository,
          profileRepository,
          today = { today },
      )

  /** A view model on the listing, owned by [ownerId], with its date picker open. */
  private fun openPickerOn(ownerId: String = "owner"): ListingDetailViewModel {
    repository.listings = listOf(listing.copy(ownerId = ownerId))
    return viewModel().also { it.openDatePicker() }
  }

  @Test
  fun showsLoadingThenHoldsTheListing() {
    val gate = CompletableDeferred<Unit>()
    repository.gate = gate
    val vm = viewModel()
    assertTrue(vm.uiState.value.isLoading)

    gate.complete(Unit)

    val state = vm.uiState.value
    assertFalse(state.isLoading)
    assertEquals(listing, state.listing)
    assertFalse(state.listingNotFound)
    assertNull(state.errorMsg)
  }

  @Test
  fun missingOrUnpublishedListingIsNotFound() {
    assertTrue(viewModel(id = "unknown").uiState.value.listingNotFound)

    repository.listings = listOf(listing.copy(status = ListingStatus.DRAFT))
    val state = viewModel().uiState.value
    assertTrue(state.listingNotFound)
    assertNull(state.listing)
  }

  @Test
  fun repositoryFailureSetsErrorMsg() {
    repository.failure = IllegalStateException("Network down")
    assertEquals("Network down", viewModel().uiState.value.errorMsg)

    // Blank, not null: the screen shows its generic error text.
    repository.failure = RuntimeException()
    assertEquals("", viewModel().uiState.value.errorMsg)
  }

  @Test
  fun retryClearsErrorAndLoadsAgain() {
    repository.failure = IllegalStateException("Network down")
    val vm = viewModel()

    repository.failure = null
    vm.retry()

    assertNull(vm.uiState.value.errorMsg)
    assertEquals(listing, vm.uiState.value.listing)
    assertEquals(2, repository.getListingCalls)
  }

  @Test
  fun validRangeSetsDates() {
    val vm = viewModel()

    vm.selectDates(oct6, oct8)

    val state = vm.uiState.value
    assertEquals(oct6, state.startDate)
    assertEquals(oct8, state.endDate)
  }

  @Test
  fun invalidRangesAreIgnored() {
    val vm = viewModel()
    vm.selectDates(oct6, oct8)
    val before = vm.uiState.value

    vm.selectDates(oct6.minusDays(1), oct8) // starts before the window
    vm.selectDates(oct6, listing.availableTo.plusDays(1)) // ends after the window
    vm.selectDates(oct8, oct6) // ends before it starts

    assertEquals(before, vm.uiState.value)
  }

  @Test
  fun clearDatesRemovesSelection() {
    val vm = viewModel()
    vm.selectDates(oct6, oct8)
    vm.clearDates()

    assertNull(vm.uiState.value.startDate)
    assertNull(vm.uiState.value.endDate)
  }

  @Test
  fun datePickerOpensDismissesAndClosesOnValidPick() {
    val vm = viewModel()
    vm.selectDates(oct6, oct8)

    vm.openDatePicker()
    assertTrue(vm.uiState.value.isDatePickerOpen)
    vm.dismissDatePicker()
    assertFalse(vm.uiState.value.isDatePickerOpen)
    assertEquals(oct6, vm.uiState.value.startDate)

    vm.openDatePicker()
    vm.selectDates(oct8, oct8)
    assertFalse(vm.uiState.value.isDatePickerOpen)
  }

  @Test
  fun datesAndPickerAreIgnoredWithoutListing() {
    repository.listings = emptyList()
    val vm = viewModel()

    vm.openDatePicker()
    vm.selectDates(oct6, oct8)

    assertFalse(vm.uiState.value.isDatePickerOpen)
    assertNull(vm.uiState.value.startDate)
  }

  @Test
  fun factoryCreatesViewModelForTheListing() {
    val vm = factory().create(ListingDetailViewModel::class.java)
    assertEquals(listing, vm.uiState.value.listing)
  }

  @Test(expected = IllegalArgumentException::class)
  fun factoryRejectsOtherViewModels() {
    factory().create(OtherViewModel::class.java)
  }

  @Test
  fun sendRequestCreatesTheRentalWithTheRightValues() {
    val vm = openPickerOn()

    vm.sendRequest(oct6, oct8)

    val rental = rentalRepository.rentals.single()
    assertEquals(listing.id, rental.listingId)
    assertEquals("owner", rental.ownerId)
    assertEquals("Mara", rental.ownerName)
    assertEquals("me", rental.borrowerId)
    assertEquals("Loca", rental.borrowerName)
    assertEquals(oct6, rental.startDate)
    assertEquals(oct8, rental.endDate)
    assertEquals(RentalStatus.REQUESTED, rental.status)
    assertEquals(rental.id, vm.uiState.value.sentRentalId)
    assertFalse(vm.uiState.value.isDatePickerOpen)
  }

  @Test
  fun missingOrFailedOwnerProfileSendsWithBlankName() {
    profileRepository = FakeProfileRepository()
    openPickerOn().sendRequest(oct6, oct8)

    profileRepository = FakeProfileRepository(failure = IllegalStateException("Network down"))
    openPickerOn().sendRequest(oct6, oct8)

    assertEquals(listOf("", ""), rentalRepository.rentals.map { it.ownerName })
  }

  @Test
  fun failedRequestShowsTheErrorAndKeepsTheSheetOpen() {
    rentalRepository.failure = IllegalStateException("Network down")
    val vm = openPickerOn()

    vm.sendRequest(oct6, oct8)

    val state = vm.uiState.value
    assertEquals("Network down", state.sendError)
    assertFalse(state.isSending)
    assertTrue(state.isDatePickerOpen)
    assertNull(state.sentRentalId)
  }

  @Test
  fun isSendingWhileTheRequestRuns() {
    val gate = CompletableDeferred<Unit>()
    rentalRepository.gate = gate
    val vm = openPickerOn()

    vm.sendRequest(oct6, oct8)
    assertTrue(vm.uiState.value.isSending)

    gate.complete(Unit)
    assertFalse(vm.uiState.value.isSending)
  }

  @Test
  fun secondTapWhileSendingSendsNothing() {
    val gate = CompletableDeferred<Unit>()
    rentalRepository.gate = gate
    val vm = openPickerOn()

    vm.sendRequest(oct6, oct8)
    vm.sendRequest(oct6, oct8)
    gate.complete(Unit)

    assertEquals(1, rentalRepository.rentals.size)
  }

  @Test
  fun notSignedInSendsNothingAndShowsTheGenericError() {
    authRepository.signedInUser = null
    val vm = openPickerOn()
    vm.sendRequest(oct6.minusDays(1), oct8) // refused first: shows the dates-taken message

    vm.sendRequest(oct6, oct8)

    assertEquals("", vm.uiState.value.sendError)
    assertFalse(vm.uiState.value.datesTaken)
    assertTrue(rentalRepository.rentals.isEmpty())
  }

  @Test
  fun ownListingIsFlaggedAndCannotSend() {
    val vm = openPickerOn(ownerId = "me")

    vm.sendRequest(oct6, oct8)

    assertTrue(vm.uiState.value.isOwnListing)
    assertTrue(rentalRepository.rentals.isEmpty())
  }

  @Test
  fun invalidRangeSendsNothing() {
    val vm = openPickerOn()

    vm.sendRequest(oct6.minusDays(1), oct8)

    assertTrue(rentalRepository.rentals.isEmpty())
  }

  @Test
  fun onRequestSentHandledClearsTheRentalId() {
    val vm = openPickerOn()
    vm.sendRequest(oct6, oct8)

    vm.onRequestSentHandled()

    assertNull(vm.uiState.value.sentRentalId)
  }

  private val oct10 = LocalDate.of(2026, 10, 10)
  private val oct11 = LocalDate.of(2026, 10, 11)

  @Test
  fun bookedRangesOfTheListingAreDisabled() {
    repository.listings = listOf(listing.copy(bookedRanges = listOf(BookedRange(oct10, oct11))))

    assertEquals(setOf(oct10, oct11), viewModel().uiState.value.disabledDates)
  }

  @Test
  fun rangeThePickerWouldRefuseSendsNothing() {
    repository.listings =
        listOf(listing.copy(ownerId = "owner", bookedRanges = listOf(BookedRange(oct10, oct11))))
    val vm = viewModel().also { it.openDatePicker() }

    vm.sendRequest(oct8, oct10) // crosses a booked day
    vm.sendRequest(oct8, listing.availableTo) // leaves no free day after it

    assertTrue(rentalRepository.rentals.isEmpty())
  }

  @Test
  fun datesBookedMeanwhileAreNotSentAndBecomeDisabled() {
    val vm = openPickerOn()
    repository.listings =
        listOf(listing.copy(ownerId = "owner", bookedRanges = listOf(BookedRange(oct6, oct6))))

    vm.sendRequest(oct6, oct8)

    val state = vm.uiState.value
    assertTrue(rentalRepository.rentals.isEmpty())
    assertTrue(state.datesTaken)
    assertEquals(setOf(oct6), state.disabledDates)
    assertTrue(state.isDatePickerOpen)
    assertFalse(state.isSending)

    vm.openDatePicker()
    assertFalse(vm.uiState.value.datesTaken)
  }

  @Test
  fun datesTakenThenNewDaysPickedSendsTheRequest() {
    val vm = openPickerOn()
    repository.listings =
        listOf(listing.copy(ownerId = "owner", bookedRanges = listOf(BookedRange(oct6, oct6))))
    vm.sendRequest(oct6, oct8)

    vm.clearSendError()
    assertFalse(vm.uiState.value.datesTaken)
    vm.sendRequest(oct10, oct11)

    val state = vm.uiState.value
    assertEquals(1, rentalRepository.rentals.size)
    assertEquals(oct10, rentalRepository.rentals.single().startDate)
    assertEquals(rentalRepository.rentals.single().id, state.sentRentalId)
    assertFalse(state.datesTaken)
    assertNull(state.sendError)
  }

  @Test
  fun failedBookedRangesReadSendsNothingAndShowsTheGenericError() {
    val vm = openPickerOn()
    repository.failure =
        IllegalStateException("Failed to get document because the client is offline")

    vm.sendRequest(oct6, oct8)

    assertTrue(rentalRepository.rentals.isEmpty())
    assertEquals("", vm.uiState.value.sendError)
    assertFalse(vm.uiState.value.isSending)
  }

  @Test
  fun listingDeletedMeanwhileShowsNotFound() {
    val vm = openPickerOn()
    repository.listings = emptyList()

    vm.sendRequest(oct6, oct8)

    val state = vm.uiState.value
    assertTrue(rentalRepository.rentals.isEmpty())
    assertTrue(state.listingNotFound)
    assertNull(state.listing)
    assertFalse(state.isDatePickerOpen)
    assertFalse(state.isSending)
  }

  @Test
  fun startDayPassedWhilePickingShowsDatesTaken() {
    repository.listings = listOf(listing.copy(ownerId = "owner"))
    var now = oct6
    val vm =
        ListingDetailViewModel(
                listing.id,
                repository,
                rentalRepository,
                authRepository,
                profileRepository,
                today = { now },
            )
            .also { it.openDatePicker() }
    now = now.plusDays(1)
    // Any read would now fail and show a send error.
    repository.failure = IllegalStateException("Network down")

    vm.sendRequest(oct6, oct8)

    assertTrue(rentalRepository.rentals.isEmpty())
    assertNull(vm.uiState.value.sendError)
    assertTrue(vm.uiState.value.datesTaken)
    assertTrue(vm.uiState.value.isDatePickerOpen)
  }

  private class OtherViewModel : ViewModel()
}
