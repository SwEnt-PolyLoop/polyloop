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
import com.swent.polyloop.model.rental.InMemoryServerClockStore
import com.swent.polyloop.model.rental.Rental
import com.swent.polyloop.model.rental.RentalRepository
import com.swent.polyloop.model.rental.RentalStatus
import com.swent.polyloop.model.rental.ServerClock
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

  /** The rentals [stored], on a server whose day is the pickup day of [request]. */
  private fun rentalsWith(vararg stored: Rental) =
      FakeRentalRepository(rentals = stored.toList(), clock = { now }).apply {
        serverNow = request.startDate.atTime(12, 0).atZone(ZoneId.of("Europe/Zurich")).toInstant()
      }

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
          serverClock(repository),
          clock,
      )

  private fun serverClock(repository: RentalRepository) =
      ServerClock(
          repository,
          InMemoryServerClockStore(),
          elapsedRealtime = { 0L },
          bootCount = { 0 },
          ioDispatcher = dispatcher,
      )

  /** [rentals] with a server that cannot be reached until [reachable] returns true. */
  private fun unreachableServer(reachable: () -> Boolean = { false }) =
      object : RentalRepository by rentals {
        override suspend fun serverNow(): Result<Instant> =
            if (reachable()) rentals.serverNow() else Result.failure(IOException("offline"))
      }

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
  fun anOfferThatExpiredWhileThePhoneSleptShowsExpiredWhenTheScreenComesBack() {
    clockStart = expiresAt.minusSeconds(3600)
    val vm = viewModel()
    vm.onStarted()

    // The phone sleeps past the expiry: its clock moves on, but the wait (uptime) does not.
    vm.onStopped()
    clockStart = expiresAt.plusSeconds(60)
    dispatcher.scheduler.runCurrent()
    assertFalse(vm.uiState.value.offer!!.isExpired)

    vm.onStarted()

    assertTrue(vm.uiState.value.offer!!.isExpired)
    assertFalse(vm.uiState.value.canAnswerOffer)
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
  fun onlyTheLenderOfARequestWithNoOfferIsLedToReviewRequest() = runTest {
    rentals = rentalsWith(request)

    assertTrue(viewModel(lender).uiState.value.canReviewRequest)
    assertFalse(viewModel(borrower).uiState.value.canReviewRequest)

    // Once the offer is sent, the card replaces it.
    val vm = viewModel(lender)
    rentals.makeOffer("r1", 180, "A", "B").getOrThrow()
    assertFalse(vm.uiState.value.canReviewRequest)
  }

  @Test
  fun aDeclinedRequestNoLongerLeadsToReviewRequest() = runTest {
    rentals = rentalsWith(request)
    val vm = viewModel(lender)

    rentals.declineRequest("r1").getOrThrow()

    assertFalse(vm.uiState.value.canReviewRequest)
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
  fun everyOtherStatusHasNoPickupDateAndDoesNotReadTheServersDay() {
    for (status in RentalStatus.entries - RentalStatus.ACCEPTED) {
      rentals = rentalsWith(request.copy(status = status))

      val state = viewModel().apply { onStarted() }.uiState.value
      assertNull(status.name, state.pickupDate)
      assertNull(status.name, state.serverToday)
      assertEquals(status.name, 0, rentals.serverNowCalls)
    }
  }

  @Test
  fun anAcceptedRentalReadsTheServersDayAndIsOnItsPickupDay() {
    rentals = rentalsWith(request.copy(status = RentalStatus.ACCEPTED))

    val state = viewModel().uiState.value

    assertEquals(request.startDate, state.serverToday)
    assertTrue(state.isPickupDay)
  }

  @Test
  fun anotherServerDayIsNotThePickupDay() {
    rentals = rentalsWith(request.copy(status = RentalStatus.ACCEPTED))
    rentals.serverNow = Instant.parse("2026-12-09T12:00:00Z")

    val state = viewModel().uiState.value

    assertEquals(LocalDate.of(2026, 12, 9), state.serverToday)
    assertFalse(state.isPickupDay)
  }

  @Test
  fun anUnknownServerDayIsNotThePickupDayAndShowsNoError() {
    rentals = rentalsWith(request.copy(status = RentalStatus.ACCEPTED))

    val state = viewModel(repository = unreachableServer()).uiState.value

    assertEquals(request.startDate, state.pickupDate)
    assertNull(state.serverToday)
    assertFalse(state.isPickupDay)
    assertNull(state.errorMsg)
    assertFalse(state.isLoading)
  }

  @Test
  fun anUnknownServerDayIsReadAgainWhenThePhoneComesBackOnline() {
    rentals = rentalsWith(request.copy(status = RentalStatus.ACCEPTED))
    connectivity = FakeConnectivityRepository(online = false)
    var reachable = false
    val vm = viewModel(repository = unreachableServer { reachable })
    assertNull(vm.uiState.value.serverToday)

    reachable = true
    connectivity.setOnline(true)

    assertEquals(request.startDate, vm.uiState.value.serverToday)
    assertTrue(vm.uiState.value.isPickupDay)
  }

  @Test
  fun aKnownServerDayIsNotReadAgainWhenThePhoneComesBackOnline() {
    rentals = rentalsWith(request.copy(status = RentalStatus.ACCEPTED))
    viewModel()

    connectivity.setOnline(false)
    connectivity.setOnline(true)

    assertEquals(1, rentals.serverNowCalls)
  }

  @Test
  fun aDayTheServerFailedToGiveOnlineIsReadAgainWhenTheChatComesBackToTheFront() {
    rentals = rentalsWith(request.copy(status = RentalStatus.ACCEPTED))
    var reachable = false
    val vm = viewModel(repository = unreachableServer { reachable })
    vm.onStarted()
    assertFalse(vm.uiState.value.isOffline)
    assertNull(vm.uiState.value.serverToday)

    reachable = true
    vm.onStopped()
    vm.onStarted()

    assertEquals(request.startDate, vm.uiState.value.serverToday)
    assertTrue(vm.uiState.value.isPickupDay)
  }

  @Test
  fun comingBackToTheChatReadsNothingWhenTheDayIsKnownOrThePhoneIsOffline() {
    rentals = rentalsWith(request.copy(status = RentalStatus.ACCEPTED))
    viewModel().onStarted()
    assertEquals(1, rentals.serverNowCalls)

    connectivity.setOnline(false)
    var reads = 0
    val vm =
        viewModel(
            repository =
                unreachableServer {
                  reads++
                  false
                }
        )
    vm.onStarted()
    assertEquals(1, reads)
  }

  @Test
  fun thePickupDateAppearsOnceTheOfferIsAcceptedWhileTheChatIsOpen() = runTest {
    val vm = viewModel()
    assertNull(vm.uiState.value.pickupDate)
    assertNull(vm.uiState.value.serverToday)

    vm.acceptOffer()

    assertEquals(request.startDate, vm.uiState.value.pickupDate)
    assertEquals(request.startDate, vm.uiState.value.serverToday)
  }

  @Test
  fun aRentalAcceptedWhileTheChatIsOpenReadsTheServersDayOnce() {
    // The live rental is driven by hand, so it can change again once accepted.
    val live = MutableStateFlow<Rental?>(request)
    val repository =
        object : RentalRepository by rentals {
          override fun observeRental(id: String): Flow<Rental?> = live
        }
    val vm = viewModel(repository = repository)

    live.value = request.copy(status = RentalStatus.ACCEPTED)
    live.value = request.copy(status = RentalStatus.ACCEPTED, deposit = 70)

    assertEquals(request.startDate, vm.uiState.value.serverToday)
    assertEquals(1, rentals.serverNowCalls)
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
          serverClock(rentals),
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
