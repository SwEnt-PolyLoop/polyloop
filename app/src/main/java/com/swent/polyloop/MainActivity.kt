package com.swent.polyloop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.authentication.SignUpScreen
import com.swent.polyloop.ui.authentication.SignUpScreen
import com.swent.polyloop.ui.theme.PolyLoopTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      PolyLoopTheme {
        SignUpScreen()
      }
    }
  }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
  Text(text = "Hello $name!", modifier = modifier.semantics { testTag = C.Tag.greeting })
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
  PolyLoopTheme { Greeting("Android") }
}
