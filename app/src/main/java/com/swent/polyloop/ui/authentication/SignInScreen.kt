package com.swent.polyloop.ui.authentication

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.swent.polyloop.ui.theme.PolyLoopTheme

@Composable
fun SignInScreen(modifier: Modifier = Modifier) {
  Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
      Text("Sign in", style = MaterialTheme.typography.headlineMedium)
    }
  }
}

@Preview(showBackground = true)
@Composable
fun SignInScreenPreview() {
  PolyLoopTheme { SignInScreen() }
}
