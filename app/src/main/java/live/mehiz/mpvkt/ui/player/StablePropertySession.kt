package live.mehiz.mpvkt.ui.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Stable application-owned flows are rebound whenever the native player is recreated. */
internal class StablePropertySession {
  private val lock = Any()
  private val channels = mutableListOf<Channel<*>>()
  private val observed = hashSetOf<Pair<String, Int>>()
  private var observe: ((String, Int) -> Unit)? = null
  private var generation = 0L

  inner class Channel<T>
  internal constructor(private val format: Int, private val read: (String) -> T?) {
    private val values = linkedMapOf<String, MutableStateFlow<T?>>()

    operator fun get(name: String): StateFlow<T?> =
      synchronized(lock) {
        values[name]
          ?: MutableStateFlow<T?>(null).also {
            values[name] = it
            if (observe != null) bind(name)
          }
      }

    internal fun cached(name: String): T? = synchronized(lock) { values[name]?.value }

    internal fun emit(name: String, value: T?) {
      values[name]?.value = value
    }

    internal fun invalidate(name: String) {
      values[name]?.value = null
    }

    internal fun reset() {
      values.values.forEach { it.value = null }
    }

    internal fun bindAll() {
      values.keys.toList().forEach(::bind)
    }

    private fun bind(name: String) {
      try {
        if (observed.add(name to format)) observe?.invoke(name, format)
        values[name]?.value = read(name)
      } catch (_: Exception) {
        values[name]?.value = null
      }
    }
  }

  fun <T> channel(format: Int, read: (String) -> T?): Channel<T> =
    synchronized(lock) { Channel(format, read).also { channels += it } }

  fun begin(observe: (String, Int) -> Unit): Long =
    synchronized(lock) {
      check(this.observe == null) { "A property session is already attached" }
      generation++
      this.observe = observe
      observed.clear()
      channels.forEach { it.reset() }
      generation
    }

  fun bindAll() = synchronized(lock) { if (observe != null) channels.forEach { it.bindAll() } }

  fun accept(token: Long, update: () -> Unit) =
    synchronized(lock) { if (observe != null && generation == token) update() }

  fun invalidate(name: String) = synchronized(lock) { channels.forEach { it.invalidate(name) } }

  fun end() =
    synchronized(lock) {
      generation++
      observe = null
      observed.clear()
      channels.forEach { it.reset() }
    }
}
