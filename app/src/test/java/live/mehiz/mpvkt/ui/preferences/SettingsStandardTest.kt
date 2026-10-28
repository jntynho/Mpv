package live.mehiz.mpvkt.ui.preferences

import android.app.Application
import android.graphics.Bitmap
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.first
import live.mehiz.mpvkt.database.entities.CustomButtonEntity
import live.mehiz.mpvkt.preferences.*
import live.mehiz.mpvkt.preferences.preference.AndroidPreferenceStore
import live.mehiz.mpvkt.presentation.components.*
import live.mehiz.mpvkt.presentation.custombuttons.components.*
import live.mehiz.mpvkt.ui.startup.InterfaceWarmup
import live.mehiz.mpvkt.ui.theme.*
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
class SettingsStandardTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Before
  fun setup() {
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
          single { AudioPreferences(store) }
        }
      )
    }
  }

  @After
  fun cleanup() {
    stopKoin()
  }

  @Test
  fun warmupActuallyDrawsAndCapturesAllScenesWithoutExposingControls() {
    var complete by mutableStateOf(false)
    var report: live.mehiz.mpvkt.ui.startup.WarmupReport? = null
    compose.mainClock.autoAdvance = false
    compose.setContent {
      MpvKtTheme {
        Box(Modifier.fillMaxSize()) {
          if (!complete)
            InterfaceWarmup(
              onReady = {
                report = it
                complete = true
              }
            )
          Text(if (complete) "Ready" else "Preparing")
        }
      }
    }
    repeat(520) {
      if (!complete) {
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.runOnUiThread {
          val view = compose.activity.findViewById<View>(android.R.id.content)
          val node = android.graphics.RenderNode("warmup-frame")
          node.setPosition(0, 0, view.width, view.height)
          val canvas = node.beginRecording(view.width, view.height)
          view.draw(canvas)
          node.endRecording()
          node.discardDisplayList()
        }
      }
    }
    compose.mainClock.autoAdvance = true
    compose.waitForIdle()
    assertTrue("Warmup did not finish", complete)
    assertTrue("Warmup report: $report", report?.rendered == true)
    assertEquals(18, report?.captures)
    assertEquals(6, report?.scenes)
    compose.onNodeWithText("Ready").assertIsDisplayed()
    compose.onAllNodes(hasClickAction()).assertCountEquals(0)
  }

  @Test
  fun unsupportedRasterizationStillFinishesAllPreparationScenes() {
    var report: live.mehiz.mpvkt.ui.startup.WarmupReport? = null
    compose.mainClock.autoAdvance = false
    compose.setContent {
      MpvKtTheme {
        Box(Modifier.fillMaxSize()) {
          if (report == null)
            InterfaceWarmup(
              onReady = { report = it },
              captureFrame = { throw UnsupportedOperationException("capture unavailable") },
            )
        }
      }
    }
    repeat(520) { if (report == null) pumpWarmupFrame() }
    compose.mainClock.autoAdvance = true
    compose.waitForIdle()
    assertNotNull("A failed capture must not trap startup", report)
    assertEquals(6, report!!.scenes)
    assertEquals(0, report!!.captures)
    assertFalse(report!!.rendered)
  }

  @Test
  fun startupKeepsTheCoverUntilPreparationAndRetainsTheMountedHome() {
    val context = compose.activity.applicationContext
    context
      .getSharedPreferences("startup", android.content.Context.MODE_PRIVATE)
      .edit()
      .clear()
      .putBoolean("introduced", true)
      .commit()
    val db =
      androidx.room.Room.inMemoryDatabaseBuilder(
          context,
          live.mehiz.mpvkt.database.MpvKtDatabase::class.java,
        )
        .build()
    org.koin.core.context.loadKoinModules(module { single { db } })
    var homes = 0
    compose.mainClock.autoAdvance = false
    compose.setContent {
      MpvKtTheme {
        live.mehiz.mpvkt.ui.startup.StartupGate {
          DisposableEffect(Unit) {
            homes++
            onDispose {}
          }
          Box(Modifier.fillMaxSize()) { Button({}) { Text("Actual home") } }
        }
      }
    }
    repeat(40) { pumpWarmupFrame() }
    compose.onNodeWithText("Preparing your library").assertIsDisplayed()
    compose.onNodeWithText("Actual home").assertDoesNotExist()
    var shown = false
    repeat(650) {
      if (!shown) {
        pumpWarmupFrame()
        shown = runCatching { compose.onNodeWithText("Actual home").assertIsDisplayed() }.isSuccess
      }
    }
    compose.mainClock.autoAdvance = true
    compose.waitForIdle()
    compose.onNodeWithText("Actual home").assertIsDisplayed()
    compose.onNodeWithText("Preparing your library").assertDoesNotExist()
    assertEquals(1, homes)
    assertEquals(
      live.mehiz.mpvkt.BuildConfig.VERSION_CODE,
      context
        .getSharedPreferences("startup", android.content.Context.MODE_PRIVATE)
        .getInt("prepared_version", 0),
    )
    db.close()
  }

  private fun pumpWarmupFrame() {
    // Room/vector preparation uses real worker threads while this test clock is virtual.
    Thread.sleep(2)
    compose.mainClock.advanceTimeByFrame()
    compose.waitForIdle()
    compose.runOnUiThread {
      val view = compose.activity.findViewById<View>(android.R.id.content)
      val node = android.graphics.RenderNode("startup-frame")
      node.setPosition(0, 0, view.width, view.height)
      val canvas = node.beginRecording(view.width, view.height)
      view.draw(canvas)
      node.endRecording()
      node.discardDisplayList()
    }
  }

  @Test
  fun expansionReversesWithoutResizingWidthOrTogglingChildSwitches() {
    var expanded by mutableStateOf(false)
    var checked by mutableStateOf(false)
    compose.setContent {
      MpvKtTheme {
        SettingsCard(Modifier.width(340.dp).testTag("card")) {
          SettingsRow({ Text("Advanced search filters") }, expanded = expanded) {
            expanded = !expanded
          }
          ExpandableSettingsContent(expanded) {
            SettingsRow({ Text("Hearing impaired") }, toggle = checked) { checked = !checked }
            Spacer(Modifier.height(140.dp))
          }
        }
      }
    }
    val width = compose.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot.width
    compose.onNodeWithText("Advanced search filters").performClick()
    compose.onNodeWithText("Hearing impaired").performClick()
    assertTrue(checked)
    assertTrue(expanded)
    compose.mainClock.autoAdvance = false
    compose.onNodeWithText("Advanced search filters").performClick()
    compose.mainClock.advanceTimeBy(90)
    compose.onNodeWithText("Advanced search filters").performClick()
    compose.mainClock.advanceTimeBy(500)
    compose.onNodeWithText("Hearing impaired").assertIsDisplayed()
    assertEquals(width, compose.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot.width)
  }

  @Test
  fun sharedDialogHasEqualCapsuleActionsAndEditableRoundedField() {
    lateinit var dialogView: View
    var value by mutableStateOf("secret-key")
    var saved = ""
    var visible by mutableStateOf(true)
    compose.setContent {
      MpvKtTheme {
        if (visible)
          AppDialog(
            { visible = false },
            title = { Text("Wyzie API key") },
            text = {
              dialogView = LocalView.current
              Column {
                Text("Enter your provider key. Stored securely on this device.")
                Spacer(Modifier.height(16.dp))
                RoundedTextField(
                  value,
                  { value = it },
                  label = { Text("API key") },
                  singleLine = true,
                )
              }
            },
            confirmButton = {
              DialogButton({
                saved = value
                visible = false
              }) {
                Text("Save key")
              }
            },
            dismissButton = { DialogButton({ visible = false }) { Text("Cancel") } },
          )
      }
    }
    compose.onNode(hasSetTextAction()).performTextReplacement("updated-key")
    val cancel = compose.onNodeWithText("Cancel").fetchSemanticsNode().boundsInRoot
    val save = compose.onNodeWithText("Save key").fetchSemanticsNode().boundsInRoot
    assertEquals(cancel.height, save.height, 1f)
    assertEquals(cancel.width, save.width, 1f)
    capture(dialogView.rootView, "dialog-key")
    compose.onNodeWithText("Save key").performClick()
    assertEquals("updated-key", saved)
    compose.onNodeWithText("Wyzie API key").assertDoesNotExist()
  }

  @Test
  fun editingCustomButtonWithoutRenamingAllowsSavingAndRejectsDuplicateName() {
    var title = ""
    compose.setContent {
      MpvKtTheme {
        CustomButtonEditDialog(
          {},
          { name, _, _ -> title = name },
          persistentListOf("Other"),
          CustomButtonEntity(1, "Quick pause", "mp.command(\"cycle pause\")", "", 0),
        )
      }
    }
    compose.onNodeWithText("OK").assertIsEnabled()
    compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("Other")
    compose.onNodeWithText("OK").assertIsNotEnabled()
    compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("Quick pause")
    compose.onNodeWithText("OK").performClick()
    assertEquals("Quick pause", title)
  }

  @Test
  fun clearingHistoryActuallyRemovesPlaybackRowsWithoutRemovingCustomButtons() {
    val context = compose.activity.applicationContext
    val db =
      androidx.room.Room.inMemoryDatabaseBuilder(
          context,
          live.mehiz.mpvkt.database.MpvKtDatabase::class.java,
        )
        .build()
    org.koin.core.context.loadKoinModules(
      module {
        single { db }
        single { AdvancedPreferences(AndroidPreferenceStore(context)) }
        single { live.mehiz.mpvkt.subtitles.SubtitleFonts(context) }
      }
    )
    kotlinx.coroutines.runBlocking {
      db.customButtonDao().upsert(CustomButtonEntity(1, "Keep", "cycle pause", "", 0))
      db
        .videoDataDao()
        .upsert(
          live.mehiz.mpvkt.database.entities.PlaybackStateEntity(
            "film",
            10,
            1.0,
            1,
            0,
            1.0,
            -1,
            0,
            1,
            0,
          )
        )
    }
    compose.setContent {
      MpvKtTheme {
        CompositionLocalProvider(LocalNavigateBack provides {}) {
          AdvancedPreferencesScreen.Content()
        }
      }
    }
    compose.onNodeWithText("Clear playback history").performScrollTo().performClick()
    compose.onNodeWithText("Confirm").performClick()
    compose.waitUntil(5000) {
      kotlinx.coroutines.runBlocking { db.videoDataDao().getVideoDataByTitle("film") == null }
    }
    kotlinx.coroutines.runBlocking {
      assertEquals("Keep", db.customButtonDao().getCustomButtons().first().single().title)
    }
    db.close()
  }

  @Test
  fun rtlDialogKeepsLongActionsEqualAndScrollableContentWithinTheWindow() {
    compose.setContent {
      MpvKtTheme {
        CompositionLocalProvider(
          androidx.compose.ui.platform.LocalDensity provides
            androidx.compose.ui.unit.Density(1f, 1.8f),
          androidx.compose.ui.platform.LocalLayoutDirection provides
            androidx.compose.ui.unit.LayoutDirection.Rtl,
        ) {
          AppDialog(
            {},
            title = { Text("إعدادات الترجمة") },
            text = {
              Column { repeat(8) { RoundedTextField("", {}, label = { Text("إعداد طويل") }) } }
            },
            confirmButton = { DialogButton({}) { Text("حفظ الإعدادات الجديدة") } },
            dismissButton = { DialogButton({}) { Text("إلغاء") } },
          )
        }
      }
    }
    val save = compose.onNodeWithText("حفظ الإعدادات الجديدة").fetchSemanticsNode().boundsInRoot
    val cancel = compose.onNodeWithText("إلغاء").fetchSemanticsNode().boundsInRoot
    assertEquals(save.height, cancel.height, 1f)
    assertEquals(save.width, cancel.width, 1f)
    compose.onNodeWithText("إلغاء").assertIsDisplayed()
    compose.onNodeWithText("حفظ الإعدادات الجديدة").assertIsDisplayed()
  }

  @Test
  fun audioOptionsUseSameCardsAndSharedSelectionDialog() {
    compose.setContent {
      MpvKtTheme {
        CompositionLocalProvider(LocalNavigateBack provides {}) { AudioPreferencesScreen.Content() }
      }
    }
    compose.onNodeWithText("Audio channels").performClick()
    compose.onNodeWithText("Stereo").performScrollTo().performClick()
    compose.onNodeWithText("Stereo").assertExists()
    compose.onNodeWithText("Done").assertDoesNotExist()
  }

  private fun capture(view: View, name: String) {
    compose.mainClock.advanceTimeBy(600)
    compose.waitForIdle()
    var bitmap: Bitmap? = null
    compose.runOnUiThread {
      val node = android.graphics.RenderNode("dialog-preview")
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
    File("build/reports/settings-standard/$name.png")
      .apply { parentFile?.mkdirs() }
      .outputStream()
      .use { requireNotNull(bitmap).compress(Bitmap.CompressFormat.PNG, 100, it) }
  }
}
