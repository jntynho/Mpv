package live.mehiz.mpvkt.ui.home

import android.app.Application
import android.graphics.Bitmap
import android.view.View
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
import java.io.File
import live.mehiz.mpvkt.presentation.components.ExpandableCard
import live.mehiz.mpvkt.ui.player.controls.components.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-rUS-w392dp-h852dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ModernPanelsAndLibraryTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private fun theme(content: @Composable () -> Unit) {
    compose.setContent {
      MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(Modifier.fillMaxSize()) { content() }
      }
    }
  }

  @Test
  fun allSeekbarStylesKeepAccessibleProgressAndOneCommitPerAction() {
    var position by mutableFloatStateOf(30f)
    var commits = 0
    theme {
      Column(Modifier.padding(20.dp)) {
        SeekbarStyle.entries.forEach { style ->
          Text(style.name)
          StyledSeeker(
            position,
            100f,
            60f,
            emptyList(),
            { position = it },
            { commits++ },
            Modifier.fillMaxWidth().testTag(style.name),
            style = style,
          )
        }
      }
    }
    SeekbarStyle.entries.forEachIndexed { i, style ->
      val requested = 40f + i * 5
      compose.onNodeWithTag(style.name).performSemanticsAction(SemanticsActions.SetProgress) {
        it(requested)
      }
      compose.runOnIdle { assertEquals(requested, position, .01f) }
    }
    assertEquals(6, commits)
    capture("seekbar-styles")
  }

  @Test
  fun missingDurationNeverProducesInvalidSeekingOrNaNRendering() {
    var commits = 0
    theme {
      StyledSeeker(
        Float.NaN,
        0f,
        Float.NaN,
        emptyList(),
        { commits++ },
        { commits++ },
        Modifier.testTag("empty"),
      )
    }
    compose.onNodeWithTag("empty").assertIsNotEnabled()
    compose.onNodeWithTag("empty").performSemanticsAction(SemanticsActions.SetProgress) {
      assertFalse(it(50f))
    }
    assertEquals(0, commits)
  }

  @Test
  fun fieldSelectionsRemainOpenAndCanScrollAcrossAllFields() {
    var display by mutableStateOf(LibraryDisplayOptions())
    var sort by mutableStateOf(LibrarySort())
    theme {
      LibrarySortMenuContent(
        sort,
        false,
        Modifier.fillMaxSize(),
        viewMode = LibraryViewMode.Videos,
        display = display,
        onDisplay = { display = it },
      ) {
        sort = it
      }
    }
    compose.onNodeWithText("Framerate").performScrollTo().performClick()
    compose.onNodeWithText("Codec support").performScrollTo().performClick()
    assertTrue(display.fields.has(LibraryField.Framerate))
    assertTrue(display.fields.has(LibraryField.Codec))
    compose.onNodeWithText("Done").assertExists()
    capture("sort-fields")
  }

  @Test
  fun collapseCanReverseWithoutLosingItsBodyOrChangingWidth() {
    var expanded by mutableStateOf(true)
    theme {
      ExpandableCard(
        expanded,
        { Text("Filters") },
        { expanded = it },
        Modifier.fillMaxWidth().testTag("card"),
      ) {
        Box(Modifier.height(180.dp)) { Text("Filter body") }
      }
    }
    val width = compose.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot.width
    compose.mainClock.autoAdvance = false
    repeat(6) {
      compose.onNodeWithText("Filters").performClick()
      compose.mainClock.advanceTimeBy(60)
    }
    compose.mainClock.advanceTimeBy(600)
    compose.mainClock.autoAdvance = true
    compose.onNodeWithText("Filter body").assertIsDisplayed()
    assertEquals(width, compose.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot.width, .1f)
  }

  @Test
  fun quickMenuHasThreeActionsAndAValidatedOpenLinkDialog() {
    theme {
      Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.BottomEnd) {
        HomeQuickActions(false)
      }
    }
    compose.onNodeWithContentDescription("Playback actions").performClick()
    compose.onNodeWithText("Instagram").assertDoesNotExist()
    compose.onNodeWithText("Open File").assertIsDisplayed()
    compose.onNodeWithText("Recently Played").assertIsDisplayed()
    capture("quick-actions")
    compose.onNodeWithText("Open Link").performClick()
    compose.onNode(hasSetTextAction()).performTextReplacement("javascript:bad")
    compose.onNode(hasText("Open Link") and hasClickAction()).performClick()
    compose.onNodeWithText("Enter a valid supported stream URL.").assertExists()
    compose.onNode(hasSetTextAction()).performTextReplacement("https://example.invalid/video.mp4")
    compose.onNode(hasText("Open Link") and hasClickAction()).performClick()
    val intent = shadowOf(compose.activity).nextStartedActivity
    assertEquals("https://example.invalid/video.mp4", intent.dataString)
  }

  @Test
  fun recentsUsePlayableUrisInsteadOfOnlyRememberingTitles() {
    RecentMedia.record(compose.activity, "https://example.invalid/played.mp4", "Played film")
    theme { HomeQuickActions(true) }
    compose.onNodeWithContentDescription("Playback actions").performClick()
    compose.onNodeWithText("Recently Played").performClick()
    compose.waitUntil(5000) {
      runCatching { compose.onNodeWithText("Played film").assertExists() }.isSuccess
    }
    compose.onNodeWithText("Played film").performClick()
    assertEquals(
      "https://example.invalid/played.mp4",
      shadowOf(compose.activity).nextStartedActivity.dataString,
    )
  }

  @Test
  fun openFileUsesTheSystemDocumentPicker() {
    theme { HomeQuickActions(true) }
    compose.onNodeWithContentDescription("Playback actions").performClick()
    compose.onNodeWithText("Open File").performClick()
    compose.waitForIdle()
    val request = shadowOf(compose.activity).nextStartedActivityForResult
    assertEquals(android.content.Intent.ACTION_OPEN_DOCUMENT, request.intent.action)
  }

  @Test
  fun clearingHistoryRemovesItsEntriesFromTheRecentSheet() {
    RecentMedia.record(compose.activity, "https://example.invalid/x.mp4", "Old film")
    RecentMedia.clear(compose.activity)
    theme { HomeQuickActions(true) }
    compose.onNodeWithContentDescription("Playback actions").performClick()
    compose.onNodeWithText("Recently Played").performClick()
    compose.onNodeWithText("Old film").assertDoesNotExist()
    compose.onNodeWithText("Your played videos will appear here.").assertIsDisplayed()
  }

  private fun capture(name: String) {
    compose.mainClock.advanceTimeBy(600)
    compose.waitForIdle()
    var bitmap: Bitmap? = null
    compose.runOnUiThread {
      val view = compose.activity.findViewById<View>(android.R.id.content)
      val node = android.graphics.RenderNode("modern-ui")
      node.setPosition(0, 0, view.width, view.height)
      val canvas = node.beginRecording(view.width, view.height)
      android.view.inspector.WindowInspector.getGlobalWindowViews()
        .filter { it.isShown }
        .forEach { window ->
          val location = IntArray(2)
          window.getLocationOnScreen(location)
          val saved = canvas.save()
          canvas.translate(location[0].toFloat(), location[1].toFloat())
          window.draw(canvas)
          canvas.restoreToCount(saved)
        }
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
    File("build/reports/modern-panels/$name.png")
      .apply { parentFile?.mkdirs() }
      .outputStream()
      .use { requireNotNull(bitmap).compress(Bitmap.CompressFormat.PNG, 100, it) }
    bitmap?.recycle()
  }
}
