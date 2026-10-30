package live.mehiz.mpvkt.ui.player.controls

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.fsaf.FileManager
import live.mehiz.mpvkt.preferences.*
import live.mehiz.mpvkt.preferences.preference.AndroidPreferenceStore
import live.mehiz.mpvkt.testutils.captureWindow
import live.mehiz.mpvkt.ui.player.controls.components.panels.*
import live.mehiz.mpvkt.ui.theme.MpvKtTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.koin.core.context.*
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-rUS-w392dp-h852dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SubtitlePanelRenderTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  lateinit var subtitles: SubtitlesPreferences

  @Before
  fun setup() {
    val store = AndroidPreferenceStore(compose.activity)
    subtitles = SubtitlesPreferences(store)
    startKoin {
      modules(
        module {
          single { subtitles }
          single { PlayerPreferences(store) }
          single { AppearancePreferences(store) }
          single { FileManager(compose.activity) }
        }
      )
    }
  }

  @After
  fun stop() {
    stopKoin()
  }

  @Test
  fun subtitleCardsRenderBeforeNativePropertiesArriveAndColorResetIsImmediate() {
    compose.setContent {
      MpvKtTheme {
        Surface { Box(Modifier.width(360.dp).fillMaxHeight()) { SubtitleSettingsPanel() } }
      }
    }
    compose.onNodeWithText("Font size").assertExists()
    compose.runOnUiThread { captureWindow(compose.activity, "subtitle-settings") }
    compose.onNodeWithContentDescription("Border").performScrollTo().performClick()
    compose.onNodeWithText("Red").performScrollTo().assertIsDisplayed()
    compose.onNodeWithText("Border").assertExists()
    compose.runOnUiThread { captureWindow(compose.activity, "subtitle-colors") }
    compose.runOnIdle {
      subtitles.borderColor.set(0xff123456.toInt())
      assertEquals(
        subtitles.borderColor.defaultValue(),
        resetColors(subtitles, SubColorType.Border),
      )
      assertEquals(subtitles.borderColor.defaultValue(), subtitles.borderColor.get())
    }
  }
}
