// Made with Claude.

package com.swent.polyloop.ui.createlisting

import android.content.Context
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview as ComposePreview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.swent.polyloop.R
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.resources.C
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The in-app camera: a live [CameraPreview] with a [CameraOverlay] (shutter, close button, count
 * "[photoCount] / [maxPhotos]") on top, filling its window. The caller shows it in a full-screen
 * dialog, which also handles the system Back button.
 *
 * Each shot is saved as a JPEG in `filesDir/listing_photos/` and its file Uri is passed to
 * [onPhotoTaken]. The camera stays open after each shot, so several photos can be taken in a row.
 * It closes through [onClose] with the close button, or by itself once [photoCount] reaches
 * [maxPhotos] (10 for a listing). With [maxPhotos] 1, e.g. for a profile photo, the count is
 * hidden. A camera or capture error shows a short message and keeps the camera open.
 *
 * When the camera opens, photo files older than a day are deleted in the background, except the
 * ones in [keepPhotos], the photos the caller's form still holds (`file:` URIs).
 *
 * This is the only source of listing and profile photos: no gallery picker and no system camera
 * app.
 *
 * @param photoCount how many photos the caller's form holds, shown as the count and used to close
 *   the camera at [maxPhotos].
 * @param onPhotoTaken called with the `file:` URI of each photo saved.
 * @param onClose called when the camera should close.
 * @param modifier the modifier of the camera.
 * @param maxPhotos how many photos fit: 10 for a listing, 1 for a profile photo.
 * @param keepPhotos the photos the caller's form still holds, as `file:` URIs; the cleanup of old
 *   photo files made when the camera opens never deletes them.
 * @param onCaptureFailed called with the `file:` URI prepared for a shot that then failed, so the
 *   caller deletes the leftover file; the camera itself deletes nothing.
 */
@Composable
fun PolyLoopCamera(
    photoCount: Int,
    onPhotoTaken: (uri: String) -> Unit,
    onClose: () -> Unit,
    onCaptureFailed: (uri: String) -> Unit,
    modifier: Modifier = Modifier,
    maxPhotos: Int = Listing.MAX_PHOTOS,
    keepPhotos: List<String> = emptyList(),
) {
  val context = LocalContext.current
  val imageCapture = remember {
    ImageCapture.Builder()
        .setResolutionSelector(photoResolutionSelector())
        .setJpegQuality(PHOTO_JPEG_QUALITY)
        .build()
  }
  PolyLoopCameraContent(
      photoCount = photoCount,
      onPhotoTaken = onPhotoTaken,
      onClose = onClose,
      modifier = modifier,
      maxPhotos = maxPhotos,
      keepPhotos = keepPhotos,
      onCaptureFailed = onCaptureFailed,
      preview = { onBindError, previewModifier ->
        CameraPreview(imageCapture, onBindError, previewModifier)
      },
      capture = { file, onSaved, onError ->
        capturePhoto(context, imageCapture, file, onSaved, onError)
      },
  )
}

/**
 * The state and shutter flow of [PolyLoopCamera], without CameraX: [preview] shows the live image
 * and calls its `onBindError` when the camera cannot start, and [capture] saves a shot into the
 * given file then calls `onSaved` or `onError`. Tests pass fakes for both.
 *
 * @param photoCount how many photos the caller's form holds, shown as the count and used to close
 *   the camera at [maxPhotos].
 * @param onPhotoTaken called with the `file:` URI of each photo saved.
 * @param onClose called when the camera should close.
 * @param preview shows the live image; it calls `onBindError` when the camera cannot start.
 * @param capture saves a shot into the given file, then calls `onSaved` or `onError`.
 * @param modifier the modifier of the camera.
 * @param maxPhotos how many photos fit: 10 for a listing, 1 for a profile photo.
 * @param keepPhotos the photos the caller's form still holds, as `file:` URIs; the cleanup of old
 *   photo files made when the camera opens never deletes them.
 * @param onCaptureFailed called with the `file:` URI prepared for a shot that then failed, so the
 *   caller deletes the leftover file; the camera itself deletes nothing.
 * @param fileDispatcher where the files are listed, created and deleted, so the main thread never
 *   touches the disk. Production uses [Dispatchers.IO]; tests pass [Dispatchers.Main] so the
 *   Compose test rule waits for the file work.
 */
