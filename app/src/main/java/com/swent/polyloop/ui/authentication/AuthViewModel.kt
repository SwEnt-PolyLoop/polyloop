// Made with Claude.

package com.swent.polyloop.ui.authentication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swent.polyloop.model.RepositoryProvider
import com.swent.polyloop.model.auth.AuthError
import com.swent.polyloop.model.auth.AuthRepository
import com.swent.polyloop.model.auth.AuthRepositoryFirebase
import com.swent.polyloop.model.auth.AuthResult
import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.auth.EpflEmail
import com.swent.polyloop.model.auth.PasswordRule
import com.swent.polyloop.model.profile.ProfileRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
 * @property resendCooldownSeconds seconds left before "Resend email" can be pressed again; counts
 *   down from 60 after each verification email sent, 0 when it can be pressed.
 * @property resendCooldownEmail the normalized address the last verification email went to. The
 *   countdown only applies to it: going back to fix a mistyped address must not block the right
 *   one.
 * @property isAskingName true on the "What's your name?" step, reached after a verified log-in
 *   whose account has no name (it could not be saved at sign-up); the profile is created once the
 *   user types it.
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
    val resendCooldownSeconds: Int = 0,
    val resendCooldownEmail: String = "",
    val isAskingName: Boolean = false,
    val isSignedIn: Boolean = false,
) {
  /** Only checked when signing up, where the name is required. */
  val isNameBlank: Boolean
    get() = name.isBlank()

  val isEmailValid: Boolean
    get() = EpflEmail.isValid(email)

  val isPasswordBlank: Boolean
    get() = password.isBlank()

  /** The sign-up rules the password breaks, in display order. Logging in ignores them. */
  val failedPasswordRules: List<PasswordRule>
    get() = PasswordRule.failedRules(password)

  /** True when the password follows every [PasswordRule]; only signing up requires it. */
  val isPasswordStrong: Boolean
    get() = failedPasswordRules.isEmpty()

  /** True when the screen lists the broken password rules: signing up with a typed password. */
  val showPasswordRules: Boolean
    get() = mode == AuthMode.SIGN_UP && password.isNotEmpty()

  /**
   * True when the main button can be pressed: valid fields and nothing already running. Signing up
   * also needs a name and a password that follows every [PasswordRule].
   */
  val canSubmit: Boolean
    get() =
        !isLoading &&
            isEmailValid &&
            !isPasswordBlank &&
            (mode == AuthMode.LOG_IN || (!isNameBlank && isPasswordStrong))

  /** Seconds left before "Resend email" can be pressed for the current [email], 0 if none. */
  val resendSecondsLeft: Int
    get() = if (resendCooldownEmail == EpflEmail.normalize(email)) resendCooldownSeconds else 0

  /** True when "Resend email" can be pressed: nothing running and the countdown is over. */
  val canResend: Boolean
    get() = !isLoading && resendSecondsLeft == 0
}

/**
 * Logs in and signs up EPFL users, including the email verification step, and creates the profile
 * of a verified user the first time they log in.
 */
