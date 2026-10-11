// Made with Claude.

package com.swent.polyloop.model.profile

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeProfileRepositoryTest {

  private val john = UserProfile(uid = "u1", name = "John", email = "john@epfl.ch")
  private val failure = IllegalStateException("offline")

  // ---------- getProfile ----------

  @Test
  fun getProfileReturnsTheStoredProfile() = runTest {
    val repository = FakeProfileRepository(listOf(john))

    assertEquals(Result.success(john), repository.getProfile("u1"))
  }

  @Test
  fun getProfileReturnsNullForAUserWithoutProfile() = runTest {
    val repository = FakeProfileRepository(listOf(john))

    assertEquals(Result.success(null), repository.getProfile("someone-else"))
  }

  // ---------- createProfileIfMissing ----------

  @Test
  fun createProfileIfMissingStoresANewProfile() = runTest {
    val repository = FakeProfileRepository()

    val result = repository.createProfileIfMissing("u1", "john@epfl.ch", "John")

    assertEquals(Result.success(Unit), result)
    assertEquals(listOf(john), repository.profiles)
  }

  @Test
  fun createProfileIfMissingKeepsAnExistingProfile() = runTest {
    val rated = john.copy(averageRating = 4.5, reviewCount = 2)
    val repository = FakeProfileRepository(listOf(rated))

    repository.createProfileIfMissing("u1", "john@epfl.ch", "Johnny")

    assertEquals(listOf(rated), repository.profiles)
  }

  // ---------- updateProfile ----------

  @Test
  fun updateProfileChangesNameAndPhotoAndKeepsTheRest() = runTest {
    val rated = john.copy(averageRating = 4.5, reviewCount = 2)
    val repository = FakeProfileRepository(listOf(rated))

    val result = repository.updateProfile("u1", "Johnny", "https://example.com/john.jpg")

    assertEquals(Result.success(Unit), result)
    assertEquals(
        rated.copy(name = "Johnny", photoUrl = "https://example.com/john.jpg"),
        repository.getProfile("u1").getOrNull(),
    )
  }

  @Test
  fun updateProfileFailsWhenTheProfileIsMissing() = runTest {
    val repository = FakeProfileRepository()

    val result = repository.updateProfile("u1", "John", "")

    assertTrue(result.exceptionOrNull() is NoSuchElementException)
    assertTrue(repository.profiles.isEmpty())
  }

  // ---------- observeProfile ----------

  @Test
  fun observeProfileEmitsTheProfileAndEachChange() = runTest {
    val repository = FakeProfileRepository(listOf(john))
    val emitted = mutableListOf<UserProfile?>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      repository.observeProfile("u1").toList(emitted)
    }

    repository.updateProfile("u1", "Johnny", "")

    assertEquals(listOf(john, john.copy(name = "Johnny")), emitted)
  }

  @Test
  fun observeProfileEmitsNullUntilTheProfileIsCreated() = runTest {
    val repository = FakeProfileRepository()
    val emitted = mutableListOf<UserProfile?>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      repository.observeProfile("u1").toList(emitted)
    }

    repository.createProfileIfMissing("u1", "john@epfl.ch", "John")

    assertEquals(listOf(null, john), emitted)
  }

  @Test
  fun observeProfileIgnoresChangesToOtherProfiles() = runTest {
    val jane = UserProfile(uid = "u2", name = "Jane", email = "jane@epfl.ch")
    val repository = FakeProfileRepository(listOf(john, jane))
    val emitted = mutableListOf<UserProfile?>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      repository.observeProfile("u1").toList(emitted)
    }

    repository.updateProfile("u2", "Janet", "")

    assertEquals(listOf(john), emitted)
  }

  // ---------- failure and gate ----------

  @Test
  fun failureMakesEveryCallFail() = runTest {
    val repository = FakeProfileRepository(listOf(john), failure = failure)

    assertSame(failure, repository.getProfile("u1").exceptionOrNull())
    assertSame(failure, repository.createProfileIfMissing("u2", "a@epfl.ch", "A").exceptionOrNull())
    assertSame(failure, repository.updateProfile("u1", "John", "").exceptionOrNull())
    assertSame(failure, runCatching { repository.observeProfile("u1").first() }.exceptionOrNull())
    assertEquals(listOf(john), repository.profiles)
  }

  @Test
  fun gateHoldsCallsUntilItIsCompleted() = runTest {
    val gate = CompletableDeferred<Unit>()
    val repository = FakeProfileRepository(listOf(john), gate = gate)
    var result: Result<UserProfile?>? = null

    val call =
        launch(UnconfinedTestDispatcher(testScheduler)) { result = repository.getProfile("u1") }
    assertFalse(call.isCompleted)
    assertNull(result)

    gate.complete(Unit)
    call.join()

    assertEquals(Result.success(john), result)
  }
}
