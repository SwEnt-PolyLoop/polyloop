// Made with Claude.

package com.swent.polyloop.model.wallet

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.swent.polyloop.utils.FirebaseEmulator
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the real [WalletRepositoryFirestore] against the Firestore emulator (not mocks). Clients
 * cannot write wallets, so the data is seeded as an admin, as Cloud Functions would. Needs
 * `firebase emulators:start`.
 */
@RunWith(AndroidJUnit4::class)
class WalletRepositoryFirestoreEmulatorTest {

  private val repository = WalletRepositoryFirestore()

  private lateinit var uid: String
  private val date = Instant.parse("2026-10-14T10:00:00Z")

  @Before
  fun setUp() {
    FirebaseEmulator.connect()
    runBlocking {
      uid = FirebaseEmulator.signUpVerified("wallet-${UUID.randomUUID()}@epfl.ch", PASSWORD)
    }
  }

  @After
  fun tearDown() {
    Firebase.auth.signOut()
  }

  private fun seedTransaction(id: String, amount: Int, type: String = "DEPOSIT_HELD") =
      FirebaseEmulator.createDocumentAsAdmin(
          "wallets/$uid/transactions/$id",
          mapOf(
              "type" to type,
              "amount" to amount,
              "itemTitle" to "Camping tent",
              "date" to Timestamp(date),
          ),
      )

  @Test
  fun observeWalletReadsTheStoredAmounts() {
    FirebaseEmulator.createDocumentAsAdmin(
        "wallets/$uid",
        mapOf("available" to 70, "heldAsDeposits" to 20, "heldForRentals" to 10),
    )

    runBlocking {
      val wallet = withTimeout(TIMEOUT_MS) { repository.observeWallet(uid).first() }

      assertEquals(Wallet(available = 70, heldAsDeposits = 20, heldForRentals = 10), wallet)
    }
  }

  @Test
  fun observeWalletReadsAMissingDocumentAsAnEmptyWallet() {
    runBlocking {
      val wallet = withTimeout(TIMEOUT_MS) { repository.observeWallet(uid).first() }

      assertEquals(Wallet(), wallet)
    }
  }

  @Test
  fun observeWalletFollowsChanges() {
    FirebaseEmulator.createDocumentAsAdmin("wallets/$uid", mapOf("available" to 5))

    runBlocking {
      assertEquals(
          Wallet(available = 5),
          withTimeout(TIMEOUT_MS) { repository.observeWallet(uid).first() },
      )
      FirebaseEmulator.deleteDocumentAsAdmin("wallets/$uid")
      FirebaseEmulator.createDocumentAsAdmin("wallets/$uid", mapOf("available" to 9))
      assertEquals(
          Wallet(available = 9),
          withTimeout(TIMEOUT_MS) { repository.observeWallet(uid).first { it.available == 9 } },
      )
    }
  }

  @Test
  fun observeHistoryReadsSeveralRowsAndSkipsMalformedOnes() {
    seedTransaction("t1", 50, type = "RECHARGE")
    seedTransaction("deposit_held_r1", -30)
    FirebaseEmulator.createDocumentAsAdmin(
        "wallets/$uid/transactions/broken",
        mapOf("type" to "GIFT", "amount" to 1),
    )

    runBlocking {
      val history =
          withTimeout(TIMEOUT_MS) { repository.observeHistory(uid).first { it.size == 2 } }

      assertEquals(setOf("t1", "deposit_held_r1"), history.map { it.id }.toSet())
      val held = history.first { it.id == "deposit_held_r1" }
      assertEquals(
          WalletTransaction(
              "deposit_held_r1",
              WalletTransactionType.DEPOSIT_HELD,
              -30,
              "Camping tent",
              date,
          ),
          held,
      )
    }
  }

  @Test
  fun observeHistoryIsEmptyWithoutTransactions() {
    runBlocking {
      assertEquals(
          emptyList<WalletTransaction>(),
          withTimeout(TIMEOUT_MS) { repository.observeHistory(uid).first() },
      )
    }
  }

  @Test
  fun observeHistoryFollowsANewRow() {
    seedTransaction("t1", 50, type = "RECHARGE")

    runBlocking {
      assertEquals(
          listOf("t1"),
          withTimeout(TIMEOUT_MS) { repository.observeHistory(uid).first() }.map { it.id },
      )
      seedTransaction("t2", -30)
      val history =
          withTimeout(TIMEOUT_MS) { repository.observeHistory(uid).first { it.size == 2 } }

      assertEquals(setOf("t1", "t2"), history.map { it.id }.toSet())
    }
  }

  private companion object {
    const val PASSWORD = "password123"
    const val TIMEOUT_MS = 10_000L
  }
}
