package live.mehiz.mpvkt.ui.player.controls

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.persistentListOf
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.preferences.*
import live.mehiz.mpvkt.preferences.preference.AndroidPreferenceStore
import live.mehiz.mpvkt.presentation.components.*
import live.mehiz.mpvkt.subtitles.SubtitleConfig
import live.mehiz.mpvkt.ui.player.controls.components.panels.*
import live.mehiz.mpvkt.ui.player.controls.components.sheets.*
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
class PanelStabilityTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Before
  fun inject() {
    val store = AndroidPreferenceStore(compose.activity)
    SubtitleConfig.active = emptySet()
    startKoin {
      modules(
        module {
          single { PlayerPreferences(store) }
          single { SubtitlesPreferences(store) }
        }
      )
    }
  }

  @After
  fun cleanup() {
    SubtitleConfig.active = emptySet()
    stopKoin()
  }

  @Test
  fun numericDraftDoesNotSendInvalidValuesToTheEngine() {
    val writes = mutableListOf<Float>()
    compose.setContent {
      MaterialTheme { OutlinedNumericChooser(1f, { writes += it }, 10f, .01f, min = .1f) }
    }
    compose.onNode(hasSetTextAction()).performTextReplacement("99")
    compose.runOnIdle { assertTrue(writes.isEmpty()) }
    compose.onNodeWithText(compose.activity.getString(R.string.panel_invalid_number)).assertExists()
    compose.onNode(hasSetTextAction()).performTextReplacement("١٫٢٥")
    compose.runOnIdle { assertEquals(1.25f, writes.last(), 0f) }
  }

  @Test
  fun incrementIsAvailableAsASemanticClick() {
    var calls = 0
    compose.setContent {
      MaterialTheme { RepeatingIconButton({ calls++ }, Modifier.testTag("repeat")) { Text("+") } }
    }
    compose.onNodeWithTag("repeat").performSemanticsAction(SemanticsActions.OnClick) { it() }
    compose.runOnIdle { assertEquals(1, calls) }
  }

  @Test
  fun openingDelayNeverWritesAndChangingTargetCancelsMeasurement() {
    var secondary by mutableStateOf(false)
    val writes = mutableListOf<Pair<Boolean, Int>>()
    compose.setContent {
      MaterialTheme {
        DelayCard(
          if (secondary) 300 else 100,
          { writes += secondary to it },
          {},
          {},
          { Button({ secondary = true }) { Text("Secondary target") } },
          DelayType.Subtitle,
          measurementKey = secondary,
        )
      }
    }
    compose.runOnIdle { assertTrue(writes.isEmpty()) }
    compose
      .onNodeWithText(
        compose.activity.getString(R.string.player_sheets_sub_delay_subtitle_voice_heard)
      )
      .performClick()
    compose.onNodeWithText("Secondary target").performClick()
    compose
      .onNodeWithText(
        compose.activity.getString(R.string.player_sheets_sub_delay_subtitle_text_seen)
      )
      .performClick()
    compose.runOnIdle { assertTrue(writes.isEmpty()) }
  }

  @Test
  fun reducedHeightDoesNotChangePortraitPaneWidth() {
    var short by mutableStateOf(false)
    compose.setContent {
      MaterialTheme {
        Box(Modifier.width(392.dp).height(if (short) 300.dp else 852.dp)) {
          PlayerSheet({}, Modifier.testTag("pane"), reduceMotion = true) { _, _ ->
            Box(Modifier.fillMaxSize())
          }
        }
      }
    }
    compose.onNodeWithTag("pane").assertWidthIsEqualTo(368.dp)
    compose.runOnIdle { short = true }
    compose.onNodeWithTag("pane").assertWidthIsEqualTo(368.dp)
  }

  @Test
  fun closingRemovesActionableChildrenFromAccessibility() {
    lateinit var close: () -> Unit
    compose.setContent {
      MaterialTheme {
        PlayerSheet({}) { onClose, _ ->
          close = onClose
          Button({}, Modifier.testTag("action")) { Text("Action") }
        }
      }
    }
    compose.mainClock.advanceTimeBy(1500)
    compose.mainClock.autoAdvance = false
    compose.runOnIdle { close() }
    compose.mainClock.advanceTimeByFrame()
    compose.onNodeWithTag("action").assertDoesNotExist()
  }

  @Test
  fun secondaryToolbarPropagatesItsTargetToAllActions() {
    val targets = mutableListOf<Int>()
    compose.setContent {
      MaterialTheme {
        SubtitlesSheet(
          persistentListOf(),
          { _, _ -> },
          { targets += it },
          { targets += it },
          { targets += it },
          { targets += it },
        )
      }
    }
    compose
      .onNodeWithText(compose.activity.getString(R.string.subtitle_secondary_track))
      .performClick()
    compose
      .onNodeWithContentDescription(
        compose.activity.getString(R.string.player_sheets_subtitles_settings_title)
      )
      .performClick()
    compose
      .onNodeWithContentDescription(
        compose.activity.getString(R.string.player_sheets_sub_delay_card_title)
      )
      .performClick()
    compose
      .onNodeWithContentDescription(compose.activity.getString(R.string.player_sheets_add_ext_sub))
      .performClick()
    compose
      .onNodeWithContentDescription(compose.activity.getString(R.string.subtitle_open_online))
      .performClick()
    compose.runOnIdle { assertEquals(listOf(1, 1, 1, 1), targets) }
  }

  @Test
  fun confirmingAnUneditedTimerPreservesItsRemainingSeconds() {
    val changes = mutableListOf<Int>()
    compose.setContent { MaterialTheme { SleepTimerPanel(59, { changes += it }, {}) } }
    compose.onNodeWithText(compose.activity.getString(R.string.generic_ok)).performClick()
    compose.runOnIdle { assertTrue(changes.isEmpty()) }
  }

  @Test
  fun nativeColorAcknowledgementUsesColorValueRatherThanHexSpelling() {
    assertTrue(
      live.mehiz.mpvkt.ui.player.PanelProperties.equivalentString(
        "sub-color",
        "#ffffff",
        "#FFFFFFFF",
      )
    )
    assertFalse(
      live.mehiz.mpvkt.ui.player.PanelProperties.equivalentString(
        "sub-color",
        "#000000",
        "#FFFFFFFF",
      )
    )
    assertFalse(SubtitleConfig.canEdit("sub-border-color", setOf("sub-outline-color")))
    assertFalse(SubtitleConfig.canEdit("sub-outline-size", setOf("sub-border-size")))
  }
}
