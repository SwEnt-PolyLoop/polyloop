// Made with Claude.

package com.swent.polyloop.model.wallet

import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WalletRepositoryFirestoreTest {

  private val db = mockk<FirebaseFirestore>()
  private val wallets = mockk<CollectionReference>()
  private val walletDocument = mockk<DocumentReference>()
  private val transactions = mockk<CollectionReference>()
  private val registration = mockk<ListenerRegistration>(relaxed = true)
  private val repository = WalletRepositoryFirestore(db)

  private val firestoreError =
      FirebaseFirestoreException("unavailable", FirebaseFirestoreException.Code.UNAVAILABLE)

  private val date = Instant.parse("2026-10-14T10:00:00Z")

  @Before
  fun setUp() {
    every { db.collection(WalletFields.COLLECTION) } returns wallets
    every { wallets.document("me") } returns walletDocument
    every { walletDocument.collection(WalletFields.TRANSACTIONS) } returns transactions
  }

  private fun snapshot(id: String, data: Map<String, Any>?): DocumentSnapshot = mockk {
    every { this@mockk.id } returns id
    every { getData(ServerTimestampBehavior.ESTIMATE) } returns data
  }

  private fun row(
      type: Any? = "DEPOSIT_HELD",
      amount: Any? = -30L,
      date: Any? = Timestamp(this.date),
      title: Any? = "Camping tent",
  ): Map<String, Any> =
      listOfNotNull(
              type?.let { WalletFields.TYPE to it },
              amount?.let { WalletFields.AMOUNT to it },
              date?.let { WalletFields.DATE to it },
              title?.let { WalletFields.ITEM_TITLE to it },
          )
          .toMap()

  // ---------- observeWallet ----------

  private val walletListener = slot<EventListener<DocumentSnapshot>>()

  private fun walletIsObserved() {
    every { walletDocument.addSnapshotListener(capture(walletListener)) } returns registration
  }

  @Test
  fun observeWalletReadsTheThreeAmounts() = runTest {
    walletIsObserved()

    val wallet = async { repository.observeWallet("me").first() }
    runCurrent()
    walletListener.captured.onEvent(
        snapshot(
            "me",
            mapOf(
                WalletFields.AVAILABLE to 70L,
                WalletFields.HELD_AS_DEPOSITS to 20L,
                WalletFields.HELD_FOR_RENTALS to 10L,
            ),
        ),
        null,
    )

    assertEquals(Wallet(available = 70, heldAsDeposits = 20, heldForRentals = 10), wallet.await())
  }

  @Test
  fun observeWalletReadsMissingFieldsAndNoDocumentAsZero() = runTest {
    walletIsObserved()

    val wallets = async { repository.observeWallet("me").take(2).toList() }
    runCurrent()
    walletListener.captured.onEvent(snapshot("me", mapOf(WalletFields.AVAILABLE to 5L)), null)
    walletListener.captured.onEvent(snapshot("me", null), null)

    assertEquals(listOf(Wallet(available = 5), Wallet()), wallets.await())
  }

  @Test
  fun observeWalletReadsNonIntegerAndNegativeValuesAsZero() = runTest {
    walletIsObserved()

    val wallet = async { repository.observeWallet("me").first() }
    runCurrent()
    walletListener.captured.onEvent(
        snapshot(
            "me",
            mapOf(
                WalletFields.AVAILABLE to 12.5,
                WalletFields.HELD_AS_DEPOSITS to -20L,
                WalletFields.HELD_FOR_RENTALS to "10",
            ),
        ),
        null,
    )

    assertEquals(Wallet(), wallet.await())
  }

  @Test
  fun observeWalletReadsAValueAboveIntMaxAsZero() = runTest {
    walletIsObserved()

    val wallet = async { repository.observeWallet("me").first() }
    runCurrent()
    walletListener.captured.onEvent(
        snapshot(
            "me",
            mapOf(
                WalletFields.AVAILABLE to Int.MAX_VALUE.toLong() + 1,
                WalletFields.HELD_AS_DEPOSITS to 20L,
            ),
        ),
        null,
    )

    assertEquals(Wallet(available = 0, heldAsDeposits = 20), wallet.await())
  }

  @Test
  fun observeWalletEmitsOnlyRealChanges() = runTest {
    walletIsObserved()

    val wallets = async { repository.observeWallet("me").take(2).toList() }
    runCurrent()
    val same = mapOf(WalletFields.AVAILABLE to 5L)
    walletListener.captured.onEvent(snapshot("me", same), null)
    walletListener.captured.onEvent(snapshot("me", same), null)
    walletListener.captured.onEvent(snapshot("me", mapOf(WalletFields.AVAILABLE to 6L)), null)

    assertEquals(listOf(Wallet(available = 5), Wallet(available = 6)), wallets.await())
  }

  @Test
  fun observeWalletReadsAnInvalidUidAsAnEmptyWalletWithoutReading() = runTest {
    assertEquals(Wallet(), repository.observeWallet("").first())
    assertEquals(Wallet(), repository.observeWallet("a/b").first())
    verify(exactly = 0) { db.collection(any()) }
  }

  @Test
  fun observeWalletThrowsReadErrorsToTheCollector() = runTest {
    walletIsObserved()

    val error = async {
      var caught: Throwable? = null
      repository.observeWallet("me").catch { caught = it }.toList()
      caught
    }
    runCurrent()
    walletListener.captured.onEvent(null, firestoreError)

    assertSame(firestoreError, error.await())
  }

  @Test
  fun observeWalletUsesAPlainListenerSoTheCacheServesItOffline() = runTest {
    walletIsObserved()

    val wallet = async { repository.observeWallet("me").first() }
    runCurrent()
    walletListener.captured.onEvent(snapshot("me", mapOf(WalletFields.AVAILABLE to 5L)), null)
    wallet.await()

    // The plain listener serves the cache, also offline; no forced server read is made.
    verify(exactly = 1) {
      walletDocument.addSnapshotListener(any<EventListener<DocumentSnapshot>>())
    }
    verify(exactly = 0) { walletDocument.get(any()) }
    verify(exactly = 0) { walletDocument.get() }
  }

  // ---------- observeHistory ----------

  private val historyListener = slot<EventListener<QuerySnapshot>>()

  private fun historyIsObserved() {
    every { transactions.addSnapshotListener(capture(historyListener)) } returns registration
  }

  private fun querySnapshot(vararg documents: DocumentSnapshot): QuerySnapshot = mockk {
    every { this@mockk.documents } returns documents.toList()
  }

  @Test
  fun observeHistoryMapsEachRowWithItsDocumentId() = runTest {
    historyIsObserved()

    val history = async { repository.observeHistory("me").first() }
    runCurrent()
    historyListener.captured.onEvent(
        querySnapshot(
            snapshot("deposit_held_r1", row()),
            snapshot("t2", row(type = "RECHARGE", amount = 50L, title = null)),
        ),
        null,
    )

    assertEquals(
        listOf(
            WalletTransaction(
                "deposit_held_r1",
                WalletTransactionType.DEPOSIT_HELD,
                -30,
                "Camping tent",
                date,
            ),
            WalletTransaction("t2", WalletTransactionType.RECHARGE, 50, "", date),
        ),
        history.await(),
    )
  }

  @Test
  fun observeHistoryReadsTheRentalPriceRows() = runTest {
    historyIsObserved()

    val history = async { repository.observeHistory("me").first() }
    runCurrent()
    historyListener.captured.onEvent(
        querySnapshot(
            snapshot("rental_held_r1", row(type = "RENTAL_HELD", amount = -45L)),
            snapshot("rental_released_r1", row(type = "RENTAL_RELEASED", amount = 45L)),
        ),
        null,
    )

    assertEquals(
        listOf(
            WalletTransaction(
                "rental_held_r1",
                WalletTransactionType.RENTAL_HELD,
                -45,
                "Camping tent",
                date,
            ),
            WalletTransaction(
                "rental_released_r1",
                WalletTransactionType.RENTAL_RELEASED,
                45,
                "Camping tent",
                date,
            ),
        ),
        history.await(),
    )
  }

  @Test
  fun observeHistorySkipsMalformedRows() = runTest {
    historyIsObserved()

    val history = async { repository.observeHistory("me").first() }
    runCurrent()
    historyListener.captured.onEvent(
        querySnapshot(
            snapshot("unknown-type", row(type = "GIFT")),
            snapshot("no-type", row(type = null)),
            snapshot("no-date", row(date = null)),
            snapshot("date-as-text", row(date = "2026-10-14")),
            snapshot("no-amount", row(amount = null)),
            snapshot("zero-amount", row(amount = 0L)),
            snapshot("fractional-amount", row(amount = 1.5)),
            snapshot("good", row()),
        ),
        null,
    )

    assertEquals(listOf("good"), history.await().map { it.id })
  }

  @Test
  fun observeHistoryKeepsARowWhoseItemTitleIsNotAStringWithAnEmptyTitle() = runTest {
    historyIsObserved()

    val history = async { repository.observeHistory("me").first() }
    runCurrent()
    historyListener.captured.onEvent(
        querySnapshot(snapshot("odd-title", row(title = 42L))),
        null,
    )

    assertEquals(
        listOf(
            WalletTransaction("odd-title", WalletTransactionType.DEPOSIT_HELD, -30, "", date),
        ),
        history.await(),
    )
  }

  @Test
  fun observeHistoryGivesAnEmptyListWithoutTransactions() = runTest {
    historyIsObserved()

    val history = async { repository.observeHistory("me").first() }
    runCurrent()
    historyListener.captured.onEvent(querySnapshot(), null)

    assertEquals(emptyList<WalletTransaction>(), history.await())
  }

  @Test
  fun observeHistoryKeepsTheOrderOfTheSnapshot() = runTest {
    historyIsObserved()

    val history = async { repository.observeHistory("me").first() }
    runCurrent()
    historyListener.captured.onEvent(
        querySnapshot(snapshot("b", row()), snapshot("a", row())),
        null,
    )

    assertEquals(listOf("b", "a"), history.await().map { it.id })
  }

  @Test
  fun observeHistoryReadsAnInvalidUidAsEmptyWithoutReading() = runTest {
    assertEquals(emptyList<WalletTransaction>(), repository.observeHistory(" ").first())
    assertEquals(emptyList<WalletTransaction>(), repository.observeHistory("a/b").first())
    verify(exactly = 0) { db.collection(any()) }
  }

  @Test
  fun observeHistoryThrowsReadErrorsToTheCollector() = runTest {
    historyIsObserved()

    val error = async {
      var caught: Throwable? = null
      repository.observeHistory("me").catch { caught = it }.toList()
      caught
    }
    runCurrent()
    historyListener.captured.onEvent(null, firestoreError)

    assertSame(firestoreError, error.await())
  }

  // ---------- recharge ----------

  @Test
  fun rechargeFailsAsNotAvailableYetWithoutReadingOrWriting() = runTest {
    val result = repository.recharge("me", 10)

    assertTrue(result.exceptionOrNull() is UnsupportedOperationException)
    verify(exactly = 0) { db.collection(any()) }
  }
}
