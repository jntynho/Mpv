package live.mehiz.mpvkt.ui.icons

import live.mehiz.mpvkt.ui.player.mediaControlIcons
import org.junit.Assert.*
import org.junit.Test

class IconCatalogTest {
  @Test fun everyControlHasTwoDistinctStyleResources() {
    IconKey.entries.forEach { key ->
      assertTrue(key.name, key.drawable(IconStyle.Outline) != 0)
      assertTrue(key.name, key.drawable(IconStyle.Broken) != 0)
      assertNotEquals(key.name, key.drawable(IconStyle.Outline), key.drawable(IconStyle.Broken))
    }
  }

  @Test fun directionSensitiveControlsUseRtlVariantsWithoutReversingMediaTime() {
    IconStyle.entries.forEach { style ->
      listOf(IconKey.ArrowBack, IconKey.FormatAlignLeft, IconKey.FormatAlignRight).forEach { key ->
        assertNotEquals(key.drawable(style), key.drawable(style, mirrored = true))
      }
      listOf(IconKey.Backward, IconKey.Forward, IconKey.Play).forEach { key ->
        assertEquals(key.drawable(style), key.drawable(style, mirrored = true))
      }
    }
  }

  @Test fun nativeMediaControlsSwitchAsOneStyleWithoutRetainingThePreviousSnapshot() {
    val outline = mediaControlIcons(IconStyle.Outline)
    val broken = mediaControlIcons(IconStyle.Broken)
    assertNotEquals(outline.rewind, broken.rewind)
    assertNotEquals(outline.play, broken.play)
    assertNotEquals(outline.pause, broken.pause)
    assertNotEquals(outline.forward, broken.forward)
    assertNotEquals(outline.stop, broken.stop)
    assertEquals(outline, mediaControlIcons(IconStyle.Outline))
  }
}