@Composable
internal fun PolyLoopCameraContent(
    photoCount: Int,
    onPhotoTaken: (uri: String) -> Unit,
    onClose: () -> Unit,
    preview: @Composable (onBindError: () -> Unit, modifier: Modifier) -> Unit,
    capture: (file: File, onSaved: () -> Unit, onError: () -> Unit) -> Unit,
    onCaptureFailed: (uri: String) -> Unit,
    modifier: Modifier = Modifier,
    maxPhotos: Int = Listing.MAX_PHOTOS,
    keepPhotos: List<String> = emptyList(),
    fileDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  // A state write is visible to the next read at once; only recomposition waits. So a second tap
  // right after the first already sees it and is ignored.
  var capturing by remember { mutableStateOf(false) }
  var hasError by remember { mutableStateOf(false) }

  // Once, when the camera opens, and off the main thread: it lists and deletes files.
  LaunchedEffect(Unit) {
    withContext(fileDispatcher) {
      deleteStalePhotos(
          File(context.filesDir, LISTING_PHOTOS_DIR),
          System.currentTimeMillis(),
          keepPhotos,
      )
    }
  }

  LaunchedEffect(photoCount) { if (shouldCloseCamera(photoCount, maxPhotos)) onClose() }

  Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
    preview({ hasError = true }, Modifier.fillMaxSize())
    CameraOverlay(
        photoCount = photoCount,
        maxPhotos = maxPhotos,
        canTakePhoto = canTakePhoto(photoCount, capturing, maxPhotos),
        errorMessage = if (hasError) stringResource(R.string.create_listing_camera_error) else null,
        onShutter = {
          if (!capturing) {
            capturing = true
            hasError = false
            scope.launch {
              // The capture is marked started above, before this runs, so a second tap is ignored
              // meanwhile; if no shot can start, it must be cleared here.
              val file =
                  try {
                    withContext(fileDispatcher) {
                      newPhotoFile(File(context.filesDir, LISTING_PHOTOS_DIR))
                    }
                  } catch (_: IOException) {
                    hasError = true
                    capturing = false
                    return@launch
                  }
              capture(
                  file,
                  {
                    capturing = false
                    onPhotoTaken(Uri.fromFile(file).toString())
                  },
                  {
                    capturing = false
                    hasError = true
                    onCaptureFailed(Uri.fromFile(file).toString())
                  },
              )
            }
          }
        },
        onClose = onClose,
    )
  }
}

/**
 * The controls over the camera: a close button and the count "[photoCount] / [maxPhotos]" on top
 * (hidden when [maxPhotos] is 1), the [errorMessage] if any in the middle, and the shutter at the
 * bottom, disabled when [canTakePhoto] is false. Everything stays inside the safe area, clear of
 * the status and navigation bars.
 */
@Composable
internal fun CameraOverlay(
    photoCount: Int,
    canTakePhoto: Boolean,
    errorMessage: String?,
    onShutter: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    maxPhotos: Int = Listing.MAX_PHOTOS,
) {
  val shutterLabel = stringResource(R.string.create_listing_camera_shutter)
  Box(modifier = modifier.fillMaxSize().testTag(C.Tag.create_listing_camera).systemBarsPadding()) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp).align(Alignment.TopStart),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(
          onClick = onClose,
          modifier = Modifier.testTag(C.Tag.create_listing_camera_close),
      ) {
        Icon(
            Icons.Default.Close,
            contentDescription = stringResource(R.string.create_listing_camera_close),
            tint = Color.White,
        )
      }
      // "0 / 1" says nothing when only one photo is taken.
      if (maxPhotos > 1) {
        Text(
            text = stringResource(R.string.create_listing_camera_count, photoCount, maxPhotos),
            color = Color.White,
            modifier = Modifier.padding(start = 8.dp).testTag(C.Tag.create_listing_camera_count),
        )
      }
    }
    if (errorMessage != null) {
      Text(
          text = errorMessage,
          color = Color.White,
          modifier =
              Modifier.align(Alignment.Center)
                  .padding(16.dp)
                  .testTag(C.Tag.create_listing_camera_error),
      )
    }
    Surface(
        onClick = onShutter,
        enabled = canTakePhoto,
        shape = CircleShape,
        color = if (canTakePhoto) Color.White else Color.Gray,
        border = BorderStroke(4.dp, Color.Gray),
        modifier =
            Modifier.align(Alignment.BottomCenter)
                .padding(32.dp)
                .size(72.dp)
                .testTag(C.Tag.create_listing_camera_shutter)
                .semantics { contentDescription = shutterLabel },
    ) {}
  }
}

/**
 * The live camera preview, with [imageCapture] bound to the back camera for the life of the
 * composition. [onBindError] is called when the camera cannot start. CameraX only, so it is kept
 * small: every decision is made in CameraPhotos.kt.
 */
@Composable
private fun CameraPreview(
    imageCapture: ImageCapture,
    onBindError: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val previewView = remember { PreviewView(context) }
  var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
  DisposableEffect(Unit) { onDispose { cameraProvider?.unbindAll() } }
  LaunchedEffect(previewView) {
    try {
      val provider = ProcessCameraProvider.awaitInstance(context)
      val preview =
          Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
      provider.unbindAll()
      provider.bindToLifecycle(
          lifecycleOwner,
          CameraSelector.DEFAULT_BACK_CAMERA,
          preview,
          imageCapture,
      )
      cameraProvider = provider
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      onBindError()
    }
  }
  AndroidView(factory = { previewView }, modifier = modifier)
}

/** Saves a shot into [file] with CameraX and reports the result. */
private fun capturePhoto(
    context: Context,
    imageCapture: ImageCapture,
    file: File,
    onSaved: () -> Unit,
    onError: () -> Unit,
) {
  imageCapture.takePicture(
      ImageCapture.OutputFileOptions.Builder(file).build(),
      ContextCompat.getMainExecutor(context),
      object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(output: ImageCapture.OutputFileResults) = onSaved()

        override fun onError(exception: ImageCaptureException) = onError()
      },
  )
}

@ComposePreview(showBackground = true)
@Composable
private fun CameraOverlayPreview() {
  CameraOverlay(
      photoCount = 2,
      canTakePhoto = true,
      errorMessage = null,
      onShutter = {},
      onClose = {},
  )
}
