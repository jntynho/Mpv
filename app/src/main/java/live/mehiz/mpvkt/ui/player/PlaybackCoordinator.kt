package live.mehiz.mpvkt.ui.player

import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal interface PlaybackEngine {
  fun paused(): Boolean?
  fun buffering(): Boolean
  fun ended(): Boolean
  fun setPaused(value: Boolean)
  fun restart()
}

internal data class PlaybackSnapshot(
  val attached: Boolean = false,
  val ready: Boolean = false,
  val wantsPlay: Boolean = true,
  val focusBlocked: Boolean = false,
  val seeking: Boolean = false,
  val confirmedPaused: Boolean? = null,
  val buffering: Boolean = false,
  val ended: Boolean = false,
  val pending: Boolean = false,
  val restarting: Boolean = false,
  val commandMillis: Long = 0,
) {
  val displayPaused get() = !wantsPlay || focusBlocked || ended
  val isBuffering get() = !displayPaused && (buffering || restarting || !ready)
}

/** User intent is separate from temporary focus/seek pauses. JNI never runs on the caller. */
internal class PlaybackCoordinator(
  private val engine: PlaybackEngine,
  private val changed: (PlaybackSnapshot) -> Unit = {},
  private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) : AutoCloseable {
  private val stateLock = Any()
  private val engineLock = Any()
  private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "MPV-playback-control").apply { isDaemon = true } }
  private val mutable = MutableStateFlow(PlaybackSnapshot(attached = true))
  val state = mutable.asStateFlow()
  @Volatile private var closed = false
  @Volatile private var revision = 0L
  private val actionVersions = ConcurrentHashMap<String, AtomicLong>()

  private fun update(transform: (PlaybackSnapshot) -> PlaybackSnapshot) = synchronized(stateLock) {
    if (!closed) transform(mutable.value).also { mutable.value = it; changed(it) } else mutable.value
  }

  fun pause() { update { revision++; it.copy(wantsPlay = false, pending = true) }; reconcile() }
  fun play(focusGranted: Boolean) {
    val restart = state.value.ended || state.value.restarting
    update { revision++; it.copy(wantsPlay = focusGranted, focusBlocked = !focusGranted,
      ended = if (focusGranted) false else it.ended, restarting = restart && focusGranted, pending = true) }
    reconcile(restart && focusGranted)
  }
  fun toggle(requestFocus: () -> Boolean) {
    if (state.value.displayPaused) play(requestFocus()) else pause()
  }
  fun focusLost(transient: Boolean) {
    update { revision++; it.copy(focusBlocked = true, wantsPlay = if (transient) it.wantsPlay else false, pending = true) }
    reconcile()
  }
  fun focusGained() {
    if (!state.value.focusBlocked) return
    update { revision++; it.copy(focusBlocked = false, pending = true) }; reconcile()
  }
  fun beginSeek() { update { revision++; it.copy(seeking = true, pending = true) }; reconcile() }
  fun endSeek() { update { revision++; it.copy(seeking = false, pending = true) }; reconcile() }
  fun mediaStarted() {
    update { revision++; it.copy(ready = false, wantsPlay = if (it.ended) true else it.wantsPlay, ended = false, buffering = false, restarting = false, confirmedPaused = null, pending = false) }
    if (state.value.focusBlocked || state.value.seeking || !state.value.wantsPlay) reconcile() else refresh()
  }
  fun mediaLoaded() { update { it.copy(ready = true) }; refresh() }

  private fun submit(block: () -> Unit) {
    if (closed) return
    try { executor.execute(block) } catch (_: RejectedExecutionException) { /* Closing this engine. */ }
  }

  private fun reconcile(restart: Boolean = false) {
    val version = revision
    val snapshot = state.value
    val target = snapshot.displayPaused || snapshot.seeking
    val start = clock()
    submit {
      var paused: Boolean? = null
      var cache = false
      var failed = false
      synchronized(engineLock) {
        if (closed || version != revision) return@submit
        try {
          if (restart || (snapshot.restarting && snapshot.wantsPlay && !target)) engine.restart()
          engine.setPaused(target)
          paused = engine.paused()
          cache = engine.buffering()
        } catch (_: Exception) { failed = true }
      }
      update {
        if (version != revision) it else it.copy(
          confirmedPaused = paused, buffering = cache, pending = false,
          wantsPlay = if (failed) false else it.wantsPlay,
          commandMillis = (clock() - start).coerceAtLeast(0),
        )
      }
    }
  }

  /** Probe current native state, rather than replaying stale asynchronously delivered values. */
  fun refresh() {
    val version = revision
    submit {
      var paused: Boolean? = null
      var cache = false
      var eof = false
      synchronized(engineLock) {
        if (closed || version != revision) return@submit
        try { paused = engine.paused(); cache = engine.buffering(); eof = engine.ended() }
        catch (_: Exception) { return@submit }
      }
      update { s ->
        if (version != revision) s else {
          val ended = eof && s.ready && !s.restarting
          val wants = when {
            ended -> false
            s.pending || s.seeking || s.focusBlocked -> s.wantsPlay
            paused != null -> !paused!!
            else -> s.wantsPlay
          }
          s.copy(confirmedPaused = paused, buffering = cache, ended = ended,
            wantsPlay = wants, restarting = s.restarting && eof)
        }
      }
    }
  }

  /** Keyed controls (volume/seek) coalesce queued values, without a debounce delay. */
  fun enqueue(key: String? = null, action: () -> Unit) {
    val counter = key?.let { actionVersions.getOrPut(it) { AtomicLong() } }
    val version = counter?.incrementAndGet()
    submit {
      synchronized(engineLock) {
        if (closed || (counter != null && counter.get() != version)) return@submit
        try { action() } catch (_: Exception) { /* Engine/file may have changed. */ }
      }
    }
  }

  override fun close() {
    synchronized(stateLock) { closed = true; revision++ }
    executor.shutdownNow()
    // Destruction waits only for the JNI operation already in flight, never for queued work.
    synchronized(engineLock) { }
  }
}
