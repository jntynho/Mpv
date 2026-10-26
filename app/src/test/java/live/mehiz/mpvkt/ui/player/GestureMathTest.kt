package live.mehiz.mpvkt.ui.player

import live.mehiz.mpvkt.ui.player.controls.pinchZoom
import live.mehiz.mpvkt.ui.player.controls.components.percentage
import org.junit.Assert.assertEquals
import org.junit.Test

class GestureMathTest {
  @Test fun zoomIsMultiplicativeAndReversible() {
    assertEquals(1f, pinchZoom(0f, 2f), .0001f)
    assertEquals(0f, pinchZoom(1f, .5f), .0001f)
  }
  @Test fun zoomRejectsInvalidInputAndStaysBounded() {
    assertEquals(3f, pinchZoom(2f, 100f), 0f)
    assertEquals(-1f, pinchZoom(0f, .001f), 0f)
    assertEquals(0f, pinchZoom(0f, Float.NaN), 0f)
    assertEquals(0f, pinchZoom(Float.NaN, 1f), 0f)
  }
  @Test fun slidersHandleFixedVolumeDevicesAndOutOfRangeValues() {
    assertEquals(0f, percentage(0, 0..0), 0f)
    assertEquals(0f, percentage(Float.NaN, 0f..1f), 0f)
    assertEquals(1f, percentage(2f, 0f..1f), 0f)
    assertEquals(0f, percentage(-1f, 0f..1f), 0f)
    assertEquals(.5f, percentage(5, 0..10), 0f)
  }
}
