// Made with Claude.

package com.swent.polyloop.model.profile

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import com.swent.polyloop.utils.FirebaseEmulator
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the real [ProfileRepositoryFirestore] against the Firestore emulator (not mocks), so these
 * tests see how Firestore really behaves. Needs `firebase emulators:start`.
 */
@RunWith(AndroidJUnit4::class)
class ProfileRepositoryFirestoreEmulatorTest {

  // Created here (not in setUp): creating it does not contact Firestore yet, so connect() can still
  // switch it to the emulator.
  private val repository = ProfileRepositoryFirestore()

  // A new signed-in user for each test, so documents left by earlier runs never get in the way.
  // The rules let a user write only their own profile.
  private val email = "profile-${UUID.randomUUID()}@epfl.ch"
  private lateinit var uid: String

  @Before
  fun setUp() {
    FirebaseEmulator.connect()
    uid = runBlocking { FirebaseEmulator.signUpVerified(email) }
  }

  @After
  fun tearDown() {
    Firebase.auth.signOut()
  }

  /** Writes a profile document as it is, like the seeded profiles, bypassing the repository. */
  private fun writeRawProfile(uid: String, fields: Map<String, Any>) {
    FirebaseEmulator.createDocumentAsAdmin("users/$uid", fields)
  }

  @Test
  fun getProfileReadsAProfileCreatedOnSignIn() {
    runBlocking {
      repository.createProfileIfMissing(uid, email, "John").getOrThrow()

      val profile = repository.getProfile(uid).getOrThrow()

      assertEquals(UserProfile(uid = uid, name = "John", email = email), profile)
    }
  }

  @Test
  fun getProfileReturnsNullWhenThereIsNoProfile() {
    runBlocking { assertNull(repository.getProfile("test-${UUID.randomUUID()}").getOrThrow()) }
  }

  @Test
  fun getProfileKeepsAProfileWithoutRatingFields() {
    runBlocking {
      writeRawProfile(uid, mapOf("name" to "Vida", "photoUrl" to "https://example.com/vida.jpg"))

      val profile = repository.getProfile(uid).getOrThrow()

      assertEquals(UserProfile(uid, "Vida", "", "https://example.com/vida.jpg", 0.0, 0), profile)
    }
  }

  @Test
  fun updateProfileChangesNameAndPhotoAndKeepsTheRest() {
    runBlocking {
      writeRawProfile(
          uid,
          mapOf("name" to "John", "email" to email, "averageRating" to 4.5, "reviewCount" to 2),
      )

      repository.updateProfile(uid, "Johnny", "https://example.com/john.jpg").getOrThrow()

      assertEquals(
          UserProfile(uid, "Johnny", email, "https://example.com/john.jpg", 4.5, 2),
          repository.getProfile(uid).getOrThrow(),
      )
    }
  }

  @Test
  fun updateProfileFailsWhenThereIsNoProfile() {
    runBlocking {
      val error = repository.updateProfile(uid, "John", "").exceptionOrNull()

      // PERMISSION_DENIED rather than NOT_FOUND: the rules refuse an update with no profile.
      assertEquals(
          FirebaseFirestoreException.Code.PERMISSION_DENIED,
          (error as FirebaseFirestoreException).code,
      )
      assertNull(repository.getProfile(uid).getOrThrow())
    }
  }

  @Test
  fun observeProfileEmitsTheProfileAgainAfterAnUpdate() {
    runBlocking {
      writeRawProfile(uid, mapOf("name" to "John", "email" to email))
      val emitted = mutableListOf<UserProfile?>()
      val firstReceived = CompletableDeferred<Unit>()

      val collector = launch {
        repository.observeProfile(uid).collect {
          emitted += it
          firstReceived.complete(Unit)
        }
      }
      withTimeout(TIMEOUT_MS) { firstReceived.await() }
      repository.updateProfile(uid, "Johnny", "").getOrThrow()
      withTimeout(TIMEOUT_MS) { while (emitted.size < 2) delay(50) }
      collector.cancel()

      assertEquals(listOf("John", "Johnny"), emitted.map { it?.name })
    }
  }

  private companion object {
    const val TIMEOUT_MS = 10_000L
  }
}
