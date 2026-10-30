package live.mehiz.mpvkt.ui.player

import live.mehiz.mpvkt.preferences.SpeedPresets
import live.mehiz.mpvkt.presentation.components.*
import live.mehiz.mpvkt.ui.player.controls.components.panels.DelayMeasurement
import org.junit.Assert.*
import org.junit.Test

class PanelDomainTest {
  @Test
  fun numbersRejectInvalidValuesAndDoNotOverflowAtTheEdges() {
    assertNull(validDecimal("99", .1f, 10f))
    assertNull(validDecimal("NaN", .1f, 10f))
    assertNull(validDecimal("Infinity", .1f, 10f))
    assertNull(validInteger("2147483648", Int.MIN_VALUE, Int.MAX_VALUE))
    assertEquals(1.25f, validDecimal("١٫٢٥", .1f, 10f)!!, 0f)
    assertEquals(-25, validInteger("−٢٥", -100, 100))
    assertEquals(Int.MAX_VALUE, steppedInteger(Int.MAX_VALUE, 50L, Int.MIN_VALUE, Int.MAX_VALUE))
    assertEquals(Int.MIN_VALUE, steppedInteger(Int.MIN_VALUE, -50L, Int.MIN_VALUE, Int.MAX_VALUE))
  }

  @Test
  fun speedPresetsHaveFiniteCanonicalUniqueKeys() {
    assertThrows(IllegalStateException::class.java) { SpeedPresets.checked(setOf("bad")) }
    assertNull(SpeedPresets.parse("NaN"))
    assertEquals(8f, SpeedPresets.parse("8")!!, 0f)
    assertEquals(
      listOf(1f, 1.5f),
      SpeedPresets.safe(setOf("1.0", "1.00", "1.5", "bad"), emptySet()),
    )
    assertEquals(listOf(1f), SpeedPresets.safe(setOf("bad"), setOf("1.0")))
  }

  @Test
  fun delayMeasurementUsesItsOwnStartAndClampsWithoutWrapping() {
    val first = DelayMeasurement(1000L, 100, false)
    assertEquals(50, first.valueAt(1050L))
    assertEquals(100, first.valueAt(900L))
    assertEquals(300, DelayMeasurement(2000L, 300, true).valueAt(2000L))
    assertEquals(Int.MAX_VALUE, DelayMeasurement(0L, Int.MAX_VALUE, true).valueAt(1000))
  }

  @Test
  fun timerDoesNotAccumulateMissedTicksOrDiscardSeconds() {
    val deadline = SleepDeadline.after(59, 1000L)
    assertEquals(59, deadline.secondsAt(1000L))
    assertEquals(40, deadline.secondsAt(20001L))
    assertEquals(1, deadline.secondsAt(59999L))
    assertEquals(0, deadline.secondsAt(60000L))
    assertEquals(0, deadline.secondsAt(90000L))
  }

  @Test
  fun subtitleTargetIsPartOfTheRouteIdentity() {
    val navigator = PlayerPanelNavigator()
    navigator.openSheet(Sheets.SubtitleTracks)
    navigator.openSubtitlePanel(Panels.SubtitleDelay, 1)
    val secondary = navigator.state.value.last()
    assertEquals(1, secondary.subtitleSlot)
    navigator.back()
    navigator.openSubtitlePanel(Panels.SubtitleDelay, 0)
    assertNotEquals(secondary.key, navigator.state.value.last().key)
    assertEquals(0, navigator.state.value.last().subtitleSlot)
  }
}
