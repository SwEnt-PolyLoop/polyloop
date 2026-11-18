// Made with Claude.

package com.swent.polyloop.ui.navigation

import androidx.lifecycle.ViewModel
import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.auth.FakeAuthRepository
import com.swent.polyloop.model.profile.FakeProfileRepository
import com.swent.polyloop.model.profile.UserProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DrawerViewModelTest {

  private val me =
      UserProfile(
          uid = "me",
          name = "John",
          email = "john@epfl.ch",
          averageRating = 4.5,
          reviewCount = 2,
      )
  private val jane = UserProfile(uid = "jane", name = "Jane", email = "jane@epfl.ch")

  private lateinit var profiles: FakeProfileRepository
  private lateinit var auth: FakeAuthRepository

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    profiles = FakeProfileRepository(listOf(me, jane))
    auth = FakeAuthRepository(signedInUser = AuthUser(uid = "me", email = me.email, name = "John"))
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun viewModel() = DrawerViewModel(profiles, auth)

  @Test
  fun showsTheSignedInUsersProfile() {
    assertEquals(me, viewModel().uiState.value.profile)
  }

  @Test
  fun profileChangesShowWithoutRefreshing() = runTest {
    val vm = viewModel()

    profiles.updateProfile("me", "Johnny", "https://example.com/john.jpg")

    assertEquals(
        me.copy(name = "Johnny", photoUrl = "https://example.com/john.jpg"),
        vm.uiState.value.profile,
    )
  }

  @Test
  fun nobodySignedInShowsNoProfile() {
    auth.signedInUser = null

    assertNull(viewModel().uiState.value.profile)
  }

  @Test
  fun missingProfileShowsNoProfile() {
    profiles = FakeProfileRepository(listOf(jane))

    assertNull(viewModel().uiState.value.profile)
  }

  @Test
  fun refreshAfterSignInShowsTheNewUser() {
    auth.signedInUser = null
    val vm = viewModel()

    auth.signedInUser = AuthUser(uid = "jane", email = jane.email, name = "Jane")
    vm.refresh()

    assertEquals(jane, vm.uiState.value.profile)
  }

  @Test
  fun refreshAfterSignOutClearsTheProfile() {
    val vm = viewModel()

    auth.signedInUser = null
    vm.refresh()

    assertNull(vm.uiState.value.profile)
  }

  @Test
  fun refreshWithTheSameUserKeepsTheProfileWithoutListeningAgain() {
    val vm = viewModel()
    // A new listener would wait here before its first value, leaving the header empty.
    profiles.gate = CompletableDeferred()

    vm.refresh()

    assertEquals(me, vm.uiState.value.profile)
  }

  @Test
  fun errorShowsPlaceholdersAndRefreshListensAgain() {
    profiles.failure = IllegalStateException("No connection")
    val vm = viewModel()
    assertNull(vm.uiState.value.profile)

    profiles.failure = null
    vm.refresh()

    assertEquals(me, vm.uiState.value.profile)
  }

  @Test
  fun adminIsShownAsAdmin() {
    auth.admin = true

    assertTrue(viewModel().uiState.value.isAdmin)
  }

  @Test
  fun nonAdminIsNotShownAsAdmin() {
    assertFalse(viewModel().uiState.value.isAdmin)
  }

  @Test
  fun profileChangesKeepTheAdminFlag() = runTest {
    auth.admin = true
    val vm = viewModel()

    profiles.updateProfile("me", "Johnny", "")

    assertEquals("Johnny", vm.uiState.value.profile?.name)
    assertTrue(vm.uiState.value.isAdmin)
  }

  @Test
  fun refreshAfterSignOutClearsTheAdminFlag() {
    auth.admin = true
    val vm = viewModel()

    auth.signedInUser = null
    vm.refresh()

    assertFalse(vm.uiState.value.isAdmin)
  }

  @Test
  fun refreshAfterAnAdminSignsInReadsTheirClaim() {
    auth.signedInUser = null
    auth.admin = true
    val vm = viewModel()
    assertFalse(vm.uiState.value.isAdmin)

    auth.signedInUser = AuthUser(uid = "jane", email = jane.email, name = "Jane")
    vm.refresh()

    assertTrue(vm.uiState.value.isAdmin)
  }

  @Test
  fun signOutWhileTheClaimIsReadNeverShowsTheAdmin() {
    auth.admin = true
    auth.gate = CompletableDeferred()
    val vm = viewModel()
    // The header does not wait for the claim.
    assertEquals(me, vm.uiState.value.profile)

    auth.signedInUser = null
    vm.refresh()
    auth.gate?.complete(Unit)

    assertFalse(vm.uiState.value.isAdmin)
  }

  @Test
  fun factoryCreatesTheViewModel() {
    val created = DrawerViewModel.Factory(profiles, auth).create(DrawerViewModel::class.java)

    assertEquals(me, created.uiState.value.profile)
  }

  @Test
  fun factoryRejectsOtherViewModels() {
    class Other : ViewModel()

    assertThrows(IllegalArgumentException::class.java) {
      DrawerViewModel.Factory(profiles, auth).create(Other::class.java)
    }
  }
}
