package live.mehiz.mpvkt.ui.home

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
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

  private fun radio(text: String) =
    compose.onNode(
      hasText(text) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
    )

  @Test
  fun rapidSelectionKeepsKeyAndUsesContextualOrderLabels() {
    val sort = mutableStateOf(LibrarySort())
    compose.setContent {
      MaterialTheme {
        LibrarySortMenuContent(sort.value, true, Modifier.width(360.dp)) { sort.value = it }
      }
    }
    radio("Size").performClick()
    radio("Largest").performClick()
    radio("Date").performClick()
    compose.waitForIdle()
    assertEquals(LibrarySort(LibrarySortKey.Date, false), sort.value)
    radio("Date").assertIsSelected()
    radio("Newest").assertIsSelected()
    assertEquals(
      radio("Date").fetchSemanticsNode().boundsInRoot.height,
      radio("Oldest").fetchSemanticsNode().boundsInRoot.height,
      1f,
    )
  }

  @Test
  fun filesOfferDurationInsteadOfTheFolderCountSort() {
    var chosen: LibrarySort? = null
    compose.setContent {
      MaterialTheme { LibrarySortMenuContent(LibrarySort(), false) { chosen = it } }
    }
    compose.onNodeWithText("Count").assertDoesNotExist()
    radio("Duration").performClick()
    assertEquals(LibrarySortKey.Duration, chosen?.key)
  }

  @Test
  fun multipleChangesStayInOneSheetAndDoneUsesTheNormalExit() {
    var selected = LibrarySort()
    compose.setContent {
      var sort by remember { mutableStateOf(selected) }
      MaterialTheme {
        LibrarySortButton(sort, true, false) {
          sort = it
          selected = it
        }
      }
    }
    compose.onNodeWithContentDescription("Sort by").performClick()
    radio("Size").performClick()
    radio("Largest").performClick()
    assertEquals(LibrarySort(LibrarySortKey.Size, false), selected)
    compose.onNodeWithText("Done").assertExists()
    compose.mainClock.autoAdvance = false
    compose.onNodeWithText("Done").performClick()
    radio("Largest").assertExists()
    compose.mainClock.advanceTimeBy(500)
    compose.onNodeWithText("Largest").assertDoesNotExist()
  }
}
