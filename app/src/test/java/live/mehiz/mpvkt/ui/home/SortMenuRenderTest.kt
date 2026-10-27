package live.mehiz.mpvkt.ui.home

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.*
import androidx.compose.ui.unit.dp
import java.io.File
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

  private fun render(onSelect: (LibrarySort) -> Unit = {}): Bitmap {
    compose.setContent {
      MaterialTheme(colorScheme = darkColorScheme(surface = Color(0xFF101719))) {
        Box(Modifier.background(Color(0xFF071311))) {
          LibrarySortMenuContent(LibrarySort(), true, modifier = Modifier.width(256.dp).testTag("sort_menu"), onSelect = onSelect)
        }
      }
    }
    val bounds = compose.onNodeWithTag("sort_menu").fetchSemanticsNode().boundsInRoot
    val bitmap = Bitmap.createBitmap(bounds.width.toInt(), bounds.height.toInt(), Bitmap.Config.ARGB_8888)
    compose.runOnUiThread {
      val canvas = Canvas(bitmap)
      canvas.translate(-bounds.left, -bounds.top)
      compose.activity.findViewById<View>(android.R.id.content).draw(canvas)
    }
    return bitmap
  }

  @Test fun renderActualMenuForReferenceComparison() {
    val bitmap = render()
    val output = File("build/reports/sort-menu/render.png").apply { parentFile?.mkdirs() }
    output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    assertEquals(256, bitmap.width)
    assertEquals(367, bitmap.height)
  }

  @Test fun outerCornersKeepTheReferenceCircleRatherThanTheOldSquircle() {
    val bitmap = render()
    val backdrop = android.graphics.Color.rgb(7, 19, 17)
    // The previous squared curves incorrectly filled these corner pixels.
    assertEquals(backdrop, bitmap.getPixel(8, 20))
    assertNotEquals(backdrop, bitmap.getPixel(20, 20))
    assertEquals(backdrop, bitmap.getPixel(8, bitmap.height - 7))
    assertNotEquals(backdrop, bitmap.getPixel(20, bitmap.height - 7))
  }

  @Test fun selectedCardsHaveCircularCornersAndTheSameVisibleHeight() {
    val bitmap = render()
    val selected = android.graphics.Color.rgb(43, 80, 73)
    assertNotEquals(selected, bitmap.getPixel(17, 72))
    assertEquals(selected, bitmap.getPixel(24, 72))
    assertNotEquals(selected, bitmap.getPixel(18, 307))
    assertEquals(selected, bitmap.getPixel(27, 307))
    fun extent(x: Int, range: IntRange): Int {
      val filled = range.filter { bitmap.getPixel(x, it) == selected }
      return filled.last() - filled.first() + 1
    }
    assertEquals(48, extent(128, 60..125))
    assertEquals(48, extent(70, 295..355))
  }

  @Test fun choosingAnOptionStillReportsTheCorrectSort() {
    var chosen: LibrarySort? = null
    render { chosen = it }
    compose.onNodeWithText("Size").performClick()
    assertEquals(LibrarySort(LibrarySortKey.Size), chosen)
  }
}
