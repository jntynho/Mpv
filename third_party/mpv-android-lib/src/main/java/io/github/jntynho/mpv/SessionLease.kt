package io.github.jntynho.mpv

/** Prevent native destruction while a caller still owns a command/property access. */
internal class SessionLease<T : Any> {
  private class Entry<T>(val owner: T, var calls: Int = 0)
  private val monitor = Object()
  @Volatile private var entry: Entry<T>? = null
  val current: T? get() = entry?.owner

  fun bind(owner: T) = synchronized(monitor) {
    check(entry == null) { "A playback session is already bound" }
    entry = Entry(owner)
  }

  fun <R> use(action: (T) -> R): R? {
    val acquired = synchronized(monitor) {
      val value = entry ?: return null
      value.calls++
      value
    }
    return try { action(acquired.owner) }
    finally {
      synchronized(monitor) {
        acquired.calls--
        if (acquired.calls == 0) monitor.notifyAll()
      }
    }
  }

  fun release(owner: T) = synchronized(monitor) {
    val previous = entry ?: return
    if (previous.owner !== owner) return
    entry = null // Late callbacks cannot acquire the retiring session.
    var interrupted = false
    while (previous.calls != 0) {
      try { monitor.wait() }
      catch (_: InterruptedException) { interrupted = true }
    }
    if (interrupted) Thread.currentThread().interrupt()
  }
}
