package live.mehiz.mpvkt.ui.player.controls.components.panels

/** A monotonic, target-owned measurement; no polling ever writes to the player. */
internal data class DelayMeasurement(val startedAt: Long, val initial: Int, val positive: Boolean) {
  fun valueAt(now: Long): Int {
    val elapsed = (now - startedAt).coerceAtLeast(0)
    return (initial.toLong() + if (positive) elapsed else -elapsed)
      .coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())
      .toInt()
  }
}
