// Made with Claude.

package com.swent.polyloop.ui.createlisting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.auth.FakeAuthRepository
import com.swent.polyloop.model.listing.ListingAddresses
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.model.listing.ListingStatus
import com.swent.polyloop.ui.listingdetail.FakeListingRepository
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Publishing, kept apart from [CreateListingViewModelTest]. It runs with Robolectric because
 * publishing turns the photo strings into `Uri`s, and `Uri.parse` returns null in a plain JVM test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CreateListingViewModelPublishTest {

  private val me = AuthUser("me", "me@epfl.ch", "Me")
  private val from = LocalDate.of(2026, 10, 6)
  private val to = LocalDate.of(2026, 10, 20)

  private lateinit var listingRepository: FakeListingRepository
  private lateinit var authRepository: FakeAuthRepository
  private lateinit var viewModel: CreateListingViewModel
  private val deleted = mutableListOf<String>()

  private val state: CreateListingUiState
    get() = viewModel.uiState.value

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    listingRepository = FakeListingRepository()
    authRepository = FakeAuthRepository(signedInUser = me)
    viewModel = CreateListingViewModel(listingRepository, authRepository, { deleted += it })
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun fillForm(vm: CreateListingViewModel = viewModel) {
    vm.onTitleChange("  Camping tent  ")
    vm.onDescriptionChange("  Light tent.  ")
    vm.onCategoryChange(ListingCategory.SPORTS_OUTDOOR)
    vm.onPricePerDayChange("15")
    vm.onItemValueChange("180")
    vm.selectDates(from, to)
    vm.onPickupAddressChange("Route Cantonale 1, Ecublens")
    vm.onDropOffAddressChange("Avenue Piccard 2, Lausanne")
    repeat(3) { vm.onPhotoTaken("file:///photo$it.jpg") }
  }

  private val photos = List(3) { "file:///photo$it.jpg" }

  /** A ViewModel held by [store], so the test can clear it like the navigation does. */
  private fun storedViewModel(store: ViewModelStore): CreateListingViewModel =
      ViewModelProvider(
          store,
          object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                CreateListingViewModel(listingRepository, authRepository, { deleted += it }) as T
          },
      )[CreateListingViewModel::class.java]

  @Test
  fun publishDeletesThePhotosOnlyAfterTheUploadSucceeded() {
    fillForm()
    val gate = CompletableDeferred<Unit>()
    listingRepository.gate = gate

    viewModel.onPublish()
    assertTrue(deleted.isEmpty())
    gate.complete(Unit)

    assertEquals(photos, deleted)
  }

  @Test
  fun publishFailureKeepsThePhotosSoTheUserCanRetry() {
    fillForm()
    listingRepository.failure = IOException("No connection")

    viewModel.onPublish()

    assertTrue(deleted.isEmpty())
    assertEquals(photos, state.photos)
  }

  @Test
  fun clearingAfterAPublishDeletesNothingMore() {
    val store = ViewModelStore()
    val vm = storedViewModel(store)
    fillForm(vm)
    vm.onPublish()
    assertEquals(photos, deleted)

    store.clear()

    assertEquals(photos, deleted)
  }

  @Test
  fun clearingAfterAFailedPublishDeletesThePhotos() {
    val store = ViewModelStore()
    val vm = storedViewModel(store)
    fillForm(vm)
    listingRepository.failure = IOException("No connection")
    vm.onPublish()

    store.clear()

    assertEquals(photos, deleted)
  }

  @Test
  fun clearingWithoutPublishingDeletesThePhotos() {
    val store = ViewModelStore()
    fillForm(storedViewModel(store))

    store.clear()

    assertEquals(photos, deleted)
  }

  @Test
  fun publishSavesTheListingWithTheTypedValuesAndSetsItsId() {
    fillForm()

    viewModel.onPublish()

    val saved = listingRepository.listings.single()
    assertEquals("me", saved.ownerId)
    assertEquals("Camping tent", saved.title)
    assertEquals("Light tent.", saved.description)
    assertEquals(ListingCategory.SPORTS_OUTDOOR, saved.category)
    assertEquals(15, saved.pricePerDay)
    assertEquals(180, saved.itemValue)
    assertEquals(from, saved.availableFrom)
    assertEquals(to, saved.availableTo)
    assertEquals(ListingStatus.PUBLISHED, saved.status)
    assertEquals(List(3) { "file:///photo$it.jpg" }, saved.photoUrls)
    assertEquals(
        ListingAddresses("Route Cantonale 1, Ecublens", "Avenue Piccard 2, Lausanne"),
        listingRepository.createdAddresses[saved.id],
    )
    assertEquals(saved.id, state.publishedListingId)
    assertFalse(state.isPublishing)
    assertFalse(state.publishFailed)
    assertTrue(state.hasAttemptedPublish)
  }

  @Test
  fun publishWithAnInvalidFormSavesNothing() {
    fillForm()
    viewModel.onTitleChange("")

    viewModel.onPublish()

    assertTrue(listingRepository.listings.isEmpty())
    assertTrue(state.hasAttemptedPublish)
    assertFalse(state.isPublishing)
    assertNull(state.publishedListingId)
    assertFalse(state.publishFailed)
  }

  @Test
  fun publishFailureWithAMessageSetsPublishFailed() {
    fillForm()
    listingRepository.failure = IOException("No connection")

    viewModel.onPublish()

    assertTrue(state.publishFailed)
    assertFalse(state.isPublishing)
    assertNull(state.publishedListingId)
  }

  @Test
  fun publishFailureWithoutAMessageSetsPublishFailed() {
    fillForm()
    listingRepository.failure = Exception()

    viewModel.onPublish()

    assertTrue(state.publishFailed)
    assertFalse(state.isPublishing)
  }

  @Test
  fun publishFailureThenRetryClearsPublishFailed() {
    fillForm()
    listingRepository.failure = IOException("No connection")
    viewModel.onPublish()
    assertTrue(state.publishFailed)

    listingRepository.failure = null
    viewModel.onPublish()

    assertFalse(state.publishFailed)
    assertEquals(1, listingRepository.listings.size)
    assertEquals(listingRepository.listings.single().id, state.publishedListingId)
  }

  @Test
  fun publishWithoutASignedInUserFailsWithoutSaving() {
    fillForm()
    authRepository.signedInUser = null

    viewModel.onPublish()

    assertTrue(state.publishFailed)
    assertFalse(state.isPublishing)
    assertTrue(listingRepository.listings.isEmpty())
  }

  @Test
  fun publishingShowsProgressAndASecondPressIsIgnored() {
    fillForm()
    val gate = CompletableDeferred<Unit>()
    listingRepository.gate = gate

    viewModel.onPublish()
    assertTrue(state.isPublishing)
    viewModel.onPublish()
    gate.complete(Unit)

    assertFalse(state.isPublishing)
    assertEquals(1, listingRepository.listings.size)
  }

  @Test
  fun onPublishedHandledClearsTheNewListingId() {
    fillForm()
    viewModel.onPublish()

    viewModel.onPublishedHandled()

    assertNull(state.publishedListingId)
  }

  @Test
  fun factoryBuildsTheViewModelAndRejectsOtherClasses() {
    val factory = CreateListingViewModel.Factory(listingRepository, authRepository)

    assertNotNull(factory.create(CreateListingViewModel::class.java))
    val rejected = runCatching { factory.create(OtherViewModel::class.java) }
    assertTrue(rejected.exceptionOrNull() is IllegalArgumentException)
  }

  private class OtherViewModel : ViewModel()
}
