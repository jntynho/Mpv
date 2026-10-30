package live.mehiz.mpvkt.ui.preferences

import android.app.Application
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import live.mehiz.mpvkt.preferences.*
import live.mehiz.mpvkt.preferences.preference.AndroidPreferenceStore
import live.mehiz.mpvkt.presentation.Screen
import live.mehiz.mpvkt.testutils.captureWindow
import live.mehiz.mpvkt.ui.secure.SecureFolderScreen
import live.mehiz.mpvkt.ui.theme.MpvKtTheme
import live.mehiz.mpvkt.ui.utils.*
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
class RefinementScreensTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  lateinit var player: PlayerPreferences

  @Before
  fun setup() {
    val store = AndroidPreferenceStore(compose.activity)
    player = PlayerPreferences(store)
    startKoin {
      modules(
        module {
          single { player }
          single { AppearancePreferences(store) }
          single { FoldersPreferences(store) }
        }
      )
    }
  }

  @After
  fun close() {
    stopKoin()
  }

  private fun capture(name: String) {
    compose.mainClock.advanceTimeBy(400)
    compose.waitForIdle()
    compose.runOnUiThread { captureWindow(compose.activity, name) }
  }

  @Test
  fun stylesSelectDirectlyAndSecurityWindowFlagsAreRestoredWhenLeavingTheGate() {
    var page by mutableStateOf<Screen>(AboutScreen)
    compose.setContent {
      MpvKtTheme {
        val stack = androidx.navigation3.runtime.rememberNavBackStack<Screen>(AboutScreen)
        CompositionLocalProvider(LocalNavigateBack provides {}, LocalBackStack provides stack) {
          Surface(Modifier.fillMaxSize()) { page.Content() }
        }
      }
    }
    compose.onNodeWithText("MPV").assertExists()
    capture("about")
    compose.runOnIdle { page = SeekbarStylesScreen }
    compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Slim"))
    compose.onNodeWithText("Slim").performClick()
    compose.runOnIdle {
      assertEquals(
        live.mehiz.mpvkt.ui.player.controls.components.SeekbarStyle.Slim,
        player.seekbarStyle.get(),
      )
    }
    compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Live preview"))
    capture("seekbar-picker")
    compose.runOnIdle { page = BackupRestoreScreen }
    compose.onNodeWithText("Export Settings").assertIsDisplayed()
    compose.onNodeWithText("Import Settings").assertIsDisplayed()
    capture("backup")
    compose.runOnIdle { page = SecureFolderScreen }
    compose.onNodeWithText("PIN").assertExists()
    compose.runOnIdle {
      assertTrue(
        compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
      )
    }
    capture("secure-folder")
    compose.runOnIdle { page = AboutScreen }
    compose.waitForIdle()
    compose.runOnIdle {
      assertEquals(
        0,
        compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE,
      )
    }
  }

  @Test
  fun thumbnailDialogUsesTheSemanticIconAndTheSharedQuietRim() {
    compose.setContent {
      MpvKtTheme {
        CompositionLocalProvider(LocalNavigateBack provides {}) {
          AppearancePreferencesScreen.Content()
        }
      }
    }
    compose.onNodeWithText("Thumbnail source").performScrollTo()
    compose.onNodeWithText("Thumbnail source").performClick()
    compose.onNode(isDialog()).assertExists()
    capture("thumbnail-source-dialog")
    compose.onNodeWithText("Extract a video frame").performClick()
    compose.onNode(isDialog()).assertDoesNotExist()
  }
}
