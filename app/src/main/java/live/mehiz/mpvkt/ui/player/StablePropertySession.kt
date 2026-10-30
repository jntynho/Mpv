package live.mehiz.mpvkt.ui.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** JNI runs on the supplied worker, never while holding the UI-facing state lock. */
internal class StablePropertySession(private val dispatch: (() -> Unit) -> Unit) {
  private val lock = Any()
  private val channels = mutableListOf<Channel<*>>()
  private val observed = hashSetOf<Pair<String, Int>>()
  private var observe: ((String, Int) -> Unit)? = null
  private var generation = 0L

  inner class Channel<T>
  internal constructor(private val format: Int, private val read: (String) -> T?) {
    private val values = linkedMapOf<String, MutableStateFlow<T?>>()
    private val revisions = mutableMapOf<String, Long>()
    private val scheduled = mutableMapOf<String, Long>()

    operator fun get(name: String): StateFlow<T?> {
      var created = false
      val flow =
        synchronized(lock) {
          values.getOrPut(name) {
            created = true
            MutableStateFlow(null)
          }
        }
      if (created) bind(name)
      return flow
    }

    internal fun cached(name: String): T? = synchronized(lock) { values[name]?.value }

    internal fun emit(name: String, value: T?) =
      synchronized(lock) {
        revisions[name] = (revisions[name] ?: 0L) + 1
        values[name]?.value = value
      }

    internal fun invalidate(name: String) {
      emit(name, null)
    }

    internal fun reset() {
      scheduled.clear()
      values.keys.toList().forEach { emit(it, null) }
    }

    internal fun bindAll() {
      val names = synchronized(lock) { values.keys.toList() }
      names.forEach(::bind)
    }

    private fun bind(name: String) {
      val binding =
        synchronized(lock) {
          val callback = observe ?: return
          if (scheduled[name] == generation) return
          scheduled[name] = generation
          Triple(generation, callback, revisions[name] ?: 0L)
        }
      dispatch {
        val shouldObserve =
          synchronized(lock) {
            if (observe == null || generation != binding.first) return@dispatch
            observed.add(name to format)
          }
        val value =
          runCatching {
              if (shouldObserve) binding.second(name, format)
              read(name)
            }
            .getOrNull()
        synchronized(lock) {
          // An event newer than the initial snapshot must win, including an unavailable event.
          if (
            observe != null &&
              generation == binding.first &&
              (revisions[name] ?: 0L) == binding.third
          )
            values[name]?.value = value
        }
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

  fun bindAll() {
    val snapshot = synchronized(lock) { if (observe == null) emptyList() else channels.toList() }
    snapshot.forEach { it.bindAll() }
  }

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
