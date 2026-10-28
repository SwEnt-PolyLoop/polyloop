// Made with Claude.

package com.swent.polyloop.ui.profile

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swent.polyloop.R
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.components.ProfileAvatar
import com.swent.polyloop.ui.createlisting.CameraPermissionMessage
import com.swent.polyloop.ui.createlisting.PolyLoopCamera

/**
 * The Edit profile screen, showing the form held by [viewModel].
 *
 * @param onSaved called once the profile is saved, to go back to the profile.
 */
@Composable
fun EditProfileScreen(
    viewModel: EditProfileViewModel,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  var cameraPermissionGranted by rememberSaveable { mutableStateOf<Boolean?>(null) }
  val cameraPermissionLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraPermissionGranted = granted
        if (granted) viewModel.openCamera()
      }
  EditProfileContent(
      uiState = uiState,
      onNameChange = viewModel::onNameChange,
      onTakePhoto = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
      onPhotoTaken = viewModel::onPhotoTaken,
      onCloseCamera = viewModel::closeCamera,
      onSave = viewModel::save,
      onRetry = viewModel::retry,
      onSaved = onSaved,
      cameraPermissionGranted = cameraPermissionGranted,
      modifier = modifier,
  )
}

/**
 * Stateless Edit profile content: the avatar with "Take photo", the name field and "Save". While
 * the profile loads it shows a spinner, and if it cannot be loaded, an error with "Retry". The new
 * photo, once taken, replaces the saved one in the avatar. While the camera is open, it fills the
 * whole screen in a full-screen dialog, like in Create listing; Back closes it.
 * [cameraPermissionGranted] is the answer to the last camera permission request, null if it was
 * never asked; a refusal shows how to allow the camera.
 */
@Composable
fun EditProfileContent(
    uiState: EditProfileUiState,
    onNameChange: (String) -> Unit,
    onTakePhoto: () -> Unit,
    onPhotoTaken: (uri: String) -> Unit,
    onCloseCamera: () -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    cameraPermissionGranted: Boolean? = null,
) {
  LaunchedEffect(uiState.isSaved) { if (uiState.isSaved) onSaved() }
  Box(
      modifier =
          modifier
              .fillMaxSize()
              .background(MaterialTheme.colorScheme.background)
              .testTag(C.Tag.edit_profile_screen)
  ) {
    when {
      uiState.isLoading ->
          CircularProgressIndicator(
              modifier = Modifier.align(Alignment.Center).testTag(C.Tag.edit_profile_loading)
          )
      uiState.loadFailed -> EditProfileLoadError(onRetry = onRetry)
      else ->
          EditProfileForm(
              uiState = uiState,
              onNameChange = onNameChange,
              onTakePhoto = onTakePhoto,
              onSave = onSave,
              cameraPermissionGranted = cameraPermissionGranted,
          )
    }
    if (uiState.isCameraOpen) {
      // Its own window, so the camera covers the whole screen, the top bar too. Back dismisses the
      // dialog, which closes the camera.
      Dialog(
          onDismissRequest = onCloseCamera,
          properties =
              DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
      ) {
        // One photo: the ViewModel closes the camera as soon as it is taken.
        PolyLoopCamera(
            photoCount = 0,
            maxPhotos = 1,
            onPhotoTaken = onPhotoTaken,
            onClose = onCloseCamera,
        )
      }
    }
  }
}

@Composable
private fun EditProfileLoadError(onRetry: () -> Unit) {
  Column(
      modifier = Modifier.fillMaxSize().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
        text = stringResource(R.string.edit_profile_load_error),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.testTag(C.Tag.edit_profile_load_error),
    )
    Button(onClick = onRetry, modifier = Modifier.testTag(C.Tag.edit_profile_retry_button)) {
      Text(stringResource(R.string.profile_retry))
    }
  }
}

/** The loaded form; everything is disabled while saving. */
@Composable
private fun EditProfileForm(
    uiState: EditProfileUiState,
    onNameChange: (String) -> Unit,
    onTakePhoto: () -> Unit,
    onSave: () -> Unit,
    cameraPermissionGranted: Boolean?,
) {
  val enabled = !uiState.isSaving
  Column(
      modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    ProfileAvatar(
        photo = uiState.newPhotoUri ?: uiState.photoUrl,
        name = uiState.name,
        size = 120.dp,
    )
    OutlinedButton(
        onClick = onTakePhoto,
        enabled = enabled,
        modifier = Modifier.testTag(C.Tag.edit_profile_take_photo_button),
    ) {
      Text(stringResource(R.string.edit_profile_take_photo))
    }
    CameraPermissionMessage(cameraPermissionGranted)
    OutlinedTextField(
        value = uiState.name,
        onValueChange = onNameChange,
        label = { Text(stringResource(R.string.edit_profile_name_label)) },
        singleLine = true,
        enabled = enabled,
        isError = uiState.nameError,
        supportingText =
            if (uiState.nameError) {
              {
                Text(
                    text = stringResource(R.string.edit_profile_name_required),
                    modifier = Modifier.testTag(C.Tag.edit_profile_name_error),
                )
              }
            } else null,
        modifier = Modifier.fillMaxWidth().testTag(C.Tag.edit_profile_name_field),
    )
    if (uiState.saveFailed) {
      Text(
          text = stringResource(R.string.edit_profile_save_error),
          color = MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.bodyMedium,
          textAlign = TextAlign.Center,
          modifier = Modifier.testTag(C.Tag.edit_profile_save_error),
      )
    }
    Button(
        onClick = onSave,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().testTag(C.Tag.edit_profile_save_button),
    ) {
      if (uiState.isSaving) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp).testTag(C.Tag.edit_profile_saving),
            strokeWidth = 2.dp,
        )
      } else {
        Text(stringResource(R.string.edit_profile_save))
      }
    }
  }
}
