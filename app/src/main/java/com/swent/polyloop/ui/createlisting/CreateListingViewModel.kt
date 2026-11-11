// Made with Claude.

package com.swent.polyloop.ui.createlisting

import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import com.swent.polyloop.model.auth.AuthRepository
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingAddresses
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.model.listing.ListingRepository
import com.swent.polyloop.model.listing.ListingStatus
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State of the Create listing screen: the fields as typed, plus checks that follow the rules of
 * [Listing.canPublish].
 *
 * @property pricePerDay the price per day as typed; valid when it is a whole number of PP from 1 to
 *   [Listing.MAX_AMOUNT].
 * @property itemValue the item value as typed, shown as the suggested deposit; same rule as
 *   [pricePerDay].
 * @property photos the URIs of the photos taken, in order. Reordering photos (SCREENS.md) comes
 *   later.
 * @property hasAttemptedPublish true once the user pressed "Publish", so the screen can show the
 *   errors of empty fields from then on.
 * @property isPublishing true while the listing is being published; "Publish" is disabled then.
 * @property isDatePickerOpen true while the date picker for the availability window is shown.
 * @property isCameraOpen true while the in-app camera is shown; it stays open between photos so
 *   several can be taken in a row.
 * @property publishFailed true when publishing failed, or when nobody was signed in; the screen
 *   then shows a generic error text. It is reset when a new publish starts.
 * @property publishedListingId the new listing's id until the screen has opened it.
 */
data class CreateListingUiState(
    val title: String = "",
    val description: String = "",
    val category: ListingCategory? = null,
    val pricePerDay: String = "",
    val itemValue: String = "",
    val availableFrom: LocalDate? = null,
    val availableTo: LocalDate? = null,
    val pickupAddress: String = "",
    val dropOffAddress: String = "",
    val photos: List<String> = emptyList(),
    val hasAttemptedPublish: Boolean = false,
    val isPublishing: Boolean = false,
    val isDatePickerOpen: Boolean = false,
    val isCameraOpen: Boolean = false,
    val publishFailed: Boolean = false,
    val publishedListingId: String? = null,
) {
  val isTitleBlank: Boolean
    get() = title.isBlank()

  val isDescriptionBlank: Boolean
    get() = description.isBlank()

  val isCategoryMissing: Boolean
    get() = category == null

  val isPickupAddressBlank: Boolean
    get() = pickupAddress.isBlank()

  val isDropOffAddressBlank: Boolean
    get() = dropOffAddress.isBlank()

  /** How many photos are still needed to reach [Listing.MIN_PHOTOS], 0 if there are enough. */
  val missingPhotos: Int
    get() = (Listing.MIN_PHOTOS - photos.size).coerceAtLeast(0)

  /** True when the photo count is from [Listing.MIN_PHOTOS] to [Listing.MAX_PHOTOS]. */
  val hasValidPhotoCount: Boolean
    get() = photos.size in Listing.MIN_PHOTOS..Listing.MAX_PHOTOS

  val canAddPhoto: Boolean
    get() = photos.size < Listing.MAX_PHOTOS

  val isPricePerDayValid: Boolean
    get() = pricePerDay.isWholeNumberAboveZero()

  val isItemValueValid: Boolean
    get() = itemValue.isWholeNumberAboveZero()

  /**
   * True when both dates are set and the window ends after it starts, so it spans at least 2 days.
   */
  val isAvailabilityValid: Boolean
    get() = availableFrom != null && availableTo != null && availableFrom.isBefore(availableTo)

  /** True when "Publish" would succeed: every check passes and nothing is already running. */
  val canPublish: Boolean
    get() =
        !isPublishing &&
            !isTitleBlank &&
            !isDescriptionBlank &&
            !isCategoryMissing &&
            hasValidPhotoCount &&
            isPricePerDayValid &&
            isItemValueValid &&
            isAvailabilityValid &&
            !isPickupAddressBlank &&
            !isDropOffAddressBlank
}

