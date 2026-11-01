package live.mehiz.mpvkt.presentation.components

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import live.mehiz.mpvkt.ui.player.controls.components.PanelTextField
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-rUS-w360dp-h800dp-mdpi")
class InputFieldsRegressionTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Test fun queryKeyAndPanelInputsKeepTheSameBodyHeightWithValidation() {
    compose.setContent {
      MaterialTheme {
        Column {
          AppTextField("", {}, label = { Text("Movie or search") })
          RoundedTextField("key", {}, label = { Text("Wyzie API key") },
            isError = true, supportingText = { Text("Validation message") })
          PanelTextField("", {}, label = { Text("Language") })
        }
      }
    }
    val fields = compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
    assertEquals(3, fields.size)
    fields.forEach { assertEquals(48f, it.boundsInRoot.height, .5f) }
  }

  @Test fun aLateEngineEchoDoesNotReplaceAnActiveDelayDraft() {
    var acknowledged by mutableStateOf(0)
    compose.setContent {
      MaterialTheme { OutlinedNumericChooser(acknowledged, {}, max = 1000, step = 1) }
    }
    val field = compose.onNode(hasSetTextAction())
    field.performClick()
    field.performTextReplacement("1")
    field.performTextReplacement("12")
    compose.runOnIdle { acknowledged = 1 }
    field.assertTextContains("12")
  }

  @Test fun incompleteSpeedEditsDoNotSendZeroOrNonFiniteValues() {
    val changes = mutableListOf<Float>()
    compose.setContent {
      MaterialTheme { OutlinedNumericChooser(1f, changes::add, max = 10f, step = .01f, min = .1f) }
    }
    val field = compose.onNode(hasSetTextAction())
    field.performClick()
    field.performTextReplacement("")
    field.performTextReplacement(".")
    compose.runOnIdle { assertTrue(changes.isEmpty()) }
    field.performTextReplacement(".5")
    compose.runOnIdle { assertEquals(.5f, changes.last(), 0f) }
  }
}
