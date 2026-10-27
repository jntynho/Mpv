package live.mehiz.mpvkt.ui.home

import androidx.compose.ui.unit.*
import org.junit.Assert.*
import org.junit.Test

class SortMenuPositionTest {
  @Test fun pointerUsesTheRealSortButtonAndPositionIsIndependentOfLanguage() {
    var pointer = 0f
    val provider = SortMenuPositionProvider(12, 2) { pointer = it }
    val anchor = IntRect(280, 24, 328, 72)
    val offset = provider.calculatePosition(anchor, IntSize(400, 800), LayoutDirection.Ltr, IntSize(168, 330))
    assertEquals(anchor.center.x.toFloat(), offset.x + pointer, 0f)
    assertEquals(74, offset.y)
    assertEquals(offset, provider.calculatePosition(anchor, IntSize(400, 800), LayoutDirection.Rtl, IntSize(168, 330)))
  }

  @Test fun popupStaysOnScreenAtEitherWindowEdgeAndInAShortWindow() {
    val provider = SortMenuPositionProvider(12, 2) { }
    for (anchor in listOf(IntRect(0, 0, 48, 48), IntRect(352, 0, 400, 48), IntRect(280, 240, 328, 288))) {
      val popup = IntSize(168, 250)
      val offset = provider.calculatePosition(anchor, IntSize(400, 320), LayoutDirection.Ltr, popup)
      assertTrue(offset.x >= 12)
      assertTrue(offset.y >= 12)
      assertTrue(offset.x + popup.width <= 388)
      assertTrue(offset.y + popup.height <= 308)
    }
  }
  @Test fun unmeasuredWindowNeverProducesNegativePopupConstraints() {
    val size = sortMenuMaxSize(IntSize.Zero, Density(3f), 0)
    assertTrue(size.width.value > 0f)
    assertTrue(size.height.value > 0f)
    assertTrue(size.width <= 280.dp)
  }

  @Test fun popupBoundsAdaptToNarrowAndShortWindows() {
    for (window in listOf(IntSize(180, 140), IntSize(400, 320), IntSize(1080, 1920))) {
      val density = Density(1f)
      val size = sortMenuMaxSize(window, density, window.height - 48)
      assertTrue(size.width.value <= window.width - 24)
      assertTrue(size.height.value <= window.height - 24)
      assertTrue(size.width.value > 0f && size.height.value > 0f)
    }
  }
}
