package live.mehiz.mpvkt.ui.player.controls.components.panels

import org.junit.Assert.*
import org.junit.Test

class SubtitleFileFilterTest {
  @Test
  fun acceptsCaseInsensitiveSubtitleExtensionsButRejectsDisguisedNonSubtitles() {
    assertTrue(isSubtitleFile("Episode.01.AR.SRT"))
    assertTrue(isSubtitleFile("映画.ass"))
    assertFalse(isSubtitleFile("Episode.srt.mp4"))
    assertFalse(isSubtitleFile("Episode.zip"))
    assertFalse(isSubtitleFile("Episode"))
  }
}
