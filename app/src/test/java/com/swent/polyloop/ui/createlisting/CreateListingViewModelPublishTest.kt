// Made with Claude.

package com.swent.polyloop.ui.createlisting

import androidx.lifecycle.ViewModel
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

  private val state: CreateListingUiState
    get() = viewModel.uiState.value

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    listingRepository = FakeListingRepository()
    authRepository = FakeAuthRepository(signedInUser = me)
    viewModel = CreateListingViewModel(listingRepository, authRepository)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun fillForm() {
    viewModel.onTitleChange("  Camping tent  ")
    viewModel.onDescriptionChange("  Light tent.  ")
    viewModel.onCategoryChange(ListingCategory.SPORTS_OUTDOOR)
    viewModel.onPricePerDayChange("15")
    viewModel.onItemValueChange("180")
    viewModel.selectDates(from, to)
    viewModel.onPickupAddressChange("Route Cantonale 1, Ecublens")
    viewModel.onDropOffAddressChange("Avenue Piccard 2, Lausanne")
    repeat(3) { viewModel.onPhotoTaken("file:///photo$it.jpg") }
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
    assertNull(state.publishError)
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
    assertNull(state.publishError)
  }

  @Test
  fun publishFailureShowsTheMessageAndAllowsAnotherTry() {
    fillForm()
    listingRepository.failure = IOException("No connection")

    viewModel.onPublish()

    assertEquals("No connection", state.publishError)
    assertFalse(state.isPublishing)
    assertNull(state.publishedListingId)

    listingRepository.failure = null
    viewModel.onPublish()

    assertNull(state.publishError)
    assertEquals(1, listingRepository.listings.size)
    assertEquals(listingRepository.listings.single().id, state.publishedListingId)
  }

  @Test
  fun publishFailureWithoutAMessageGivesABlankError() {
    fillForm()
    listingRepository.failure = Exception()

    viewModel.onPublish()

    assertEquals("", state.publishError)
  }

  @Test
  fun publishWithoutASignedInUserFailsWithoutSaving() {
    fillForm()
    authRepository.signedInUser = null

    viewModel.onPublish()

    assertEquals("", state.publishError)
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