/**
 * Holds the Create listing form while the user fills it in, and publishes the listing.
 *
 * The photo files are deleted through [deletePhoto] when a photo is removed, when the form is
 * closed without publishing, and after a successful publish; they are kept when publishing fails,
 * so the user can try again. Deleting touches the disk, so it runs on [fileScope], never on the
 * main thread.
 *
 * @param listingRepository where the listing is published.
 * @param authRepository where the signed-in owner is read.
 * @param deletePhoto deletes the photo file at a `file:` URI; production uses [deletePhotoFile].
 * @param fileScope the scope the photo deletions run in. It is the ViewModel's own, not
 *   [viewModelScope], because clearing the ViewModel cancels [viewModelScope] before [onCleared]
 *   runs, and the photos still in the form must be deleted there. It is never cancelled: each job
 *   is a few deletions and ends by itself. Production uses a [SupervisorJob] on [Dispatchers.IO];
 *   tests pass a scope on a test dispatcher to control when the deletions run.
 */
class CreateListingViewModel(
    private val listingRepository: ListingRepository,
    private val authRepository: AuthRepository,
    private val deletePhoto: (uri: String) -> Unit = ::deletePhotoFile,
    private val fileScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : ViewModel() {

  private val _uiState = MutableStateFlow(CreateListingUiState())
  val uiState: StateFlow<CreateListingUiState> = _uiState.asStateFlow()

  /** True once the listing is saved: its photos are already deleted. */
  private var published = false

  fun onTitleChange(title: String) = _uiState.update { it.copy(title = title) }

  fun onDescriptionChange(description: String) = _uiState.update {
    it.copy(description = description)
  }

  fun onCategoryChange(category: ListingCategory) = _uiState.update { it.copy(category = category) }

  fun onPricePerDayChange(pricePerDay: String) = _uiState.update {
    it.copy(pricePerDay = pricePerDay)
  }

  fun onItemValueChange(itemValue: String) = _uiState.update { it.copy(itemValue = itemValue) }

  fun onPickupAddressChange(pickupAddress: String) = _uiState.update {
    it.copy(pickupAddress = pickupAddress)
  }

  fun onDropOffAddressChange(dropOffAddress: String) = _uiState.update {
    it.copy(dropOffAddress = dropOffAddress)
  }

  /** Opens the date picker. */
  fun openDatePicker() = _uiState.update { it.copy(isDatePickerOpen = true) }

  /** Closes the date picker and keeps the dates already chosen. */
  fun dismissDatePicker() = _uiState.update { it.copy(isDatePickerOpen = false) }

  /** Sets the availability window to [start]..[end] and closes the date picker. */
  fun selectDates(start: LocalDate, end: LocalDate) = _uiState.update {
    it.copy(availableFrom = start, availableTo = end, isDatePickerOpen = false)
  }

  /**
   * Opens the in-app camera. Ignored once there are [Listing.MAX_PHOTOS] photos, and while a
   * publish is running.
   */
  fun openCamera() = _uiState.update {
    if (it.canAddPhoto && !it.isPublishing) it.copy(isCameraOpen = true) else it
  }

  /** Closes the in-app camera and keeps the photos already taken. */
  fun closeCamera() = _uiState.update { it.copy(isCameraOpen = false) }

  /**
   * Appends the photo taken at [uri] and keeps the camera open. Once there are [Listing.MAX_PHOTOS]
   * photos, or while a publish is running, the photo is not added and its file is deleted.
   *
   * @param uri the `file:` URI of the photo the camera just saved.
   */
  fun onPhotoTaken(uri: String) {
    if (_uiState.value.canAddPhoto && !_uiState.value.isPublishing)
        _uiState.update { it.copy(photos = it.photos + uri) }
    else deleteFiles(listOf(uri))
  }

  /**
   * Deletes the file prepared for a shot that failed, which was never added to the form.
   *
   * @param uri the `file:` URI the camera had prepared for the shot.
   */
  fun onCaptureFailed(uri: String) = deleteFiles(listOf(uri))

  /**
   * Removes the photo at [index] and deletes its file. Ignored when there is no photo there, and
   * while a publish is running.
   *
   * @param index the position of the photo in the photo row, starting at 0.
   */
  fun onRemovePhoto(index: Int) {
    if (_uiState.value.isPublishing) return
    val removed = _uiState.value.photos.getOrNull(index) ?: return
    _uiState.update { it.copy(photos = it.photos.filterIndexed { i, _ -> i != index }) }
    deleteFiles(listOf(removed))
  }

  /**
   * Marks the publish attempt, so the screen shows the errors of empty fields, then saves the
   * listing if every check passes (the photos are uploaded first). The title and description are
   * trimmed. On success it sets the new listing's id; on failure, the error. Ignored while a
   * publish is running.
   */
  fun onPublish() {
    if (_uiState.value.isPublishing) return
    val form = _uiState.value
    _uiState.update { it.copy(hasAttemptedPublish = true) }
    if (!form.canPublish) return
    val owner = authRepository.getCurrentUser()
    if (owner == null) {
      _uiState.update { it.copy(publishFailed = true) }
      return
    }
    val listing =
        Listing(
            id = "",
            ownerId = owner.uid,
            title = form.title.trim(),
            description = form.description.trim(),
            category = requireNotNull(form.category),
            photoUrls = emptyList(),
            pricePerDay = form.pricePerDay.toInt(),
            itemValue = form.itemValue.toInt(),
            availableFrom = requireNotNull(form.availableFrom),
            availableTo = requireNotNull(form.availableTo),
            status = ListingStatus.PUBLISHED,
        )
    val addresses = ListingAddresses(form.pickupAddress, form.dropOffAddress)
    _uiState.update { it.copy(isPublishing = true, publishFailed = false) }
    viewModelScope.launch {
      listingRepository
          .createListing(listing, addresses, form.photos.map(Uri::parse))
          .onSuccess { id ->
            published = true
            deleteFiles(form.photos)
            _uiState.update { it.copy(isPublishing = false, publishedListingId = id) }
          }
          .onFailure { _uiState.update { it.copy(isPublishing = false, publishFailed = true) } }
    }
  }

  /** Clears the new listing's id once the screen has opened it. */
  fun onPublishedHandled() = _uiState.update { it.copy(publishedListingId = null) }

  /** The form is closed: deletes the photos still in it, unless the listing was published. */
  override fun onCleared() {
    if (!published) deleteFiles(_uiState.value.photos)
  }

  /**
   * Deletes the photo files at [uris] on [fileScope], off the main thread. It also works from
   * [onCleared], where [viewModelScope] is already cancelled.
   *
   * @param uris the `file:` URIs of the photos to delete.
   */
  private fun deleteFiles(uris: List<String>) {
    fileScope.launch { uris.forEach(deletePhoto) }
  }

  /** Builds the ViewModel with its dependencies (manual constructor injection). */
  class Factory(
      private val listingRepository: ListingRepository,
      private val authRepository: AuthRepository,
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      require(modelClass.isAssignableFrom(CreateListingViewModel::class.java)) {
        "Unknown ViewModel class: ${modelClass.name}"
      }
      @Suppress("UNCHECKED_CAST")
      return CreateListingViewModel(listingRepository, authRepository) as T
    }
  }
}

