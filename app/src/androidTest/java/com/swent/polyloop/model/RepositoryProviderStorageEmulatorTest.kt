package com.swent.polyloop.model

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.storage.StorageException
import com.swent.polyloop.utils.FirebaseEmulator
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Uploads a file through [RepositoryProvider.storage] to the Firebase Storage emulator and reads it
 * back, to check that Storage is wired up. Needs `firebase emulators:start`.
 */
@RunWith(AndroidJUnit4::class)
class RepositoryProviderStorageEmulatorTest {

  private val path = "test/${UUID.randomUUID()}.txt"
  private val bytes = "Hello PolyLoop".toByteArray()

  @Before
  fun setUp() {
    FirebaseEmulator.connect()
  }

  @After
  fun tearDown() {
    runBlocking {
      runCatching { RepositoryProvider.storage.reference.child(path).delete().await() }
    }
    Firebase.auth.signOut()
  }

  /** Signs in a new EPFL user, marking the email as verified if [verified] is true. */
  private suspend fun signIn(verified: Boolean) {
    val email = "test-${UUID.randomUUID()}@epfl.ch"
    val user = Firebase.auth.createUserWithEmailAndPassword(email, PASSWORD).await().user!!
    if (!verified) return
    user.sendEmailVerification().await()
    FirebaseEmulator.verifyEmail(email)
    // Refresh the ID token so Storage sees email_verified = true.
    user.reload().await()
    user.getIdToken(true).await()
  }

  @Test
  fun uploadedFileCanBeReadBack() {
    runBlocking {
      signIn(verified = true)
      val ref = RepositoryProvider.storage.reference.child(path)

      ref.putBytes(bytes).await()
      val downloaded = ref.getBytes(MAX_BYTES).await()

      assertArrayEquals(bytes, downloaded)
    }
  }

  @Test
  fun unverifiedUserCannotUpload() {
    runBlocking {
      signIn(verified = false)

      try {
        RepositoryProvider.storage.reference.child(path).putBytes(bytes).await()
        fail("The upload should have been refused")
      } catch (e: StorageException) {
        assertEquals(StorageException.ERROR_NOT_AUTHORIZED, e.errorCode)
      }
    }
  }

  private companion object {
    const val PASSWORD = "password123"
    const val MAX_BYTES = 1024L
  }
}
