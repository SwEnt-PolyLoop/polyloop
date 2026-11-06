// Made with Claude.

package com.swent.polyloop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import com.swent.polyloop.R
import com.swent.polyloop.resources.C

/**
 * A round profile photo, [size] wide, cropped to fill the circle (the photo itself is 4:3).
 *
 * Shows the first letter of [name] instead ("?" without a name) when [photo] is blank, and when the
 * photo cannot be loaded: offline and not cached, or a URL that stopped working because a newer
 * photo replaced it before the profile was saved.
 *
 * @param photo the photo's download URL, or the local file of a photo just taken; "" for none.
 */
@Composable
fun ProfileAvatar(photo: String, name: String, size: Dp, modifier: Modifier = Modifier) {
  // Forgotten when the photo changes, so a new photo gets its own try.
  var failed by remember(photo) { mutableStateOf(false) }
  Box(
      modifier =
          modifier
              .size(size)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.surfaceVariant),
      contentAlignment = Alignment.Center,
  ) {
    if (photo.isBlank() || failed) {
      Text(
          text = name.trim().firstOrNull()?.uppercase() ?: "?",
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.testTag(C.Tag.profile_avatar),
      )
    } else {
      AsyncImage(
          model = photo,
          contentDescription = stringResource(R.string.profile_photo_description),
          contentScale = ContentScale.Crop,
          onError = { failed = true },
          modifier = Modifier.fillMaxSize().testTag(C.Tag.profile_avatar_photo),
      )
    }
  }
}
