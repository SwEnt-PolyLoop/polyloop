// Made with Claude.

package com.swent.polyloop.ui.authentication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swent.polyloop.model.auth.AuthError
import com.swent.polyloop.model.auth.AuthRepository
import com.swent.polyloop.model.auth.AuthRepositoryFirebase
import com.swent.polyloop.model.auth.AuthResult
import com.swent.polyloop.model.auth.EpflEmail
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which form the Sign-up / login screen shows. */
enum class AuthMode {
  LOG_IN,
  SIGN_UP,
}

/**
 * State of the Sign-up / login screen.
 *
 * @property hasAttemptedSubmit true once the user pressed the main button, so the screen can show
 *   the errors of empty or invalid fields from then on.
 * @property error the reason the last action failed; the screen turns it into a message.
 * @property isAwaitingVerification true on the "Check your inbox" step, reached after a sign-up
 *   that created the account, or a log-in with an EPFL email that is not verified yet.
 * @property isVerificationEmailResent true once "Resend email" succeeded.
 * @property isSignedIn true once a verified user is signed in; the app then leaves this screen.
 */
data class AuthUiState(
    val mode: AuthMode = AuthMode.LOG_IN,
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val hasAttemptedSubmit: Boolean = false,
    val isLoading: Boolean = false,
    val error: AuthError? = null,
    val isAwaitingVerification: Boolean = false,
    val isVerificationEmailResent: Boolean = false,
    val isSignedIn: Boolean = false,
) {
  /** Only checked when signing up, where the name is required. */
  val isNameBlank: Boolean
    get() = name.isBlank()

  val isEmailValid: Boolean
    get() = EpflEmail.isValid(email)

  val isPasswordBlank: Boolean
    get() = password.isBlank()

  /** True when the main button can be pressed: valid fields and nothing already running. */
  val canSubmit: Boolean
    get() =
        !isLoading && isEmailValid && !isPasswordBlank && (mode == AuthMode.LOG_IN || !isNameBlank)
}

/** Logs in and signs up EPFL users, including the email verification step. */
class AuthViewModel(private val authRepository: AuthRepository) : ViewModel() {

  private val _uiState = MutableStateFlow(AuthUiState())
  val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

  fun onNameChange(name: String) = _uiState.update { it.copy(name = name, error = null) }

  fun onEmailChange(email: String) = _uiState.update { it.copy(email = email, error = null) }

  fun onPasswordChange(password: String) = _uiState.update {
    it.copy(password = password, error = null)
  }

  /** Switches between the log-in and sign-up forms, keeping what was typed. */
  fun switchMode(mode: AuthMode) = _uiState.update {
    it.copy(mode = mode, error = null, hasAttemptedSubmit = false)
  }

  /** Logs in or signs up, depending on the mode. Only marks the attempt if a field is invalid. */
  fun submit() {
    _uiState.update { it.copy(hasAttemptedSubmit = true) }
    val state = _uiState.value
    if (!state.canSubmit) return
    when (state.mode) {
      AuthMode.LOG_IN -> signIn()
      AuthMode.SIGN_UP -> signUp()
    }
  }

  /** "Resend email" on the "Check your inbox" step. */
  fun resendVerificationEmail() {
    val state = _uiState.value
    if (state.isLoading) return
    launchAction(onStart = { it.copy(isVerificationEmailResent = false) }) {
      when (val result = authRepository.resendVerificationEmail(state.email, state.password)) {
        is AuthResult.Success -> _uiState.update { it.copy(isVerificationEmailResent = true) }
        is AuthResult.Failure -> _uiState.update { it.copy(error = result.error) }
      }
    }
  }

  /** "I've verified" on the "Check your inbox" step: tries to log in again. */
  fun confirmVerified() {
    if (_uiState.value.isLoading) return
    signIn()
  }

  /**
   * Leaves "Check your inbox" for the form it came from, keeping what was typed, e.g. when the
   * email had a typo (a valid EPFL address, but not the user's) and the verification email never
   * arrives.
   */
  fun backToForm() = _uiState.update {
    it.copy(
        isAwaitingVerification = false,
        isVerificationEmailResent = false,
        error = null,
        hasAttemptedSubmit = false,
    )
  }

  private fun signIn() {
    val state = _uiState.value
    launchAction {
      when (val result = authRepository.signIn(state.email, state.password)) {
        is AuthResult.Success ->
            _uiState.update { it.copy(isSignedIn = true, isAwaitingVerification = false) }
        is AuthResult.Failure ->
            _uiState.update {
              it.copy(
                  error = result.error,
                  // Only an EPFL address can wait for its verification email.
                  isAwaitingVerification =
                      it.isAwaitingVerification ||
                          (result.error == AuthError.EMAIL_NOT_VERIFIED && it.isEmailValid),
              )
            }
      }
    }
  }

  private fun signUp() {
    val state = _uiState.value
    launchAction {
      when (val result = authRepository.signUp(state.name, state.email, state.password)) {
        is AuthResult.Success -> _uiState.update { it.copy(isAwaitingVerification = true) }
        is AuthResult.Failure ->
            _uiState.update {
              it.copy(
                  error = result.error,
                  // In both cases the account exists, so the user still has to verify it.
                  isAwaitingVerification = result.error in ACCOUNT_CREATED_ERRORS,
              )
            }
      }
    }
  }

  /**
   * Runs [block] with the loading flag set, after clearing the previous error. The repository
   * reports failures as [AuthResult.Failure], but an exception it lets through is shown as
   * [AuthError.UNKNOWN] instead of crashing the app.
   */
  private fun launchAction(
      onStart: (AuthUiState) -> AuthUiState = { it },
      block: suspend () -> Unit,
  ) {
    _uiState.update { onStart(it.copy(isLoading = true, error = null)) }
    viewModelScope.launch {
      try {
        block()
      } catch (e: CancellationException) {
        // Cancellation is not a failure: the screen is gone and nobody is waiting.
        throw e
      } catch (e: Exception) {
        _uiState.update { it.copy(error = AuthError.UNKNOWN) }
      } finally {
        _uiState.update { it.copy(isLoading = false) }
      }
    }
  }

  /** Builds the ViewModel with its dependencies (manual constructor injection). */
  class Factory(private val authRepository: AuthRepository = AuthRepositoryFirebase()) :
      ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      require(modelClass.isAssignableFrom(AuthViewModel::class.java)) {
        "Unknown ViewModel class: ${modelClass.name}"
      }
      @Suppress("UNCHECKED_CAST")
      return AuthViewModel(authRepository) as T
    }
  }

  private companion object {
    /** Sign-up failures that still created the account and sent (or tried to send) the email. */
    val ACCOUNT_CREATED_ERRORS =
        setOf(AuthError.VERIFICATION_EMAIL_NOT_SENT, AuthError.NAME_NOT_SAVED)
  }
}
