package live.mehiz.mpvkt.ui.player.controls

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.collections.immutable.persistentListOf
import live.mehiz.mpvkt.ui.player.controls.components.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-rUS-w392dp-h852dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeferredSeekTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private fun slider() = compose.onNodeWithContentDescription("Playback position")

  @Test
  fun allStylesPreviewWhileHoldingAndCommitOnlyOnceOnRelease() {
    var style by mutableStateOf(SeekbarStyle.Normal)
    var position by mutableFloatStateOf(10f)
    var dragging = false
    val seeks = mutableListOf<Float>()
    compose.setContent {
      MaterialTheme {
        Surface {
          SeekbarWithTimers(
            position,
            100f,
            90f,
            80f,
            {
              seeks.add(it)
              position = it
            },
            {},
            false to false,
            {},
            {},
            persistentListOf(),
            style = style,
            onScrubbingChanged = { dragging = it },
          )
        }
      }
    }
    SeekbarStyle.entries.forEach { choice ->
      compose.runOnIdle { style = choice }
      val before = seeks.size
      slider().performTouchInput {
        down(Offset(width * .25f, height * .5f))
        moveTo(Offset(width * .75f, height * .5f))
      }
      compose.runOnIdle {
        assertEquals(before, seeks.size)
        assertTrue(dragging)
      }
      val preview =
        slider().fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
      assertTrue(preview in 70f..85f)
      slider().performTouchInput { up() }
      compose.runOnIdle {
        assertEquals(before + 1, seeks.size)
        assertFalse(dragging)
        assertEquals(preview, seeks.last(), .1f)
      }
    }
  }

  @Test
  fun cancellationAndMediaReplacementNeverCommitAnOldFingerPosition() {
    var media by mutableLongStateOf(1L)
    var position by mutableFloatStateOf(10f)
    val seeks = mutableListOf<Float>()
    compose.setContent {
      MaterialTheme {
        Surface {
          SeekbarWithTimers(
            position,
            100f,
            90f,
            80f,
            { seeks.add(it) },
            {},
            false to false,
            {},
            {},
            persistentListOf(),
            mediaKey = media,
          )
        }
      }
    }
    slider().performTouchInput {
      down(center)
      moveBy(Offset(25f, 0f))
    }
    compose.runOnIdle { position = 12f }
    assertTrue(
      slider().fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current > 50f
    )
    slider().performTouchInput { cancel() }
    compose.runOnIdle { assertTrue(seeks.isEmpty()) }
    slider().performTouchInput {
      down(center)
      moveBy(Offset(20f, 0f))
    }
    compose.runOnIdle { media = 2L }
    slider().performTouchInput { up() }
    compose.runOnIdle { assertTrue(seeks.isEmpty()) }
  }

  @Test
  fun verticalSwipesCancelPreviewWithoutSeeking() {
    val seeks = mutableListOf<Float>()
    compose.setContent {
      MaterialTheme {
        Surface {
          SeekbarWithTimers(
            10f,
            100f,
            90f,
            80f,
            { seeks.add(it) },
            {},
            false to false,
            {},
            {},
            persistentListOf(),
          )
        }
      }
    }
    slider().performTouchInput {
      down(center)
      moveBy(Offset(0f, 70f))
      up()
    }
    compose.runOnIdle { assertTrue(seeks.isEmpty()) }
    assertEquals(
      10f,
      slider().fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current,
      .1f,
    )
  }

  @Test
  fun draftSurvivesOldAcknowledgementsAndRejectsInvalidNumbers() {
    val state = ScrubSession()
    state.preview(Float.NaN, 100f)
    assertNull(state.release())
    state.preview(30f, 100f)
    assertEquals(30f, state.release()!!, 0f)
    assertNull(state.release())
    state.preview(80f, 100f)
    state.acknowledge(30f)
    assertEquals(80f, state.draft!!, 0f)
    state.cancel()
    assertNull(state.release())
    assertNull(state.draft)
    state.preview(150f, 100f)
    assertEquals(100f, state.release()!!, 0f)
    state.acknowledge(99.5f)
    assertNull(state.draft)
  }
}