class AuthViewModel(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(AuthUiState())
  val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

  private var resendCooldownJob: Job? = null

  /** The signed-in user who still has to type their name before their profile is created. */
  private var userWithoutName: AuthUser? = null

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
    if (!state.canResend) return
    launchAction(onStart = { it.copy(isVerificationEmailResent = false) }) {
      when (val result = authRepository.resendVerificationEmail(state.email, state.password)) {
        is AuthResult.Success -> {
          _uiState.update { it.copy(isVerificationEmailResent = true) }
          startResendCooldown()
        }
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
   *
   * Ignored while a request runs, so its result cannot land on the form. The resend countdown keeps
   * running for the address it was sent to: going back must not be a way around it.
   *
   * @return true if the step was left, false if it was ignored.
   */
  fun backToForm(): Boolean {
    if (_uiState.value.isLoading) return false
    _uiState.update {
      it.copy(
          isAwaitingVerification = false,
          isVerificationEmailResent = false,
          error = null,
          hasAttemptedSubmit = false,
      )
    }
    return true
  }

  /** "Continue" on the "What's your name?" step. Only marks the attempt if the name is blank. */
  fun submitName() {
    _uiState.update { it.copy(hasAttemptedSubmit = true) }
    val state = _uiState.value
    val user = userWithoutName
    if (state.isLoading || state.isNameBlank || user == null) return
    launchAction { createProfile(user, state.name.trim()) }
  }

  private fun signIn() {
    val state = _uiState.value
    launchAction {
      when (val result = authRepository.signIn(state.email, state.password)) {
        is AuthResult.Success -> onVerifiedSignIn(result.data)
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
        is AuthResult.Success -> {
          _uiState.update { it.copy(isAwaitingVerification = true) }
          startResendCooldown()
        }
        is AuthResult.Failure -> {
          _uiState.update {
            it.copy(
                error = result.error,
                // In both cases the account exists, so the user still has to verify it.
                isAwaitingVerification = result.error in ACCOUNT_CREATED_ERRORS,
            )
          }
          // Only the name failed: the verification email was sent, so Resend waits too.
          if (result.error == AuthError.NAME_NOT_SAVED) startResendCooldown()
        }
      }
    }
  }

  /**
   * Creates the profile, or first asks for the name when the account has none. The typed name is
   * only saved in the profile, never in the account, so the profile is read first: a user who
   * already has one is let in without being asked again.
   */
  private suspend fun onVerifiedSignIn(user: AuthUser) {
    if (user.name.isNotBlank()) {
      createProfile(user, user.name)
      return
    }
    val profile = profileRepository.getProfile(user.uid)
    when {
      profile.isFailure -> onProfileFailure()
      profile.getOrNull() != null -> onSignedIn()
      else -> {
        userWithoutName = user
        _uiState.update {
          it.copy(isAskingName = true, isAwaitingVerification = false, hasAttemptedSubmit = false)
        }
      }
    }
  }

  /** Creates the profile if it is missing, then lets the user in. */
  private suspend fun createProfile(user: AuthUser, name: String) {
    val created = profileRepository.createProfileIfMissing(user.uid, user.email, name).isSuccess
    userWithoutName = null
    if (created) onSignedIn() else onProfileFailure()
  }

  private fun onSignedIn() {
    _uiState.update {
      it.copy(isSignedIn = true, isAwaitingVerification = false, isAskingName = false)
    }
  }

  /**
   * Signs the user out when the profile cannot be read or created, so the next launch comes back
   * here instead of opening the app without a profile.
   */
  private fun onProfileFailure() {
    authRepository.signOut()
    _uiState.update {
      it.copy(
          error = AuthError.PROFILE_NOT_CREATED,
          // The account exists and is verified: the way back in is to log in again.
          mode = AuthMode.LOG_IN,
          isAwaitingVerification = false,
          isAskingName = false,
          hasAttemptedSubmit = false,
      )
    }
  }

  /** Leaving on the "What's your name?" step signs out, so no account stays without a profile. */
  override fun onCleared() {
    if (userWithoutName != null) authRepository.signOut()
  }

  /**
   * Disables "Resend email" for [RESEND_COOLDOWN_SECONDS], counting down once per second.
   *
   * The countdown lives in memory only, so restarting the app resets it. That is intended: it only
   * prevents accidental repeated taps, while Firebase rate-limits verification emails on the server
   * (reported as [AuthError.TOO_MANY_REQUESTS]).
   */
  private fun startResendCooldown() {
    resendCooldownJob?.cancel()
    _uiState.update { it.copy(resendCooldownEmail = EpflEmail.normalize(it.email)) }
    resendCooldownJob = viewModelScope.launch {
      for (seconds in RESEND_COOLDOWN_SECONDS downTo 1) {
        _uiState.update { it.copy(resendCooldownSeconds = seconds) }
        delay(1_000)
      }
      _uiState.update { it.copy(resendCooldownSeconds = 0) }
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
  class Factory(
      private val authRepository: AuthRepository = AuthRepositoryFirebase(),
      private val profileRepository: ProfileRepository = RepositoryProvider.profileRepository,
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      require(modelClass.isAssignableFrom(AuthViewModel::class.java)) {
        "Unknown ViewModel class: ${modelClass.name}"
      }
      @Suppress("UNCHECKED_CAST")
      return AuthViewModel(authRepository, profileRepository) as T
    }
  }

  private companion object {
    /** How long "Resend email" stays disabled after a verification email was sent. */
    const val RESEND_COOLDOWN_SECONDS = 60

    /** Sign-up failures that still created the account and sent (or tried to send) the email. */
    val ACCOUNT_CREATED_ERRORS =
        setOf(AuthError.VERIFICATION_EMAIL_NOT_SENT, AuthError.NAME_NOT_SAVED)
  }
}
