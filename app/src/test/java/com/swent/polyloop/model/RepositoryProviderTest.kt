// Made with Claude
package com.swent.polyloop.model

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.swent.polyloop.model.auth.AuthRepository
import com.swent.polyloop.model.auth.AuthRepositoryFirebase
import com.swent.polyloop.model.auth.AuthResult
import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.listing.ListingRepositoryFirestore
import com.swent.polyloop.ui.listingdetail.FakeListingRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RepositoryProviderTest {

  /** Minimal [AuthRepository] to check that an assigned repository is returned as is. */
  private class FakeAuthRepository : AuthRepository {
    override suspend fun signUp(name: String, email: String, password: String) =
        AuthResult.Success(Unit)

    override suspend fun signIn(email: String, password: String): AuthResult<AuthUser> =
        AuthResult.Success(AuthUser("uid", email, "Name"))

    override suspend fun resendVerificationEmail(email: String, password: String) =
        AuthResult.Success(Unit)

    override fun getCurrentUser(): AuthUser? = null

    override fun signOut() = Unit
  }

  @Before
  fun setUp() {
    // The real repositories get Firebase through FirebaseAuth/FirebaseFirestore.getInstance(),
    // which needs an initialised Firebase app, so those are stubbed statically.
    mockkStatic(FirebaseAuth::class, FirebaseFirestore::class)
    every { FirebaseAuth.getInstance() } returns mockk(relaxed = true)
    every { FirebaseFirestore.getInstance() } returns mockk(relaxed = true)
    RepositoryProvider.reset()
  }

  @After
  fun tearDown() {
    RepositoryProvider.reset()
    unmockkAll()
  }

  // ---------- authRepository ----------

  @Test
  fun authRepositoryDefaultsToFirebaseImplementation() {
    assertTrue(RepositoryProvider.authRepository is AuthRepositoryFirebase)
  }

  @Test
  fun authRepositoryReturnsSameInstanceOnEveryAccess() {
    val first = RepositoryProvider.authRepository

    assertSame(first, RepositoryProvider.authRepository)
    verify(exactly = 1) { FirebaseAuth.getInstance() }
  }

  @Test
  fun authRepositoryReturnsAssignedRepository() {
    val fake = FakeAuthRepository()

    RepositoryProvider.authRepository = fake

    assertSame(fake, RepositoryProvider.authRepository)
    verify(exactly = 0) { FirebaseAuth.getInstance() }
  }

  // ---------- listingRepository ----------

  @Test
  fun listingRepositoryDefaultsToFirestoreImplementation() {
    assertTrue(RepositoryProvider.listingRepository is ListingRepositoryFirestore)
  }

  @Test
  fun listingRepositoryReturnsSameInstanceOnEveryAccess() {
    val first = RepositoryProvider.listingRepository

    assertSame(first, RepositoryProvider.listingRepository)
    verify(exactly = 1) { FirebaseFirestore.getInstance() }
  }

  @Test
  fun listingRepositoryReturnsAssignedRepository() {
    val fake = FakeListingRepository()

    RepositoryProvider.listingRepository = fake

    assertSame(fake, RepositoryProvider.listingRepository)
    verify(exactly = 0) { FirebaseFirestore.getInstance() }
  }

  // ---------- laziness and reset ----------

  @Test
  fun resetDoesNotCreateRepositories() {
    RepositoryProvider.reset()

    verify(exactly = 0) { FirebaseAuth.getInstance() }
    verify(exactly = 0) { FirebaseFirestore.getInstance() }
  }

  @Test
  fun accessingListingRepositoryDoesNotCreateAuthRepository() {
    RepositoryProvider.listingRepository

    verify(exactly = 0) { FirebaseAuth.getInstance() }
  }

  @Test
  fun resetForgetsAssignedRepositories() {
    val fakeAuth = FakeAuthRepository()
    val fakeListing = FakeListingRepository()
    RepositoryProvider.authRepository = fakeAuth
    RepositoryProvider.listingRepository = fakeListing

    RepositoryProvider.reset()

    assertTrue(RepositoryProvider.authRepository is AuthRepositoryFirebase)
    assertTrue(RepositoryProvider.listingRepository is ListingRepositoryFirestore)
  }

  @Test
  fun resetCreatesNewInstancesOnNextAccess() {
    val firstAuth = RepositoryProvider.authRepository
    val firstListing = RepositoryProvider.listingRepository

    RepositoryProvider.reset()

    assertNotSame(firstAuth, RepositoryProvider.authRepository)
    assertNotSame(firstListing, RepositoryProvider.listingRepository)
  }
}
