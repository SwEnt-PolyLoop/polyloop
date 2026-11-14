// Made with Claude.

package com.swent.polyloop.ui.chat

import androidx.lifecycle.ViewModel
import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.auth.FakeAuthRepository
import com.swent.polyloop.model.chat.Conversation
import com.swent.polyloop.model.chat.FakeChatRepository
import com.swent.polyloop.model.connectivity.FakeConnectivityRepository
import com.swent.polyloop.model.rental.DepositOffer
import com.swent.polyloop.model.rental.FakeRentalRepository
import com.swent.polyloop.model.rental.Rental
import com.swent.polyloop.model.rental.RentalRepository
import com.swent.polyloop.model.rental.RentalStatus
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
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

/** The rental in the chat: the deposit offer (the card's state and the answers) and the pickup. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelOfferTest {

  private val now = Instant.parse("2026-11-04T10:00:00Z")
  private val expiresAt = Instant.parse("2026-11-11T10:00:00Z")

  private val lender = AuthUser(uid = "owner", email = "mara@epfl.ch", name = "Mara")
  private val borrower = AuthUser(uid = "borrower", email = "loca@epfl.ch", name = "Loca")

  private val lenderOffer = DepositOffer(180, "owner", false, null, expiresAt)
  private val counteroffer = DepositOffer(120, "borrower", true, 180, expiresAt)

  private val request =
      Rental(
          id = "r1",
          listingId = "l1",
          listingTitle = "Camping tent",
          ownerId = "owner",
          ownerName = "Mara",
          borrowerId = "borrower",
          borrowerName = "Loca",
          startDate = LocalDate.of(2026, 12, 10),
          endDate = LocalDate.of(2026, 12, 12),
          status = RentalStatus.REQUESTED,
          pricePerDay = 5,
          totalPrice = 15,
          pickupArea = "Ecublens",
          dropOffArea = "Renens",
          createdAt = now,
      )

  private val dispatcher = UnconfinedTestDispatcher()

  // The phone's time: [clockStart], moved on by the test's virtual time.
  private var clockStart = now
  private val clock = { clockStart.plusMillis(dispatcher.scheduler.currentTime) }

  /** Moves the phone's time on by [millis], running what was waiting for it. */
  private fun advanceClock(millis: Long) {
    dispatcher.scheduler.advanceTimeBy(millis)
    dispatcher.scheduler.runCurrent()
  }

  private lateinit var chat: FakeChatRepository
  private lateinit var connectivity: FakeConnectivityRepository
  private lateinit var rentals: FakeRentalRepository

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    chat = FakeChatRepository(currentUid = "borrower")
    chat.conversations =
        listOf(
            Conversation(
                rentalId = "r1",
                listingTitle = "Camping tent",
                ownerId = "owner",
                ownerName = "Mara",
                borrowerId = "borrower",
                borrowerName = "Loca",
                lastMessage = "",
                lastMessageAt = now,
                status = RentalStatus.REQUESTED,
                startDate = request.startDate,
                endDate = request.endDate,
            )
        )
    connectivity = FakeConnectivityRepository()
    rentals = rentalsWith(request.copy(offer = lenderOffer))
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun rentalsWith(vararg stored: Rental) =
      FakeRentalRepository(rentals = stored.toList(), clock = { now })

  private fun viewModel(
      user: AuthUser = borrower,
      repository: RentalRepository = rentals,
  ): ChatViewModel =
      ChatViewModel(
          "r1",
          chat,
          FakeAuthRepository(signedInUser = user),
          repository,
          connectivity,
          clock,
      )

  private suspend fun stored(id: String = "r1"): Rental = rentals.getRental(id).getOrThrow()!!

  // ---------- The card ----------

  @Test
  fun anOfferJustBeforeItsExpiryCanStillBeAnswered() {
    clockStart = expiresAt.minusMillis(1)

    val state = viewModel().uiState.value

    assertFalse(state.offer!!.isExpired)
    assertTrue(state.canAnswerOffer)
    assertTrue(state.offer!!.canCounter)
  }

  @Test
  fun anOfferLeftOpenBecomesExpiredRightAtItsExpiry() {
    clockStart = expiresAt.minusMillis(1)
    val vm = viewModel()

    advanceClock(1)

    val state = vm.uiState.value
    assertTrue(state.offer!!.isExpired)
    assertFalse(state.canAnswerOffer)
    assertFalse(state.offer!!.canCounter)
  }

  @Test
  fun anExpiredOfferCannotBeAnsweredAndClosesTheCounterForm() {
    clockStart = expiresAt.minusMillis(1)
    val vm = viewModel()
    vm.startCounter()
    vm.onCounterDraftChange("120")

    advanceClock(1)
    vm.acceptOffer()
    vm.sendCounter()

    assertFalse(vm.uiState.value.isCountering)
    assertFalse(vm.uiState.value.canSendCounter)
    vm.startCounter()
    assertFalse(vm.uiState.value.isCountering)
    runBlocking { assertEquals(RentalStatus.REQUESTED, stored().status) }
  }

  @Test
  fun theAuthorAlsoSeesTheirOfferExpired() {
    clockStart = expiresAt

    assertTrue(viewModel(lender).uiState.value.offer!!.isExpired)
  }

  @Test
  fun noOfferMeansNoCard() {
    rentals = rentalsWith(request)

    assertNull(viewModel(lender).uiState.value.offer)
  }

  @Test
  fun theBorrowerSeesTheLendersOfferToAnswerAndCounter() {
    val state = viewModel().uiState.value

    val offer = state.offer!!
    assertEquals(180, offer.amount)
    assertFalse(offer.isMine)
    assertTrue(offer.canCounter)
    assertEquals(expiresAt, offer.expiresAt)
    assertTrue(state.canAnswerOffer)
  }

  @Test
  fun theLenderSeesTheirOwnOfferWaitingWithNoAnswer() {
    val state = viewModel(lender).uiState.value

    assertTrue(state.offer!!.isMine)
    assertFalse(state.offer!!.canCounter)
    assertFalse(state.canAnswerOffer)
  }

  @Test
  fun theLenderCanAnswerACounterofferButNotCounterIt() {
    rentals = rentalsWith(request.copy(offer = counteroffer))

    val state = viewModel(lender).uiState.value

    assertEquals(120, state.offer?.amount)
    assertEquals(180, state.offer?.previousAmount)
    assertTrue(state.canAnswerOffer)
    assertFalse(state.offer!!.canCounter)
  }

  @Test
  fun theLenderSeesHowManyRequestsAcceptingWouldDecline() {
    val touching =
        request.copy(
            id = "r2",
            startDate = LocalDate.of(2026, 12, 13),
            endDate = LocalDate.of(2026, 12, 14),
        )
    val free =
        request.copy(
            id = "r3",
            startDate = LocalDate.of(2026, 12, 14),
            endDate = LocalDate.of(2026, 12, 15),
        )
    rentals = rentalsWith(request.copy(offer = counteroffer), touching, free)

    assertEquals(1, viewModel(lender).uiState.value.offer?.overlappingRequests)
  }

  @Test
  fun theBorrowerIsNotToldAboutOtherRequests() {
    val touching =
        request.copy(
            id = "r2",
            startDate = LocalDate.of(2026, 12, 13),
            endDate = LocalDate.of(2026, 12, 14),
        )
    rentals = rentalsWith(request.copy(offer = lenderOffer), touching)

    assertEquals(0, viewModel().uiState.value.offer?.overlappingRequests)
  }

  @Test
  fun aReadErrorOnTheRentalHidesTheCardAndKeepsTheChat() {
    val failing =
        object : RentalRepository by rentals {
          override fun observeRental(id: String): Flow<Rental?> = flow { error("down") }
        }

    val state = viewModel(repository = failing).uiState.value

    assertNull(state.offer)
    assertNull(state.errorMsg)
    assertFalse(state.isLoading)
  }

  @Test
  fun aReadErrorOnTheLentRentalsKeepsTheCardWithoutAWarning() {
    val failing =
        object : RentalRepository by rentals {
          override fun observeRentalsAsLender(uid: String): Flow<List<Rental>> = flow {
            error("down")
          }
        }

    assertEquals(0, viewModel(repository = failing).uiState.value.offer?.overlappingRequests)
  }

  @Test
  fun theAnswersAreDisabledOffline() {
    val vm = viewModel()

    connectivity.setOnline(false)

    assertFalse(vm.uiState.value.canAnswerOffer)

    connectivity.setOnline(true)

    assertTrue(vm.uiState.value.canAnswerOffer)
  }

  // ---------- Pickup ----------

  @Test
  fun anAcceptedRentalGivesItsFirstDayAsThePickupDateToBothPeople() {
    rentals = rentalsWith(request.copy(status = RentalStatus.ACCEPTED))

    assertEquals(request.startDate, viewModel(borrower).uiState.value.pickupDate)
    assertEquals(request.startDate, viewModel(lender).uiState.value.pickupDate)
  }

  @Test
  fun everyOtherStatusHasNoPickupDate() {
    for (status in RentalStatus.entries - RentalStatus.ACCEPTED) {
      rentals = rentalsWith(request.copy(status = status))

      assertNull(status.name, viewModel().uiState.value.pickupDate)
    }
  }

  @Test
  fun thePickupDateAppearsOnceTheOfferIsAcceptedWhileTheChatIsOpen() = runTest {
    val vm = viewModel()
    assertNull(vm.uiState.value.pickupDate)

    vm.acceptOffer()

    assertEquals(request.startDate, vm.uiState.value.pickupDate)
  }

  @Test
  fun aReadErrorOnTheRentalGivesNoPickupDate() {
    val failing =
        object : RentalRepository by rentalsWith(request.copy(status = RentalStatus.ACCEPTED)) {
          override fun observeRental(id: String): Flow<Rental?> = flow { error("down") }
        }

    assertNull(viewModel(repository = failing).uiState.value.pickupDate)
  }

  // ---------- Answers ----------

  @Test
  fun acceptingMakesTheRentalAcceptedAndRemovesTheCard() = runTest {
    val vm = viewModel()

    vm.acceptOffer()

    assertEquals(RentalStatus.ACCEPTED, stored().status)
    assertEquals(180, stored().deposit)
    assertNull(vm.uiState.value.offer)
    assertFalse(vm.uiState.value.isAnswering)
  }

  @Test
  fun decliningMakesTheRentalDeclinedAndRemovesTheCard() = runTest {
    val vm = viewModel()

    vm.declineOffer()

    assertEquals(RentalStatus.DECLINED, stored().status)
    assertNull(vm.uiState.value.offer)
  }

  @Test
  fun answersDoNothingOnYourOwnOfferOrOffline() = runTest {
    val own = viewModel(lender)
    own.acceptOffer()
    own.declineOffer()
    assertEquals(RentalStatus.REQUESTED, stored().status)

    val vm = viewModel()
    connectivity.setOnline(false)
    vm.acceptOffer()
    vm.declineOffer()
    assertEquals(RentalStatus.REQUESTED, stored().status)
  }

  @Test
  fun aFailedAnswerShowsTheErrorAndKeepsTheCard() {
    val vm = viewModel()
    rentals.failure = IllegalStateException("This offer has expired.")

    vm.acceptOffer()

    assertEquals("This offer has expired.", vm.uiState.value.offerError)
    assertEquals(180, vm.uiState.value.offer?.amount)
    assertFalse(vm.uiState.value.isAnswering)
  }

  @Test
  fun aFailureWithoutAMessageGivesABlankError() {
    val vm = viewModel()
    rentals.failure = IllegalStateException()

    vm.declineOffer()

    assertEquals("", vm.uiState.value.offerError)
  }

  @Test
  fun anAnswerClearsTheLastError() {
    val vm = viewModel()
    rentals.failure = IllegalStateException("down")
    vm.acceptOffer()

    rentals.failure = null
    vm.acceptOffer()

    assertNull(vm.uiState.value.offerError)
  }

  @Test
  fun theAnswersAreLockedWhileOneIsSent() = runTest {
    val vm = viewModel()
    val gate = CompletableDeferred<Unit>()
    rentals.gate = gate

    vm.acceptOffer()
    assertTrue(vm.uiState.value.isAnswering)
    assertFalse(vm.uiState.value.canAnswerOffer)
    vm.declineOffer()

    gate.complete(Unit)

    assertEquals(RentalStatus.ACCEPTED, stored().status)
  }

  // ---------- Counteroffer ----------

  @Test
  fun startCounterOpensAnEmptyFormAndBackClosesIt() {
    val vm = viewModel()

    vm.startCounter()
    assertTrue(vm.uiState.value.isCountering)
    assertEquals("", vm.uiState.value.counterDraft)
    assertFalse(vm.uiState.value.canSendCounter)

    vm.cancelCounter()
    assertFalse(vm.uiState.value.isCountering)
  }

  @Test
  fun startCounterDoesNothingOnACounteroffer() {
    rentals = rentalsWith(request.copy(offer = counteroffer))
    val vm = viewModel(lender)

    vm.startCounter()

    assertFalse(vm.uiState.value.isCountering)
  }

  @Test
  fun aCounterOutOfRangeOrNotWholeCannotBeSent() {
    val vm = viewModel().apply { startCounter() }

    for (amount in listOf("", "0", "-5", "12.5", "abc", "100000")) {
      vm.onCounterDraftChange(amount)
      assertFalse("amount \"$amount\"", vm.uiState.value.canSendCounter)
    }
    for (amount in listOf("1", "99999")) {
      vm.onCounterDraftChange(amount)
      assertTrue("amount \"$amount\"", vm.uiState.value.canSendCounter)
    }
  }

  @Test
  fun sendingTheCounterReplacesTheOfferAndClosesTheForm() = runTest {
    val vm = viewModel()
    vm.startCounter()
    vm.onCounterDraftChange("120")

    vm.sendCounter()

    val state = vm.uiState.value
    assertFalse(state.isCountering)
    assertEquals(120, state.offer?.amount)
    assertEquals(180, state.offer?.previousAmount)
    assertTrue(state.offer!!.isMine)
    assertEquals(120, stored().offer?.amount)
  }

  @Test
  fun sendCounterDoesNothingWithAnInvalidAmount() = runTest {
    val vm = viewModel()
    vm.startCounter()

    vm.sendCounter()

    assertEquals(180, stored().offer?.amount)
    assertTrue(vm.uiState.value.isCountering)
  }

  @Test
  fun aFailedCounterKeepsTheFormAndItsAmount() {
    val vm = viewModel()
    vm.startCounter()
    vm.onCounterDraftChange("120")
    rentals.failure = IllegalStateException("refused")

    vm.sendCounter()

    val state = vm.uiState.value
    assertEquals("refused", state.offerError)
    assertTrue(state.isCountering)
    assertEquals("120", state.counterDraft)
  }

  @Test
  fun theFormClosesWhenTheOfferIsGone() = runTest {
    val vm = viewModel()
    vm.startCounter()

    rentals.declineOffer("r1").getOrThrow()

    assertFalse(vm.uiState.value.isCountering)
    assertNull(vm.uiState.value.offer)
  }

  // ---------- Factory ----------

  private class OtherViewModel : ViewModel()

  private fun factory() =
      ChatViewModel.Factory(
          "r1",
          chat,
          FakeAuthRepository(signedInUser = borrower),
          rentals,
          connectivity,
      )

  @Test
  fun factoryCreatesTheViewModel() {
    assertEquals(180, factory().create(ChatViewModel::class.java).uiState.value.offer?.amount)
  }

  @Test(expected = IllegalArgumentException::class)
  fun factoryRefusesAnotherViewModel() {
    factory().create(OtherViewModel::class.java)
  }
}
