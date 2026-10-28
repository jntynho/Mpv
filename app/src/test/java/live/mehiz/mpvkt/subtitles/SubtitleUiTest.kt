package live.mehiz.mpvkt.subtitles

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import java.io.File
import live.mehiz.mpvkt.preferences.AdvancedPreferences
import live.mehiz.mpvkt.preferences.AppearancePreferences
import live.mehiz.mpvkt.preferences.PlayerPreferences
import live.mehiz.mpvkt.preferences.SubtitlesPreferences
import live.mehiz.mpvkt.preferences.preference.AndroidPreferenceStore
import live.mehiz.mpvkt.presentation.components.PlayerSheet
import live.mehiz.mpvkt.ui.icons.*
import live.mehiz.mpvkt.ui.player.controls.components.PlayerPanelHeader
import live.mehiz.mpvkt.ui.player.controls.components.panels.SubtitleSearchPanel
import live.mehiz.mpvkt.ui.preferences.SubtitleSettingsPage
import live.mehiz.mpvkt.ui.theme.DarkMode
import live.mehiz.mpvkt.ui.theme.MpvKtTheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-rUS-w392dp-h852dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SubtitleUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Before
  fun preferencesForScaffold() {
    val store = AndroidPreferenceStore(compose.activity.applicationContext)
    startKoin {
      modules(
        module {
          single { PlayerPreferences(store) }
          single {
            AppearancePreferences(store).apply {
              darkMode.set(DarkMode.Dark)
              materialYou.set(false)
            }
          }
        }
      )
    }
  }

  @After
  fun closePreferences() {
    stopKoin()
  }

  private fun theme(content: @Composable () -> Unit) {
    compose.setContent { MpvKtTheme { content() } }
  }

  @Test
  fun actualSettingsUseSharedValuesAndCanToggleAutoLoading() {
    val context = compose.activity.applicationContext
    val store = AndroidPreferenceStore(context)
    val preferences = SubtitlesPreferences(store)
    preferences.autoLoadExternal.set(true)
    val service =
      SubtitleService(
        FakeSubtitleTransport { _, _ -> "[]".toByteArray() },
        { "" },
        File(context.filesDir, "subtitle-test"),
      )
    theme {
      SubtitleSettingsPage(
        preferences,
        SubtitleFonts(context),
        SubtitleKeys(context),
        service,
        AdvancedPreferences(store),
        {},
        {},
      )
    }
    compose.waitUntil(3000) {
      runCatching { compose.onNodeWithText("Automatically load subtitles").assertIsEnabled() }
        .isSuccess
    }
    compose.onNodeWithText("Automatically load subtitles").performClick()
    compose.runOnIdle { assertFalse(preferences.autoLoadExternal.get()) }
    val bitmap = Bitmap.createBitmap(392, 852, Bitmap.Config.ARGB_8888)
    compose.runOnUiThread {
      compose.activity.findViewById<View>(android.R.id.content).draw(Canvas(bitmap))
    }
    File("build/reports/subtitles/settings.png")
      .apply { parentFile?.mkdirs() }
      .outputStream()
      .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
  }

  @Test
  fun seasonAndEpisodeAppearOnlyInSeriesMode() {
    val context = compose.activity.applicationContext
    val preferences = SubtitlesPreferences(AndroidPreferenceStore(context))
    val service =
      SubtitleService(
        FakeSubtitleTransport { _, _ -> "[]".toByteArray() },
        { "" },
        File(context.filesDir, "subtitle-test"),
      )
    theme {
      val scope = rememberCoroutineScope()
      val controller = remember {
        SubtitleSearchController(scope, service, { SubtitleSearchOptions() })
      }
      SubtitleSearchPanel(controller, preferences, {}, { _, _ -> })
    }
    compose.onNodeWithText("Season").assertDoesNotExist()
    compose.onNodeWithContentDescription("TV series").performClick()
    compose.onNodeWithText("Season").assertExists()
    compose.onNodeWithText("Episode").assertExists()
    compose.onNodeWithContentDescription("TV series").performClick()
    compose.onNodeWithText("Season").assertDoesNotExist()
  }

  @Test
  @Config(qualifiers = "en-rUS-w960dp-h540dp-land-mdpi")
  fun landscapeSearchScrollsAndCancelsBeforeTheExitAnimation() {
    val context = compose.activity.applicationContext
    val preferences = SubtitlesPreferences(AndroidPreferenceStore(context))
    val service =
      SubtitleService(
        FakeSubtitleTransport { url, _ ->
          when {
            "/api/tmdb/tv/7/1" in url ->
              """{"episodes":[{"episode_number":2,"name":"Episode Two"}]}"""
            "/api/tmdb/tv/" in url -> """{"seasons":[{"season_number":1,"name":"Season One"}]}"""
            else ->
              """[{"url":"https://example.com/Show.S01E02.srt","fileName":"Show.S01E02.srt","language":"en","format":"srt","source":"Example"}]"""
          }.toByteArray()
        },
        { "" },
        File(context.filesDir, "subtitle-test"),
      )
    lateinit var controller: SubtitleSearchController
    var closing = false
    var dismissed = false
    theme {
      val scope = rememberCoroutineScope()
      controller = remember {
        SubtitleSearchController(scope, service, { SubtitleSearchOptions(mode = "wyzie") })
      }
      LaunchedEffect(Unit) {
        controller.editQuery("Show")
        controller.selectMedia(MediaMatch(7, "Show", true, "2024"))
      }
      PlayerSheet(
        onDismissRequest = { dismissed = true },
        onCloseStart = {
          closing = true
          controller.stop()
        },
      ) { close, drag ->
        Column(Modifier.fillMaxSize()) {
          PlayerPanelHeader("Search subtitles", Icons.Subtitles, close, drag, onBack = {})
          Box(Modifier.weight(1f)) { SubtitleSearchPanel(controller, preferences, {}, { _, _ -> }) }
        }
      }
    }
    compose.waitUntil(5000) { controller.state.value.episodes.isNotEmpty() }
    compose.runOnIdle { controller.search() }
    compose.waitUntil(5000) {
      controller.state.value.subtitles.size == 1 && !controller.state.value.loading
    }
    compose.mainClock.advanceTimeBy(600)
    compose.waitForIdle()
    compose.onNodeWithText("English").assertIsDisplayed()
    compose.onNodeWithText("Season 1").assertIsDisplayed()
    compose.onNodeWithText("Episode 2").assertExists()
    val bitmap = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888)
    compose.runOnUiThread {
      compose.activity.findViewById<View>(android.R.id.content).draw(Canvas(bitmap))
    }
    File("build/reports/subtitles/search-player.png")
      .apply { parentFile?.mkdirs() }
      .outputStream()
      .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    compose.onNodeWithText("Show.S01E02.srt").performScrollTo().assertIsDisplayed()
    compose.mainClock.autoAdvance = false
    compose.onAllNodesWithContentDescription("Close").onLast().performClick()
    compose.runOnIdle {
      assertTrue(closing)
      assertFalse(dismissed)
    }
    compose.mainClock.advanceTimeBy(500)
    compose.runOnIdle { assertTrue(dismissed) }
  }
}
