package live.mehiz.mpvkt.ui.player.controls.components

fun percentage(value: Float, range: ClosedFloatingPointRange<Float>): Float {
  val span = range.endInclusive - range.start
  return if (!value.isFinite() || span <= 0f) 0f else ((value - range.start) / span).coerceIn(0f, 1f)
}

fun percentage(value: Int, range: ClosedRange<Int>): Float {
  return percentage(value.toFloat(), range.start.toFloat()..range.endInclusive.toFloat())
}

