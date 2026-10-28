package live.mehiz.mpvkt.ui.home

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import live.mehiz.mpvkt.presentation.components.PaneMotion
import live.mehiz.mpvkt.presentation.components.StableBottomSheet
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
class SheetAndSortMotionTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun dismissalTranslatesTheSheetWithoutChangingItsMeasuredSize() {
    var dismiss by mutableStateOf(false)
    var visible by mutableStateOf(true)
    compose.setContent {
      MaterialTheme {
        if (visible)
          StableBottomSheet(
            { visible = false },
            Modifier.testTag("sheet"),
            dismissRequested = dismiss,
          ) {
            Box(Modifier.fillMaxWidth().height(240.dp))
          }
      }
    }
    compose.mainClock.advanceTimeBy(600)
    compose.waitForIdle()
    val before = compose.onNodeWithTag("sheet").getUnclippedBoundsInRoot()
    compose.mainClock.autoAdvance = false
    compose.runOnIdle { dismiss = true }
    repeat(3) {
      compose.mainClock.advanceTimeByFrame()
      compose.waitForIdle()
    }
    compose.mainClock.advanceTimeBy(160)
    val during = compose.onNodeWithTag("sheet").getUnclippedBoundsInRoot()
    assertEquals((before.right - before.left).value, (during.right - during.left).value, .1f)
    assertEquals((before.bottom - before.top).value, (during.bottom - during.top).value, .1f)
    val visual = compose.onNodeWithTag("sheet").fetchSemanticsNode().boundsInRoot
    assertTrue("before=$before during=$during visual=$visual", visual.top > before.top.value)
    compose.mainClock.advanceTimeBy(500)
    compose.onNodeWithTag("sheet").assertDoesNotExist()
  }

  @Test
  fun reversingKeyedRowsStartsAtZeroWithoutDroppingTheirPlacementAnimation() {
    var ascending by mutableStateOf(true)
    lateinit var state: LazyListState
    compose.setContent {
      MaterialTheme {
        state = rememberLazyListState()
        ResetScrollOnLibrarySort(state, LibrarySort(ascending = ascending))
        val rows = if (ascending) (0..49).toList() else (49 downTo 0).toList()
        LazyColumn(Modifier.height(400.dp), state = state) {
          items(rows, key = { it }) {
            Text("Row $it", Modifier.animateItem(placementSpec = tween(320)).height(64.dp))
          }
        }
      }
    }
    compose.runOnIdle { state.requestScrollToItem(5) }
    compose.waitForIdle()
    compose.runOnIdle { ascending = false }
    compose.waitForIdle()
    compose.runOnIdle {
      assertEquals(0, state.firstVisibleItemIndex)
      assertEquals(0, state.firstVisibleItemScrollOffset)
      assertEquals(49, state.layoutInfo.visibleItemsInfo.first().key)
    }
  }

  @Test
  fun paneReboundIsBoundedAndAnInterruptedEntranceCannotReopenAfterClose() {
    lateinit var motion: PaneMotion
    lateinit var scope: kotlinx.coroutines.CoroutineScope
    compose.setContent {
      scope = rememberCoroutineScope()
      motion = remember { PaneMotion() }
      Box(Modifier.size(1.dp))
    }
    compose.mainClock.autoAdvance = false
    compose.runOnIdle { motion.open(scope, 400f, 8f, false) }
    var minimum = 1f
    repeat(60) {
      compose.mainClock.advanceTimeBy(16)
      compose.runOnIdle { minimum = minOf(minimum, motion.position) }
    }
    assertTrue(minimum >= -.0201f)
    assertTrue(minimum < 0f)
    compose.runOnIdle {
      assertEquals(0f, motion.position, .001f)
      motion.open(scope, 400f, 8f, false)
    }
    compose.mainClock.advanceTimeBy(100)
    var closed = 0
    compose.runOnIdle { motion.animate(scope, 1f, 320) { closed++ } }
    compose.mainClock.advanceTimeBy(1200)
    compose.runOnIdle {
      assertEquals(1, closed)
      assertEquals(1f, motion.position, .001f)
    }
  }
}
