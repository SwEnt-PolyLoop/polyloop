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
import com.swent.polyloop.ui.theme.SampleAppTheme

class SecondActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      _root_ide_package_.com.swent.polyloop.ui.theme.SampleAppTheme {
        // A surface container using the 'background' color from the theme
        Surface(
          modifier = Modifier.fillMaxSize().semantics {
            testTag = _root_ide_package_.com.swent.polyloop.resources.C.Tag.second_screen_container
          },
          color = MaterialTheme.colorScheme.background,
        ) {
          _root_ide_package_.com.swent.polyloop.GreetingRobo("Robolectric")
        }
      }
    }
  }
}

@Composable
fun GreetingRobo(name: String, modifier: Modifier = Modifier) {
  Text(text = "Hello $name!", modifier = modifier.semantics { testTag = _root_ide_package_.com.swent.polyloop.resources.C.Tag.greeting_robo })
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview2() {
  _root_ide_package_.com.swent.polyloop.ui.theme.SampleAppTheme {
    _root_ide_package_.com.swent.polyloop.GreetingRobo(
      "Robolectric"
    )
  }
}
