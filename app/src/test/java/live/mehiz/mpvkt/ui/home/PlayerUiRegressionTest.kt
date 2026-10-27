package live.mehiz.mpvkt.ui.home

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import live.mehiz.mpvkt.presentation.components.SlimPlayerSlider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-rUS-w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerUiRegressionTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Test fun longTitleKeepsItsMeasuredWidthWhileBackButtonExpands() {
    val title = "A very long folder name ".repeat(20)
    val folder = mutableStateOf(false)
    compose.setContent {
      MaterialTheme {
        LibraryHeader(title, false, folder.value, "", LibrarySort(), false,
          {}, {}, {}, {}, {})
      }
    }
    val initial = compose.onNodeWithText(title).fetchSemanticsNode().boundsInRoot.width
    compose.mainClock.autoAdvance = false
    compose.runOnIdle { folder.value = true }
    compose.mainClock.advanceTimeBy(120)
    val middle = compose.onNodeWithText(title).fetchSemanticsNode().boundsInRoot.width
    compose.mainClock.advanceTimeBy(400)
    val end = compose.onNodeWithText(title).fetchSemanticsNode().boundsInRoot.width
    assertEquals(initial, middle, 1f)
    assertEquals(middle, end, 1f)
    assertTrue(end <= 148f)
  }

  @Test fun thinSliderRetainsAccessibilityAndClampsExternalValues() {
    var chosen = -1f
    compose.setContent { MaterialTheme { SlimPlayerSlider(200f, { chosen = it }, Modifier.width(240.dp), valueRange = 0f..100f) } }
    val slider = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
    assertEquals(100f, slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current, .001f)
    slider.performSemanticsAction(SemanticsActions.SetProgress) { it(25f) }
    compose.runOnIdle { assertEquals(25f, chosen, .001f) }
  }
}
