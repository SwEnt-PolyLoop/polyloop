package com.swent.polyloop.model

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.swent.polyloop.utils.FirebaseEmulator
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertArrayEquals
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

  @Before
  fun setUp() {
    FirebaseEmulator.connect()
    // The Storage rules only let signed-in users read and write.
    runBlocking {
      Firebase.auth
          .createUserWithEmailAndPassword("test-${UUID.randomUUID()}@epfl.ch", PASSWORD)
          .await()
    }
  }

  @After
  fun tearDown() {
    runBlocking {
      runCatching { RepositoryProvider.storage.reference.child(path).delete().await() }
    }
    Firebase.auth.signOut()
  }

  @Test
  fun uploadedFileCanBeReadBack() {
    runBlocking {
      val bytes = "Hello PolyLoop".toByteArray()
      val ref = RepositoryProvider.storage.reference.child(path)

      ref.putBytes(bytes).await()
      val downloaded = ref.getBytes(MAX_BYTES).await()

      assertArrayEquals(bytes, downloaded)
    }
  }

  private companion object {
    const val PASSWORD = "password123"
    const val MAX_BYTES = 1024L
  }
}
