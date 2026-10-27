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
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException

/**
 * The in-app camera: a live [CameraPreview] with a [CameraOverlay] (shutter, close button, count
 * "[photoCount] / 10") on top, filling its window. The caller shows it in a full-screen dialog,
 * which also handles the system Back button.
 *
 * Each shot is saved as a JPEG in `filesDir/listing_photos/` and its file Uri is passed to
 * [onPhotoTaken]. The camera stays open after each shot, so several photos can be taken in a row.
 * It closes through [onClose] with the close button, or by itself once [photoCount] reaches
 * [Listing.MAX_PHOTOS]. A camera or capture error shows a short message and keeps the camera open.
 *
 * This is the only source of listing photos: no gallery picker and no system camera app.
 */
@Composable
fun PolyLoopCamera(
    photoCount: Int,
    onPhotoTaken: (uri: String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
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
 */
@Composable
internal fun PolyLoopCameraContent(
    photoCount: Int,
    onPhotoTaken: (uri: String) -> Unit,
    onClose: () -> Unit,
    preview: @Composable (onBindError: () -> Unit, modifier: Modifier) -> Unit,
    capture: (file: File, onSaved: () -> Unit, onError: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var isCapturing by remember { mutableStateOf(false) }
  var hasError by remember { mutableStateOf(false) }
  // Set before the capture starts, so a second tap before the next recomposition is ignored.
  val captureInFlight = remember { AtomicBoolean(false) }

  LaunchedEffect(photoCount) { if (shouldCloseCamera(photoCount)) onClose() }

  Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
    preview({ hasError = true }, Modifier.fillMaxSize())
    CameraOverlay(
        photoCount = photoCount,
        canTakePhoto = canTakePhoto(photoCount, isCapturing),
        errorMessage = if (hasError) stringResource(R.string.create_listing_camera_error) else null,
        onShutter = {
          if (captureInFlight.compareAndSet(false, true)) {
            isCapturing = true
            hasError = false
            val file = newPhotoFile(File(context.filesDir, LISTING_PHOTOS_DIR))
            capture(
                file,
                {
                  captureInFlight.set(false)
                  isCapturing = false
                  onPhotoTaken(Uri.fromFile(file).toString())
                },
                {
                  captureInFlight.set(false)
                  isCapturing = false
                  hasError = true
                  file.delete()
                },
            )
          }
        },
        onClose = onClose,
    )
  }
}

/**
 * The controls over the camera: a close button and the count "[photoCount] / 10" on top, the
 * [errorMessage] if any in the middle, and the shutter at the bottom, disabled when [canTakePhoto]
 * is false. Everything stays inside the safe area, clear of the status and navigation bars.
 */
@Composable
internal fun CameraOverlay(
    photoCount: Int,
    canTakePhoto: Boolean,
    errorMessage: String?,
    onShutter: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
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
      Text(
          text =
              stringResource(R.string.create_listing_camera_count, photoCount, Listing.MAX_PHOTOS),
          color = Color.White,
          modifier = Modifier.padding(start = 8.dp).testTag(C.Tag.create_listing_camera_count),
      )
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
