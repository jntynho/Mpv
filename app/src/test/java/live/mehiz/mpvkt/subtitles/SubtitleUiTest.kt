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
import live.mehiz.mpvkt.ui.player.controls.components.panels.LocalSubtitlePosters
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
    compose
      .onAllNodes(hasScrollToIndexAction())
      .onLast()
      .performScrollToNode(hasText("Show.S01E02.srt"))
    compose.onNodeWithText("Show.S01E02.srt").assertIsDisplayed()
    compose.mainClock.autoAdvance = false
    compose.onAllNodesWithContentDescription("Close").onLast().performClick()
    compose.runOnIdle {
      assertTrue(closing)
      assertFalse(dismissed)
    }
    compose.mainClock.advanceTimeBy(500)
    compose.runOnIdle { assertTrue(dismissed) }
  }

  @Test
  @Config(qualifiers = "en-rUS-w1080dp-h608dp-land-mdpi")
  fun posterResultsRenderInsideTheExistingPanel() = renderPosterSearch(false)

  @Test
  @Config(qualifiers = "en-rUS-w1080dp-h608dp-land-mdpi")
  fun selectedSeriesPosterRendersInsideTheExistingPanel() = renderPosterSearch(true)

  private fun renderPosterSearch(selectedScreenshot: Boolean) {
    val context = compose.activity.applicationContext
    val preferences = SubtitlesPreferences(AndroidPreferenceStore(context))
    preferences.searchMode.set("wyzie")
    val art = posterFixture()
    val loader = SubtitlePosters(FakeSubtitleTransport { _, _ -> art })
    val service =
      SubtitleService(
        FakeSubtitleTransport { url, _ ->
          when {
            "/api/tmdb/search" in url ->
              """{"results":[{"id":7,"title":"Afterlight","mediaType":"tv","releaseYear":"2017","poster":"/afterlight.jpg"},{"id":8,"title":"Afterlight: A New Beginning","mediaType":"movie","releaseYear":"2024","poster":"/afterlight-new.jpg"},{"id":9,"title":"Afterlight Stories","mediaType":"tv","releaseYear":"2020"}]}"""
            "/api/tmdb/tv/7/1" in url ->
              """{"episodes":[{"episode_number":3,"name":"Yesterday and Tomorrow"}]}"""
            "/api/tmdb/tv/7" in url -> """{"seasons":[{"season_number":1,"name":"Season 1"}]}"""
            else ->
              """[{"url":"https://example.com/Afterlight.S01E03.srt","fileName":"Afterlight.S01E03.1080p.WEB.srt","language":"en","format":"srt","source":"Example"}]"""
          }.toByteArray()
        },
        { "" },
        File(context.filesDir, "subtitle-test"),
      )
    lateinit var controller: SubtitleSearchController
    theme {
      val scope = rememberCoroutineScope()
      controller = remember {
        SubtitleSearchController(scope, service, { SubtitleSearchOptions(mode = "wyzie") })
      }
      LaunchedEffect(Unit) {
        controller.editQuery("Afterlight")
        controller.findMedia()
      }
      CompositionLocalProvider(LocalSubtitlePosters provides loader) {
        PlayerSheet({}, reduceMotion = true) { close, drag ->
          Column(Modifier.fillMaxSize()) {
            PlayerPanelHeader("Subtitle search", Icons.Subtitles, close, drag, onBack = {})
            Box(Modifier.weight(1f)) {
              SubtitleSearchPanel(controller, preferences, {}, { _, _ -> }, reduceMotion = true)
            }
          }
        }
      }
    }
    compose.waitUntil(5000) {
      controller.state.value.matches.size == 3 &&
        loader.peek(subtitlePosterUrl("/afterlight.jpg")) != null
    }
    compose.mainClock.advanceTimeBy(600)
    compose.waitForIdle()
    compose.onNodeWithText("Afterlight: A New Beginning").assertIsDisplayed()
    if (!selectedScreenshot) capturePosterPane("poster-results")
    compose.onAllNodesWithText("Afterlight", useUnmergedTree = true).onLast().performClick()
    compose.waitUntil(5000) {
      controller.state.value.episode == 3 && !controller.state.value.loadingEpisodes
    }
    compose.runOnIdle { controller.search() }
    compose.waitUntil(5000) {
      controller.state.value.subtitles.size == 1 && !controller.state.value.loading
    }
    compose.mainClock.advanceTimeBy(600)
    compose.waitForIdle()
    compose.onNodeWithText("Season 1").assertIsDisplayed()
    if (selectedScreenshot) capturePosterPane("poster-selected-series")
    compose.onNodeWithText("Afterlight.S01E03.1080p.WEB.srt").performScrollTo().assertIsDisplayed()
  }

  private fun capturePosterPane(name: String) {
    repeat(3) {
      compose.mainClock.advanceTimeByFrame()
      compose.waitForIdle()
    }
    var bitmap: Bitmap? = null
    compose.runOnUiThread {
      val node = android.graphics.RenderNode("subtitle-preview")
      node.setPosition(0, 0, 1080, 608)
      val canvas = node.beginRecording(1080, 608)
      compose.activity.findViewById<View>(android.R.id.content).draw(canvas)
      node.endRecording()
      val render =
        android.graphics.HardwareRenderer::class
          .java
          .getDeclaredMethod(
            "createHardwareBitmap",
            android.graphics.RenderNode::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
          )
      render.isAccessible = true
      val hardware = render.invoke(null, node, 1080, 608) as Bitmap
      bitmap = hardware.copy(Bitmap.Config.ARGB_8888, false)
      hardware.recycle()
      node.discardDisplayList()
    }
    val image = requireNotNull(bitmap)
    val pixel = image.getPixel(600, 30)
    assertTrue(
      "The rendered pane header must be visible",
      pixel ushr 24 > 128 && pixel and 0xffffff != 0,
    )
    File("build/reports/subtitles/$name.png")
      .apply { parentFile?.mkdirs() }
      .outputStream()
      .use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    image.recycle()
  }

  /** Fictional artwork for deterministic screenshots; never bundled in an APK. */
  private fun posterFixture(): ByteArray {
    val image = Bitmap.createBitmap(185, 278, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(image)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    paint.shader =
      android.graphics.LinearGradient(
        0f,
        0f,
        185f,
        278f,
        intArrayOf(0xff132a4a.toInt(), 0xff52717b.toInt(), 0xff07121b.toInt()),
        null,
        android.graphics.Shader.TileMode.CLAMP,
      )
    canvas.drawRect(0f, 0f, 185f, 278f, paint)
    paint.shader = null
    val random = java.util.Random(7)
    paint.color = 0xffdde9ed.toInt()
    repeat(55) {
      canvas.drawCircle(
        random.nextFloat() * 185,
        random.nextFloat() * 170,
        .7f + random.nextFloat(),
        paint,
      )
    }
    paint.color = 0xffd4b78a.toInt()
    canvas.drawCircle(130f, 94f, 38f, paint)
    paint.color = 0xff07121b.toInt()
    canvas.drawCircle(113f, 84f, 37f, paint)
    val mountain =
      android.graphics.Path().apply {
        moveTo(0f, 225f)
        lineTo(50f, 153f)
        lineTo(82f, 200f)
        lineTo(125f, 165f)
        lineTo(185f, 227f)
        lineTo(185f, 278f)
        lineTo(0f, 278f)
        close()
      }
    canvas.drawPath(mountain, paint)
    paint.color = 0xfff0e9d9.toInt()
    paint.textAlign = android.graphics.Paint.Align.CENTER
    paint.textSize = 20f
    paint.typeface =
      android.graphics.Typeface.create(
        android.graphics.Typeface.SANS_SERIF,
        android.graphics.Typeface.BOLD,
      )
    canvas.drawText("AFTERLIGHT", 92.5f, 240f, paint)
    paint.textSize = 7f
    canvas.drawText("EVERY NIGHT HAS A BEGINNING", 92.5f, 255f, paint)
    return java.io
      .ByteArrayOutputStream()
      .also {
        image.compress(Bitmap.CompressFormat.PNG, 100, it)
        image.recycle()
      }
      .toByteArray()
  }
}
