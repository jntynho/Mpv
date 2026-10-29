package live.mehiz.mpvkt.ui.preferences

import android.app.Application
import android.graphics.Bitmap
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import java.io.File
import live.mehiz.mpvkt.preferences.*
import live.mehiz.mpvkt.preferences.preference.AndroidPreferenceStore
import live.mehiz.mpvkt.ui.player.controls.*
import live.mehiz.mpvkt.ui.theme.*
import live.mehiz.mpvkt.ui.utils.LocalNavigateBack
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
class LibraryCustomizationTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private lateinit var player: PlayerPreferences
  private lateinit var appearance: AppearancePreferences
  private lateinit var folders: FoldersPreferences

  @Before
  fun setup() {
    val store = AndroidPreferenceStore(compose.activity.applicationContext)
    player = PlayerPreferences(store)
    appearance = AppearancePreferences(store)
    folders = FoldersPreferences(store)
    startKoin {
      modules(
        module {
          single { player }
          single { appearance }
          single { folders }
        }
      )
    }
  }

  @After
  fun cleanup() {
    stopKoin()
  }

  @Test
  fun buttonLocationEditorMovesOneButtonAndPreviewsThePersistedResult() {
    compose.setContent {
      MpvKtTheme {
        CompositionLocalProvider(LocalNavigateBack provides {}) {
          CompositionLocalProvider(
            androidx.compose.ui.platform.LocalLayoutDirection provides
              androidx.compose.ui.unit.LayoutDirection.Rtl
          ) {
            LandscapeControlsScreen.Content()
          }
        }
      }
    }
    val previewTitle = compose.onNodeWithText("MPV").fetchSemanticsNode().boundsInRoot
    assertTrue(
      "Preview must match the player's physical LTR layout even in RTL settings",
      previewTitle.center.x < 196f,
    )
    capture("landscape-controls")
    compose.onNodeWithText("More").performScrollTo().performClick()
    compose.onNode(hasText("Bottom left") and hasAnyAncestor(isDialog())).performClick()
    compose.waitForIdle()
    assertTrue(LandscapeControl.More in player.landscapeLayout.get().bottomLeft)
    assertFalse(LandscapeControl.More in player.landscapeLayout.get().topRight)
    compose.onNode(isDialog()).assertDoesNotExist()
  }

  @Test
  fun paletteAppliesGloballyDisablesWallpaperAndCanDisableThumbnailWork() {
    appearance.materialYou.set(true)
    compose.setContent {
      MpvKtTheme {
        CompositionLocalProvider(LocalNavigateBack provides {}) {
          AppearancePreferencesScreen.Content()
        }
      }
    }
    compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("Ocean"))
    compose.onNodeWithContentDescription("Ocean").assertIsNotSelected().performClick()
    compose.waitForIdle()
    assertEquals(ColourAccent.Ocean, appearance.colourAccent.get())
    assertFalse(appearance.materialYou.get())
    compose.onNodeWithContentDescription("Ocean").assertIsSelected()
    compose.onNodeWithText("Ocean").assertExists()
    capture("appearance-ocean")
    compose.onNodeWithText("Show video thumbnails").performScrollTo().performClick()
    assertFalse(appearance.showThumbnails.get())
    compose.onNodeWithText("Thumbnail source").assertDoesNotExist()
    compose.onNodeWithText("Show video thumbnails").performClick()
    compose.onNodeWithText("Thumbnail source").performScrollTo().performClick()
    compose.onNodeWithText("Extract a video frame").performScrollTo().performClick()
    assertEquals(
      live.mehiz.mpvkt.ui.home.ThumbnailGenerator.Frame,
      appearance.thumbnailGenerator.get(),
    )
    compose.onNodeWithText("Frame position").performScrollTo().assertIsDisplayed()
    capture("thumbnail-options")
  }

  @Test
  fun folderFiltersPersistAndEmptyIncludeModeExplainsTheEmptyLibrary() {
    val library = live.mehiz.mpvkt.ui.home.MediaLibrary(compose.activity.application)
    compose.setContent {
      MpvKtTheme {
        val stack =
          androidx.navigation3.runtime.rememberNavBackStack<live.mehiz.mpvkt.presentation.Screen>(
            FoldersPreferencesScreen
          )
        CompositionLocalProvider(
          LocalNavigateBack provides {},
          live.mehiz.mpvkt.ui.utils.LocalBackStack provides stack,
          live.mehiz.mpvkt.ui.home.LocalMediaLibrary provides library,
        ) {
          FoldersPreferencesScreen.Content()
        }
      }
    }
    compose.onNodeWithText("Minimum video duration").performClick()
    compose.onNodeWithText("10 s").performScrollTo().performClick()
    assertEquals(10, folders.minimumDuration.get())
    compose.onNodeWithText("Only show included folders").performScrollTo().performClick()
    assertTrue(folders.includeOnly.get())
    compose
      .onNodeWithText("Select at least one folder. Until then the library will be empty.")
      .performScrollTo()
      .assertIsDisplayed()
    capture("folder-preferences")
  }

  @Test
  fun decoderModeChangesKeepTheNextButtonInTheSamePosition() {
    var decoder by mutableStateOf(live.mehiz.mpvkt.ui.player.Decoder.HWPlus)
    compose.setContent {
      MpvKtTheme {
        LandscapeControlStrip(
          listOf(LandscapeControl.Decoder, LandscapeControl.Audio),
          decoder,
          1f,
          null,
          null,
          false,
          false,
          {},
          {},
        )
      }
    }
    val before =
      compose.onNodeWithContentDescription("Audio").fetchSemanticsNode().boundsInRoot.left
    compose.runOnIdle { decoder = live.mehiz.mpvkt.ui.player.Decoder.SW }
    compose.mainClock.advanceTimeBy(500)
    compose.waitForIdle()
    val after = compose.onNodeWithContentDescription("Audio").fetchSemanticsNode().boundsInRoot.left
    assertEquals(before, after, .1f)
  }

  private fun capture(name: String) {
    compose.mainClock.advanceTimeBy(600)
    compose.waitForIdle()
    var bitmap: Bitmap? = null
    compose.runOnUiThread {
      val view = compose.activity.findViewById<View>(android.R.id.content)
      val node = android.graphics.RenderNode("customization-preview")
      node.setPosition(0, 0, view.width, view.height)
      val canvas = node.beginRecording(view.width, view.height)
      view.draw(canvas)
      node.endRecording()
      val method =
        android.graphics.HardwareRenderer::class
          .java
          .getDeclaredMethod(
            "createHardwareBitmap",
            android.graphics.RenderNode::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
          )
      method.isAccessible = true
      val hardware = method.invoke(null, node, view.width, view.height) as Bitmap
      bitmap = hardware.copy(Bitmap.Config.ARGB_8888, false)
      hardware.recycle()
      node.discardDisplayList()
    }
    File("build/reports/library-customization/$name.png")
      .apply { parentFile?.mkdirs() }
      .outputStream()
      .use { requireNotNull(bitmap).compress(Bitmap.CompressFormat.PNG, 100, it) }
    bitmap?.recycle()
  }
}
