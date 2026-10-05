// Made with Claude.

package com.swent.polyloop.model.profile

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProfileRepositoryFirestoreTest {

  private lateinit var db: FirebaseFirestore
  private lateinit var document: DocumentReference
  private lateinit var repository: ProfileRepositoryFirestore

  @Before
  fun setUp() {
    db = mockk()
    document = mockk()
    val users = mockk<CollectionReference>()
    every { db.collection("users") } returns users
    every { users.document("uid123") } returns document
    every { document.set(any()) } returns Tasks.forResult(null)

    repository = ProfileRepositoryFirestore(db)
  }

  private fun profileExists(exists: Boolean) {
    val snapshot = mockk<DocumentSnapshot>()
    every { snapshot.exists() } returns exists
    every { document.get() } returns Tasks.forResult(snapshot)
  }

  private val unavailable =
      FirebaseFirestoreException("offline", FirebaseFirestoreException.Code.UNAVAILABLE)

  @Test
  fun createsProfileWithOnlyAppOwnedFieldsWhenMissing() = runTest {
    profileExists(false)

    val result = repository.createProfileIfMissing("uid123", "john@epfl.ch", "John")

    assertEquals(Result.success(Unit), result)
    verify { document.set(mapOf("name" to "John", "email" to "john@epfl.ch", "photoUrl" to "")) }
  }

  @Test
  fun doesNotWriteWhenProfileAlreadyExists() = runTest {
    profileExists(true)

    val result = repository.createProfileIfMissing("uid123", "john@epfl.ch", "John")

    assertEquals(Result.success(Unit), result)
    verify(exactly = 0) { document.set(any()) }
  }

  @Test
  fun failsWithCauseWhenProfileCannotBeRead() = runTest {
    every { document.get() } returns Tasks.forException(unavailable)

    val result = repository.createProfileIfMissing("uid123", "john@epfl.ch", "John")

    assertSame(unavailable, result.exceptionOrNull())
  }

  @Test
  fun failsWithCauseWhenProfileCannotBeWritten() = runTest {
    profileExists(false)
    every { document.set(any()) } returns Tasks.forException(unavailable)

    val result = repository.createProfileIfMissing("uid123", "john@epfl.ch", "John")

    assertSame(unavailable, result.exceptionOrNull())
  }

  @Test
  fun rethrowsCancellationInsteadOfReportingAFailure() = runTest {
    every { document.get() } returns Tasks.forCanceled()

    // A failure Result would be caught here as a success; only a thrown exception is an error.
    val thrown = runCatching {
      repository.createProfileIfMissing("uid123", "john@epfl.ch", "John")
    }
        .exceptionOrNull()

    assertTrue(thrown is CancellationException)
  }
}
