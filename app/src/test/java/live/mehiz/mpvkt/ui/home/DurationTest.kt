package live.mehiz.mpvkt.ui.home
import org.junit.Assert.assertEquals
import org.junit.Test
class DurationTest {
 @Test fun formatsLongVideosWithoutWrappingMinutes() {
  assertEquals("25:02", formatDuration(1502000))
  assertEquals("10:25:53", formatDuration(37553000))
  assertEquals("0:00", formatDuration(-1))
 }
}
