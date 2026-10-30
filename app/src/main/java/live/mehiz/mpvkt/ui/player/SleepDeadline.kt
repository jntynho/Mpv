package live.mehiz.mpvkt.ui.player

/** Absolute monotonic deadline: delayed ticks cannot accumulate timing drift. */
internal data class SleepDeadline(val expiresAt: Long) {
  fun millisecondsAt(now: Long) = (expiresAt - now).coerceAtLeast(0L)

  fun secondsAt(now: Long) =
    ((millisecondsAt(now) + 999L) / 1000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

  companion object {
    fun after(seconds: Int, now: Long) =
      SleepDeadline(now + seconds.coerceAtLeast(0).toLong() * 1000L)
  }
}
