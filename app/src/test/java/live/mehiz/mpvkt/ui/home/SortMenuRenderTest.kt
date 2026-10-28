package live.mehiz.mpvkt.ui.home

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import java.io.File
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
class SortMenuRenderTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Test fun rapidSelectionKeepsBothKeyAndDirectionAndEqualTouchHeights() {
    val sort = mutableStateOf(LibrarySort())
    compose.setContent { MaterialTheme {
      LibrarySortMenuContent(sort.value, true, Modifier.width(360.dp).testTag("sort_sheet")) { sort.value = it }
    } }
    compose.onNodeWithText("Size").performClick()
    compose.onNodeWithText("Z-A").performClick()
    compose.onNodeWithText("Date").performClick()
    compose.waitForIdle()
    assertEquals(LibrarySort(LibrarySortKey.Date, false), sort.value)
    compose.onNodeWithText("Date").assertIsSelected()
    compose.onNodeWithText("Z-A").assertIsSelected()
    val row = compose.onNodeWithText("Date").fetchSemanticsNode().boundsInRoot
    val order = compose.onNodeWithText("A-Z").fetchSemanticsNode().boundsInRoot
    assertEquals(row.height, order.height, 1f)
    val bounds = compose.onNodeWithTag("sort_sheet").fetchSemanticsNode().boundsInRoot
    val bitmap = Bitmap.createBitmap(bounds.width.toInt(), bounds.height.toInt(), Bitmap.Config.ARGB_8888)
    compose.runOnUiThread {
      val canvas = Canvas(bitmap)
      canvas.translate(-bounds.left, -bounds.top)
      compose.activity.findViewById<View>(android.R.id.content).draw(canvas)
    }
    File("build/reports/sort-sheet/render.png").apply { parentFile?.mkdirs() }.outputStream().use {
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
    }
  }

  @Test fun fileSortingCannotSelectCount() {
    var chosen: LibrarySort? = null
    compose.setContent { MaterialTheme { LibrarySortMenuContent(LibrarySort(), false) { chosen = it } } }
    compose.onNodeWithText("Count").assertIsNotEnabled().performClick()
    assertNull(chosen)
  }

  @Test fun choosingSortClosesAfterItsExitAndOrderCanBeChangedOnReopen() {
    var selected = LibrarySort()
    compose.setContent {
      var sort by remember { mutableStateOf(selected) }
      MaterialTheme { LibrarySortButton(sort, true, false) { sort = it; selected = it } }
    }
    compose.onNodeWithContentDescription("Sort by").performClick()
    compose.mainClock.advanceTimeBy(600)
    compose.waitForIdle()
    compose.mainClock.autoAdvance = false
    compose.onNodeWithText("Size").performClick()
    assertEquals(LibrarySortKey.Size, selected.key)
    compose.onNodeWithText("Z-A").assertExists()
    compose.mainClock.advanceTimeBy(500)
    compose.onNodeWithText("Z-A").assertDoesNotExist()
    compose.mainClock.autoAdvance = true
    compose.onNodeWithContentDescription("Sort by").performClick()
    compose.onNodeWithText("Z-A").performClick()
    compose.waitForIdle()
    assertFalse(selected.ascending)
    compose.onNodeWithText("Z-A").assertDoesNotExist()
  }
}
