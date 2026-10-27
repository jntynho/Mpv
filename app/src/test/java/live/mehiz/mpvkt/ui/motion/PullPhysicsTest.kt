package live.mehiz.mpvkt.ui.motion

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class PullPhysicsTest {
  @Test fun dragResistanceIsSymmetricMonotonicAndBounded() {
    var previous = 0f
    for (raw in 1..10000) {
      val offset = dampedPull(raw.toFloat(), 144f)
      assertTrue(offset >= previous)
      assertTrue(offset < 144f)
      assertTrue(offset < raw)
      assertEquals(-offset, dampedPull(-raw.toFloat(), 144f), .001f)
      previous = offset
    }
  }
  @Test fun grabbingASettlingHeaderDoesNotJump() {
    for (offset in listOf(-80f, -40f, 0f, 40f, 80f, 140f)) {
      assertEquals(offset, dampedPull(rawPull(offset, 144f), 144f), .001f)
    }
  }
  @Test fun badGeometryCannotProduceNaNOrInfinity() {
    for (raw in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) assertEquals(0f, dampedPull(raw, 144f), 0f)
    assertEquals(0f, dampedPull(50f, 0f), 0f)
    assertEquals(0f, rawPull(50f, 0f), 0f)
    assertTrue(abs(rawPull(144f, 144f)).isFinite())
  }
}
