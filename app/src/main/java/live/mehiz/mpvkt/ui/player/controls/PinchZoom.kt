package live.mehiz.mpvkt.ui.player.controls

/** mpv video-zoom is logarithmic: +1 means 2x, +3 means 8x. */
fun pinchZoom(current: Float, factor: Float): Float {
  val baseline = if (current.isFinite()) current else 0f
  if (!factor.isFinite() || factor <= 0f) return baseline.coerceIn(-1f, 3f)
  return (baseline + kotlin.math.log2(factor)).coerceIn(-1f, 3f)
}
