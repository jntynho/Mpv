package live.mehiz.mpvkt.ui.motion

import org.junit.Assert.*
import org.junit.Test

class MiuixSpringTest {
  @Test fun dampingMatchesMiuixAndTouchHandoffIsContinuous() {
    for (range in listOf(400f, 1000f)) for (fraction in listOf(.01f, .1f, .3f, .6f)) {
      val distance = SpringMath.obtainDampingDistance(fraction, range)
      assertEquals(fraction * range, SpringMath.obtainTouchDistance(distance, range), .01f)
      assertTrue(distance <= fraction * range)
    }
  }
  @Test fun springSettlesForFastAndSlowFlingsWithoutNonFiniteValues() {
    for (velocity in listOf(-20000f, -500f, 0f, 500f, 20000f)) {
      val engine = SpringEngine()
      engine.start(60f, 0f, velocity)
      var settled = false
      for (frame in 0..2000) {
        if (engine.step(.016f)) { settled = true; break }
        assertTrue(engine.currentPos.isFinite())
      }
      assertTrue(settled)
      assertEquals(0.0, engine.currentPos, .001)
    }
  }
}
