package live.mehiz.mpvkt.ui.home

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import live.mehiz.mpvkt.preferences.AppearancePreferences
import live.mehiz.mpvkt.preferences.preference.AndroidPreferenceStore
import live.mehiz.mpvkt.presentation.components.PlayerSheet
import live.mehiz.mpvkt.presentation.components.StableBottomSheet
import live.mehiz.mpvkt.ui.player.controls.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-rUS-w392dp-h852dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RefinementBehaviorTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun sortSheetUsesCompactContentHeightWithoutLosingItsHeader() {
    var display by mutableStateOf(LibraryDisplayOptions())
    compose.setContent {
      MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
          StableBottomSheet(
            {},
            Modifier.testTag("sort-sheet"),
            reduceMotion = true,
            compact = true,
          ) {
            LibrarySortMenuContent(
              LibrarySort(),
              true,
              Modifier.fillMaxWidth().weight(1f, false),
              display = display,
              onDisplay = { display = it },
              onSelect = {},
            )
          }
        }
      }
    }
    compose.waitForIdle()
    val original = compose.onNodeWithTag("sort-sheet").getUnclippedBoundsInRoot()
    compose.onNodeWithText("Card").performClick()
    compose.waitForIdle()
    val bounds = compose.onNodeWithTag("sort-sheet").fetchSemanticsNode().boundsInRoot
    assertTrue(bounds.height <= 614f)
    assertEquals((original.right - original.left).value, bounds.width, .5f)
    compose.onNodeWithText("Sort by").assertIsDisplayed()
    compose.onNodeWithContentDescription("Fields").performClick()
    compose.waitForIdle()
    assertTrue(compose.onNodeWithTag("sort-sheet").fetchSemanticsNode().boundsInRoot.height <= 614f)
    compose.onNodeWithText("Sort by").assertIsDisplayed()
    compose.onNodeWithContentDescription("Fields").performClick()
    compose.waitForIdle()
    assertTrue(compose.onNodeWithTag("sort-sheet").fetchSemanticsNode().boundsInRoot.height <= 614f)
    compose.onNodeWithText("Sort by").assertIsDisplayed()
    compose.runOnUiThread {
      live.mehiz.mpvkt.testutils.captureWindow(compose.activity, "sort-compact")
    }
  }

  @Test
  fun cardDefaultsAreIndependentAndDoNotExposeLongPaths() {
    val prefs = AppearancePreferences(AndroidPreferenceStore(compose.activity))
    assertTrue("Path" in prefs.libraryFields.get())
    assertFalse("Path" in prefs.libraryCardFields.get())
    assertTrue("Thumbnails" in prefs.libraryCardFields.get())
    prefs.libraryFields.set(setOf("Path", "Date", "Size"))
    assertFalse("Date" in prefs.libraryCardFields.get())
    val folder =
      LibraryFolder(
        "test",
        "A folder with a very long title that must stay within the card",
        "/storage/very/long/path",
        2,
        1000,
        1,
        duration = 60000,
      )
    compose.setContent {
      MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
          Box(Modifier.fillMaxSize()) {
            Box(Modifier.width(156.dp)) {
              LibraryFolderItem(
                folder,
                LibraryDisplayOptions(prefs.libraryCardFields.get(), true),
                Modifier.testTag("card"),
              ) {}
            }
          }
        }
      }
    }
    compose.onNodeWithText("/storage/very/long/path").assertDoesNotExist()
    compose.onNodeWithTag("card").assertWidthIsEqualTo(156.dp)
    compose.runOnUiThread {
      live.mehiz.mpvkt.testutils.captureWindow(compose.activity, "folder-card")
    }
  }

  @Test
  fun inactivePlayerSheetDoesNotDispatchTaps() {
    var calls = 0
    compose.setContent {
      MaterialTheme {
        PlayerSheet({}, reduceMotion = true, interactive = false) { _, _ ->
          Button({ calls++ }, Modifier.testTag("unsafe")) { Text("Action") }
        }
      }
    }
    compose.onNodeWithTag("unsafe").assertDoesNotExist()
    assertEquals(0, calls)
  }

  @Test
  fun layoutMovePreservesAllControlsAndRejectsInvalidIndices() {
    val initial = LandscapeLayout()
    val moved = initial.move(ControlRegion.TopRight, 0, 3)
    assertEquals(initial.topRight.first(), moved.topRight.last())
    assertEquals(initial.topRight.toSet(), moved.topRight.toSet())
    assertEquals(moved, moved.move(ControlRegion.TopRight, -1, 2))
    assertEquals(moved, moved.move(ControlRegion.TopRight, 1, 99))
    assertEquals(moved, moved.normalized())
  }
}
