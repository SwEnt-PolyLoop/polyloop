// Made with Claude
package com.swent.polyloop.model

import android.content.Context
import android.net.ConnectivityManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.swent.polyloop.model.auth.AuthRepository
import com.swent.polyloop.model.auth.AuthRepositoryFirebase
import com.swent.polyloop.model.auth.AuthResult
import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.chat.ChatRepositoryFirestore
import com.swent.polyloop.model.chat.FakeChatRepository
import com.swent.polyloop.model.connectivity.ConnectivityRepositoryAndroid
import com.swent.polyloop.model.connectivity.FakeConnectivityRepository
import com.swent.polyloop.model.listing.ListingRepositoryFirestore
import com.swent.polyloop.model.profile.FakeProfileRepository
import com.swent.polyloop.model.profile.ProfileRepositoryFirestore
import com.swent.polyloop.model.rental.FakeRentalRepository
import com.swent.polyloop.model.rental.RentalRepositoryFirestore
import com.swent.polyloop.ui.listingdetail.FakeListingRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
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

  // The app context that init keeps; the real connectivity repository reads its
  // ConnectivityManager.
  private val appContext =
      mockk<Context> {
        every { getSystemService(ConnectivityManager::class.java) } returns mockk(relaxed = true)
      }
  private val storage: FirebaseStorage = mockk()

  @Before
  fun setUp() {
    // The real repositories and RepositoryProvider.storage get Firebase through
    // FirebaseAuth/FirebaseFirestore/FirebaseStorage.getInstance(), which needs an initialised
    // Firebase app, so those are stubbed statically.
    mockkStatic(FirebaseAuth::class, FirebaseFirestore::class, FirebaseStorage::class)
    every { FirebaseAuth.getInstance() } returns mockk(relaxed = true)
    every { FirebaseFirestore.getInstance() } returns mockk(relaxed = true)
    every { FirebaseStorage.getInstance() } returns storage
    // init is given an activity-like context and must keep its application context.
    RepositoryProvider.init(mockk { every { applicationContext } returns appContext })
    RepositoryProvider.reset()
  }

  @After
  fun tearDown() {
    RepositoryProvider.reset()
    RepositoryProvider.clearContext()
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

  // ---------- chatRepository ----------

  @Test
  fun chatRepositoryDefaultsToFirestoreImplementation() {
    assertTrue(RepositoryProvider.chatRepository is ChatRepositoryFirestore)
  }

  @Test
  fun chatRepositoryReturnsSameInstanceOnEveryAccess() {
    val first = RepositoryProvider.chatRepository

    assertSame(first, RepositoryProvider.chatRepository)
    verify(exactly = 1) { FirebaseFirestore.getInstance() }
  }

  @Test
  fun chatRepositoryReturnsAssignedRepository() {
    val fake = FakeChatRepository()

    RepositoryProvider.chatRepository = fake

    assertSame(fake, RepositoryProvider.chatRepository)
    verify(exactly = 0) { FirebaseFirestore.getInstance() }
    verify(exactly = 0) { FirebaseAuth.getInstance() }
  }

  // ---------- storage ----------

  @Test
  fun storageReturnsTheFirebaseStorageInstance() {
    assertSame(storage, RepositoryProvider.storage)
  }

  @Test
  fun accessingRepositoriesDoesNotTouchStorage() {
    RepositoryProvider.authRepository
    RepositoryProvider.listingRepository
    RepositoryProvider.profileRepository
    RepositoryProvider.chatRepository

    verify(exactly = 0) { FirebaseStorage.getInstance() }
  }

  // ---------- profileRepository ----------

  @Test
  fun profileRepositoryDefaultsToFirestoreImplementation() {
    assertTrue(RepositoryProvider.profileRepository is ProfileRepositoryFirestore)
  }

  @Test
  fun profileRepositoryReturnsSameInstanceOnEveryAccess() {
    val first = RepositoryProvider.profileRepository

    assertSame(first, RepositoryProvider.profileRepository)
    verify(exactly = 1) { FirebaseFirestore.getInstance() }
  }

  @Test
  fun profileRepositoryReturnsAssignedRepository() {
    val fake = FakeProfileRepository()

    RepositoryProvider.profileRepository = fake

    assertSame(fake, RepositoryProvider.profileRepository)
    verify(exactly = 0) { FirebaseFirestore.getInstance() }
  }

  // ---------- rentalRepository ----------

  @Test
  fun rentalRepositoryDefaultsToFirestoreImplementation() {
    assertTrue(RepositoryProvider.rentalRepository is RentalRepositoryFirestore)
  }

  @Test
  fun rentalRepositoryReturnsSameInstanceOnEveryAccess() {
    val first = RepositoryProvider.rentalRepository

    assertSame(first, RepositoryProvider.rentalRepository)
    verify(exactly = 1) { FirebaseFirestore.getInstance() }
  }

  @Test
  fun rentalRepositoryReturnsAssignedRepository() {
    val fake = FakeRentalRepository()

    RepositoryProvider.rentalRepository = fake

    assertSame(fake, RepositoryProvider.rentalRepository)
    verify(exactly = 0) { FirebaseFirestore.getInstance() }
  }

  // ---------- connectivityRepository ----------

  @Test
  fun connectivityRepositoryDefaultsToAndroidImplementation() {
    assertTrue(RepositoryProvider.connectivityRepository is ConnectivityRepositoryAndroid)
  }

  @Test
  fun connectivityRepositoryReturnsSameInstanceOnEveryAccess() {
    val first = RepositoryProvider.connectivityRepository

    assertSame(first, RepositoryProvider.connectivityRepository)
    verify(exactly = 1) { appContext.getSystemService(ConnectivityManager::class.java) }
  }

  @Test
  fun connectivityRepositoryReturnsAssignedRepository() {
    val fake = FakeConnectivityRepository()

    RepositoryProvider.connectivityRepository = fake

    assertSame(fake, RepositoryProvider.connectivityRepository)
    verify(exactly = 0) { appContext.getSystemService(ConnectivityManager::class.java) }
  }

  @Test
  fun connectivityRepositoryFailsClearlyWhenInitWasNotCalled() {
    RepositoryProvider.clearContext()

    val error =
        assertThrows(IllegalStateException::class.java) {
          RepositoryProvider.connectivityRepository
        }
    assertEquals("RepositoryProvider.init was not called", error.message)
  }

  // ---------- laziness and reset ----------

  @Test
  fun resetDoesNotCreateRepositories() {
    RepositoryProvider.reset()

    verify(exactly = 0) { FirebaseAuth.getInstance() }
    verify(exactly = 0) { FirebaseFirestore.getInstance() }
    verify(exactly = 0) { FirebaseStorage.getInstance() }
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
    val fakeChat = FakeChatRepository()
    RepositoryProvider.authRepository = fakeAuth
    RepositoryProvider.listingRepository = fakeListing
    RepositoryProvider.chatRepository = fakeChat
    RepositoryProvider.profileRepository = FakeProfileRepository()
    RepositoryProvider.rentalRepository = FakeRentalRepository()
    RepositoryProvider.connectivityRepository = FakeConnectivityRepository()

    RepositoryProvider.reset()

    assertTrue(RepositoryProvider.authRepository is AuthRepositoryFirebase)
    assertTrue(RepositoryProvider.listingRepository is ListingRepositoryFirestore)
    assertTrue(RepositoryProvider.rentalRepository is RentalRepositoryFirestore)
    assertTrue(RepositoryProvider.chatRepository is ChatRepositoryFirestore)
    assertTrue(RepositoryProvider.profileRepository is ProfileRepositoryFirestore)
    assertTrue(RepositoryProvider.connectivityRepository is ConnectivityRepositoryAndroid)
  }

  @Test
  fun resetCreatesNewInstancesOnNextAccess() {
    val firstAuth = RepositoryProvider.authRepository
    val firstListing = RepositoryProvider.listingRepository
    val firstChat = RepositoryProvider.chatRepository
    val firstProfile = RepositoryProvider.profileRepository
    val firstRental = RepositoryProvider.rentalRepository
    val firstConnectivity = RepositoryProvider.connectivityRepository

    RepositoryProvider.reset()

    assertNotSame(firstAuth, RepositoryProvider.authRepository)
    assertNotSame(firstListing, RepositoryProvider.listingRepository)
    assertNotSame(firstChat, RepositoryProvider.chatRepository)
    assertNotSame(firstProfile, RepositoryProvider.profileRepository)
    assertNotSame(firstRental, RepositoryProvider.rentalRepository)
    assertNotSame(firstConnectivity, RepositoryProvider.connectivityRepository)
  }
}
