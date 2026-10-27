package live.mehiz.mpvkt.ui.motion

import kotlin.math.abs

internal fun dampedPull(raw: Float, limit: Float): Float =
  if (!raw.isFinite() || !limit.isFinite() || limit <= 0f) 0f
  else raw / (abs(raw) + limit / .65f) * limit

internal fun rawPull(offset: Float, limit: Float): Float {
  if (!offset.isFinite() || !limit.isFinite() || limit <= 0f) return 0f
  val fraction = (offset / limit).coerceIn(-.9999f, .9999f)
  return fraction * limit / (.65f * (1f - abs(fraction)))
}
