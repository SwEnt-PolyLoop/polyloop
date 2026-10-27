// Made with Claude.

package com.swent.polyloop.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swent.polyloop.model.auth.AuthRepository
import com.swent.polyloop.model.profile.ProfileRepository
import com.swent.polyloop.model.profile.UserProfile
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * State of the navigation drawer's header.
 *
 * @property profile the signed-in user's profile; null when nobody is signed in, while it loads, or
 *   when the user has no profile. The header then shows placeholders.
 */
data class DrawerUiState(val profile: UserProfile? = null)

/**
 * Follows the signed-in user's profile for the drawer header (avatar, name and rating). Shared by
 * every screen, so it lives as long as the app; [refresh] switches to the user signed in now.
 */
class DrawerViewModel(
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(DrawerUiState())
  val uiState: StateFlow<DrawerUiState> = _uiState.asStateFlow()

  private var observedUid: String? = null
  private var observing: Job? = null

  init {
    refresh()
  }

  /**
   * Follows the profile of the user signed in now. Call it when the user may have changed (after a
   * sign-in or a sign-out); it does nothing while that user's profile is already followed.
   */
  fun refresh() {
    val uid = authRepository.getCurrentUser()?.uid
    if (uid == observedUid && (uid == null || observing?.isActive == true)) return
    observing?.cancel()
    observedUid = uid
    _uiState.value = DrawerUiState()
    if (uid == null) return
    observing = viewModelScope.launch {
      profileRepository
          .observeProfile(uid)
          // The header falls back to its placeholders; the next refresh listens again.
          .catch { emit(null) }
          .collect { profile -> _uiState.value = DrawerUiState(profile) }
    }
  }

  /** Builds the ViewModel with its dependencies (manual constructor injection). */
  class Factory(
      private val profileRepository: ProfileRepository,
      private val authRepository: AuthRepository,
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      require(modelClass.isAssignableFrom(DrawerViewModel::class.java)) {
        "Unknown ViewModel class: ${modelClass.name}"
      }
      @Suppress("UNCHECKED_CAST")
      return DrawerViewModel(profileRepository, authRepository) as T
    }
  }
}