/**
 * A [CreateListingViewModel] held by [store], so a test can clear it with `store.clear()` like the
 * navigation does, with the given repositories and photo deleter. Only tests use it: it exists once
 * here because `androidTest` cannot see the unit tests' folder.
 *
 * @param store the store that holds the ViewModel; clearing it calls the ViewModel's `onCleared`.
 * @param listingRepository the repository the ViewModel publishes to; tests pass a fake.
 * @param authRepository the repository the ViewModel reads the owner from; tests pass a fake.
 * @param fileScope where the deletions run; by default a scope on [Dispatchers.Unconfined], so a
 *   test sees them done as soon as the call returns. Tests that control the timing pass a scope on
 *   a test dispatcher.
 * @param deletePhoto how a photo file is deleted; tests pass a recorder or a fake.
 * @return the ViewModel stored in [store] (the same one if the store already holds it).
 */
@VisibleForTesting
internal fun storedCreateListingViewModel(
    store: ViewModelStore,
    listingRepository: ListingRepository,
    authRepository: AuthRepository,
    fileScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    deletePhoto: (uri: String) -> Unit = ::deletePhotoFile,
): CreateListingViewModel =
    ViewModelProvider(
        store,
        object : ViewModelProvider.Factory {
          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T =
              CreateListingViewModel(listingRepository, authRepository, deletePhoto, fileScope) as T
        },
    )[CreateListingViewModel::class.java]

/** Only the digits 0-9 ("007" is 7), from 1 to [Listing.MAX_AMOUNT]. */
internal fun String.isWholeNumberAboveZero(): Boolean =
    isNotEmpty() && all { it in '0'..'9' } && (toIntOrNull() ?: 0) in 1..Listing.MAX_AMOUNT
