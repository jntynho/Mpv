package live.mehiz.mpvkt.ui.player

import live.mehiz.mpvkt.ui.player.controls.*
import org.junit.Assert.*
import org.junit.Test

class LandscapeLayoutTest {
  @Test
  fun movesAreUniqueReorderableAndSurvivePersistence() {
    val original = LandscapeLayout()
    val moved = original.assign(LandscapeControl.More, ControlRegion.BottomLeft)
    assertFalse(LandscapeControl.More in moved.topRight)
    assertEquals(LandscapeControl.More, moved.bottomLeft.last())
    val ordered = moved.reorder(ControlRegion.BottomLeft, moved.bottomLeft.lastIndex, -1)
    assertEquals(ordered, LandscapeLayout.restore(ordered.serialize()))
    assertEquals(11, ControlRegion.entries.flatMap { ordered.buttons(it) }.distinct().size)
    assertTrue(LandscapeControl.More in original.topRight)
    assertFalse(
      ControlRegion.entries.any {
        LandscapeControl.More in ordered.assign(LandscapeControl.More, null).buttons(it)
      }
    )
  }

  @Test
  fun regionLimitsAndDamagedSavedValuesCannotDuplicateOrCrashControls() {
    val full =
      LandscapeLayout(
        topRight = LandscapeControl.entries.take(6),
        bottomRight = emptyList(),
        bottomLeft = emptyList(),
      )
    assertEquals(full, full.assign(LandscapeControl.Aspect, ControlRegion.TopRight))
    assertEquals(full, full.reorder(ControlRegion.TopRight, 0, -1))
    val damaged =
      LandscapeLayout(
          topRight = LandscapeControl.entries,
          bottomRight = listOf(LandscapeControl.More, LandscapeControl.Lock),
          bottomLeft = listOf(LandscapeControl.Lock),
        )
        .normalized()
    val flat = ControlRegion.entries.flatMap { damaged.buttons(it) }
    assertEquals(flat.distinct().size, flat.size)
    assertTrue(ControlRegion.entries.all { damaged.buttons(it).size <= 6 })
    assertTrue(LandscapeControl.Lock in damaged.bottomRight)
    assertEquals(LandscapeLayout(), LandscapeLayout.restore("invalid saved data"))
  }
}
