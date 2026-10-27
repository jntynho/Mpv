package live.mehiz.mpvkt.ui.motion

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

@Stable
internal class ElasticPullState(
  private val scope: CoroutineScope,
  private val limit: Float,
  private val enabled: Boolean,
  val threshold: Float = Float.POSITIVE_INFINITY,
  private val hold: Float = 0f,
  private val canRefresh: () -> Boolean = { false },
  private val onRefresh: (suspend () -> Unit)? = null,
  private val reduceMotion: Boolean = false,
  private val platformOverscroll: Boolean = false,
) {
  var offset by mutableFloatStateOf(0f); private set
  var refreshing by mutableStateOf(false); private set
  private var raw = 0f
  private var job: Job? = null
  private fun interrupt() {
    if (job?.isActive == true) { job?.cancel(); raw = if (platformOverscroll) sign(offset) * SpringMath.obtainTouchDistance(offset, limit) else rawPull(offset, limit) }
  }
  private suspend fun animate(target: Float) {
    if (platformOverscroll && !reduceMotion) {
      SpringEngine().runSettleAnimation(offset, target, 0f, { offset = it }, {})
      offset = target
      return
    }
    AnimationState(offset).animateTo(target, if (reduceMotion) tween(100) else spring(dampingRatio = .88f, stiffness = 320f)) { offset = value }
    offset = target
  }
  fun release() {
    if (!enabled || refreshing || abs(offset) < .1f || job?.isActive == true) return
    if (offset >= threshold && canRefresh() && onRefresh != null) {
      refreshing = true
      job = scope.launch {
        try {
          // Scan immediately; its completion and the visual settling run independently.
          val work = async { onRefresh.invoke() }
          animate(hold)
          work.await()
          delay(160)
        } finally {
          refreshing = false
          animate(0f)
          raw = 0f
        }
      }
    } else job = scope.launch { animate(0f); raw = 0f }
  }
  fun cancel() { job?.cancel() }
  val connection = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
      if (!enabled || refreshing || source != NestedScrollSource.UserInput) return Offset.Zero
      interrupt()
      if (raw != 0f && available.y * raw < 0) {
        val consume = sign(available.y) * minOf(abs(available.y), abs(raw))
        raw += consume; offset = if (platformOverscroll) sign(raw) * SpringMath.obtainDampingDistance(abs(raw) / limit, limit) else dampedPull(raw, limit)
        return Offset(0f, consume)
      }
      return Offset.Zero
    }
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
      if (!enabled || refreshing || source != NestedScrollSource.UserInput || available.y == 0f) return Offset.Zero
      if (platformOverscroll && available.y < 0f) return Offset.Zero
      if (onRefresh != null && available.y > 0f && !canRefresh()) return Offset.Zero
      interrupt()
      raw += available.y; offset = if (platformOverscroll) sign(raw) * SpringMath.obtainDampingDistance(abs(raw) / limit, limit) else dampedPull(raw, limit)
      return Offset(0f, available.y)
    }
    override suspend fun onPreFling(available: Velocity): Velocity {
      if (offset == 0f || refreshing) return Velocity.Zero
      release()
      return Velocity(0f, available.y)
    }
    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
      if (enabled && !platformOverscroll && !reduceMotion && !refreshing && abs(offset) < .1f && job?.isActive != true) {
        val pulse = edgePulse(available.y, limit)
        if (pulse != 0f) {
          job = scope.launch {
            AnimationState(offset).animateTo(pulse, tween(90)) { offset = value }
            animate(0f)
            raw = 0f
          }
          return Velocity(0f, available.y)
        }
      }
      release()
      return Velocity.Zero
    }
  }
}
