package live.mehiz.mpvkt.ui.preferences

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.LayoutDirection
import live.mehiz.mpvkt.ui.theme.DarkMode
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-rUS-w360dp-h800dp-mdpi")
class AppearanceModeSelectorTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Test fun rapidSelectionRemainsExclusiveAndKeepsEqualWidths() {
    var selected by mutableStateOf(DarkMode.Dark)
    compose.setContent { MaterialTheme { AppearanceModeSelector(selected, { selected = it }) } }
    val originalWidth = compose.onNodeWithText("Dark").fetchSemanticsNode().boundsInRoot.width
    compose.onNodeWithText("Light").performClick()
    compose.onNodeWithText("System").performClick()
    compose.onNodeWithText("Dark").performClick()
    compose.onNodeWithText("Dark").assertIsSelected()
    compose.onNodeWithText("Light").assertIsNotSelected()
    compose.onNodeWithText("System").assertIsNotSelected()
    assertEquals(originalWidth, compose.onNodeWithText("System").fetchSemanticsNode().boundsInRoot.width, .5f)
  }

  @Test fun rtlUsesTheSameLogicalModesAndSelection() {
    var selected by mutableStateOf(DarkMode.System)
    compose.setContent {
      MaterialTheme {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
          AppearanceModeSelector(selected, { selected = it })
        }
      }
    }
    compose.onNodeWithText("System").assertIsSelected()
    compose.onNodeWithText("Light").performClick().assertIsSelected()
    compose.onNodeWithText("System").assertIsNotSelected()
    compose.runOnIdle { assertEquals(DarkMode.Light, selected) }
  }
}
