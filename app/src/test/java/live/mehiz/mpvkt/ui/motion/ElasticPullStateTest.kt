package live.mehiz.mpvkt.ui.motion

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class ElasticPullStateTest {
  private class Frames : MonotonicFrameClock {
    private var nanos = 0L
    override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
      yield()
      nanos += 16_666_667L
      return onFrame(nanos)
    }
  }
  @Test fun pullingTheBottomReboundsWithoutRefreshing() = runBlocking(Frames()) {
    var refreshes = 0
    val pull = ElasticPullState(this, 144f, true, threshold = 64f, hold = 56f,
      canRefresh = { true }, onRefresh = { refreshes++ })
    val consumed = pull.connection.onPostScroll(Offset.Zero, Offset(0f, -500f), NestedScrollSource.UserInput)
    assertEquals(-500f, consumed.y, 0f)
    assertTrue(pull.offset < 0f)
    pull.connection.onPreFling(Velocity.Zero)
    repeat(200) { yield() }
    assertEquals(0, refreshes)
    assertEquals(0f, pull.offset, .001f)
    assertFalse(pull.refreshing)
  }
  @Test fun anEmptyRefreshGateStillAllowsBottomStretch() = runBlocking(Frames()) {
    val pull = ElasticPullState(this, 144f, true, canRefresh = { false }, onRefresh = {})
    assertEquals(Offset.Zero, pull.connection.onPostScroll(Offset.Zero, Offset(0f, 200f), NestedScrollSource.UserInput))
    pull.connection.onPostScroll(Offset.Zero, Offset(0f, -100f), NestedScrollSource.UserInput)
    assertTrue(pull.offset < 0f)
    pull.cancel()
  }
  @Test fun finishingAScrollInsideTheListDoesNotCreateAPulse() = runBlocking(Frames()) {
    val pull = ElasticPullState(this, 144f, true)
    pull.connection.onPostFling(Velocity(0f, -2000f), Velocity.Zero)
    repeat(5) { yield() }
    assertEquals(0f, pull.offset, 0f)
  }
  @Test fun flingAtTheBottomHasOneBoundedPulseAndSettles() = runBlocking(Frames()) {
    val pull = ElasticPullState(this, 144f, true)
    pull.connection.onPostFling(Velocity.Zero, Velocity(0f, -8000f))
    var peak = 0f
    repeat(200) { yield(); peak = minOf(peak, pull.offset) }
    assertTrue(peak < -1f)
    assertTrue(peak >= -144f * .18f - .1f)
    assertEquals(0f, pull.offset, .001f)
  }
  @Test fun reducedMotionSkipsTheAutomaticFlingPulse() = runBlocking(Frames()) {
    val pull = ElasticPullState(this, 144f, true, reduceMotion = true)
    pull.connection.onPostFling(Velocity.Zero, Velocity(0f, -8000f))
    repeat(5) { yield() }
    assertEquals(0f, pull.offset, 0f)
  }
}
