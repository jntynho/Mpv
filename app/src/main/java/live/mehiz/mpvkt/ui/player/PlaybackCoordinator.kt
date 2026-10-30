package live.mehiz.mpvkt.ui.player

import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal interface PlaybackEngine {
  fun paused(): Boolean?

  fun buffering(): Boolean

  fun ended(): Boolean

  fun setPaused(value: Boolean)

  fun restart()

  fun positionSeconds(): Double? = null
}

internal enum class PlaybackFailure {
  Unconfirmed,
  NativeError,
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
  val failure: PlaybackFailure? = null,
  val pausePositionSeconds: Double? = null,
  val pauseAdvanceMillis: Double? = null,
) {
  // A failed request must not present the desired state as a confirmed native pause.
  val displayPaused
    get() =
      if (failure != null && confirmedPaused != null) confirmedPaused
      else !wantsPlay || focusBlocked || ended

  // Native-confirmed glyph while a manual command is pending; temporary seeking remains
  // intent-based.
  val iconPaused
    get() = if (pending && !seeking) confirmedPaused ?: displayPaused else displayPaused

  val isBuffering
    get() = !displayPaused && (buffering || restarting || !ready)

  internal val needsPause
    get() = !wantsPlay || focusBlocked || seeking || ended
}

/**
 * Single JNI owner, priority control lane, acknowledged pause barrier, and bounded confirmation.
 */
internal class PlaybackCoordinator(
  private val engine: PlaybackEngine,
  private val changed: (PlaybackSnapshot) -> Unit = {},
  private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
  private val confirmationTimeoutMillis: Long = 900,
  private val confirmationIntervalMillis: Long = 35,
) : AutoCloseable {
  private val stateLock = Any()
  private val engineLock = Any()
  private val sequence = AtomicLong()

  private class Work(val priority: Int, val sequence: Long, val action: () -> Unit) :
    Runnable, Comparable<Work> {
    override fun run() = action()

    override fun compareTo(other: Work) =
      compareValues(priority, other.priority).takeIf { it != 0 }
        ?: sequence.compareTo(other.sequence)
  }

  private val executor =
    ThreadPoolExecutor(
      1,
      1,
      0,
      TimeUnit.MILLISECONDS,
      PriorityBlockingQueue<Runnable>(),
      { r -> Thread(r, "MPV-playback-control").apply { isDaemon = true } },
    )
  private val timer =
    ScheduledThreadPoolExecutor(1) { r ->
        Thread(r, "MPV-playback-confirm").apply { isDaemon = true }
      }
      .apply { removeOnCancelPolicy = true }
  private val mutable = MutableStateFlow(PlaybackSnapshot(attached = true))
  val state = mutable.asStateFlow()
  @Volatile private var closed = false
  private var revision = 0L
  private var generation = 0L
  private var seekSession = 0L
  private var pauseBarrier = false
  private var controlQueued = false
  private var refreshQueued = false
  private var retry: ScheduledFuture<*>? = null
  private var requestedAt = 0L
  private var requestedPosition: Double? = null
  @Volatile private var observedPosition: Double? = null
  private var attemptedRevision = -1L
  private var attemptedTarget: Boolean? = null
  private var attempts = 0
  private var restarted = false
  private var lastNativeError = false
  private val keyed = mutableMapOf<String, Work>()

  private fun publish(snapshot: PlaybackSnapshot) {
    mutable.value = snapshot
    changed(snapshot)
  }

  private fun request(stop: Boolean = false, transform: (PlaybackSnapshot) -> PlaybackSnapshot) =
    synchronized(stateLock) {
      if (closed) return@synchronized
      revision++
      requestedAt = clock()
      if (stop) {
        pauseBarrier = true
        requestedPosition = observedPosition
      }
      attempts = 0
      lastNativeError = false
      retry?.cancel(false)
      retry = null
      publish(transform(state.value).copy(pending = true, failure = null))
      scheduleControl()
    }

  fun pause() = request(stop = true) { it.copy(wantsPlay = false) }

  fun play(focusGranted: Boolean) =
    request(stop = !focusGranted) {
      it.copy(
        wantsPlay = focusGranted,
        focusBlocked = !focusGranted,
        ended = if (focusGranted) false else it.ended,
        restarting = (it.ended || it.restarting) && focusGranted,
      )
    }

  fun toggle(requestFocus: () -> Boolean) {
    if (state.value.displayPaused) play(requestFocus()) else pause()
  }

  fun focusLost(transient: Boolean) =
    request(stop = true) {
      it.copy(focusBlocked = true, wantsPlay = if (transient) it.wantsPlay else false)
    }

  fun focusGained() {
    if (state.value.focusBlocked) request { it.copy(focusBlocked = false) }
  }

  fun beginSeek() =
    synchronized(stateLock) {
      seekSession++
      request(stop = true) { it.copy(seeking = true) }
    }

  // Resume only after the last queued seek has run. A newer gesture invalidates an older release.
  fun endSeek() =
    synchronized(stateLock) {
      val session = seekSession
      enqueue("end-seek") {
        synchronized(stateLock) { if (session == seekSession) request { it.copy(seeking = false) } }
      }
    }

  fun positionChanged(seconds: Double) {
    if (seconds.isFinite()) observedPosition = seconds
  }

  fun mediaStarted() =
    synchronized(stateLock) {
      if (closed) return@synchronized
      generation++
      keyed.values.forEach(executor::remove)
      keyed.clear()
      seekSession++
      revision++
      observedPosition = null
      requestedPosition = null
      restarted = false
      retry?.cancel(false)
      retry = null
      val next =
        state.value.copy(
          ready = false,
          seeking = false,
          wantsPlay = if (state.value.ended) true else state.value.wantsPlay,
          ended = false,
          buffering = false,
          restarting = false,
          confirmedPaused = null,
          failure = null,
          pausePositionSeconds = null,
          pauseAdvanceMillis = null,
        )
      pauseBarrier = next.needsPause
      requestedAt = clock()
      attempts = 0
      publish(next.copy(pending = next.needsPause))
      if (next.needsPause) scheduleControl() else refresh()
    }

  fun mediaLoaded() =
    synchronized(stateLock) {
      if (closed) return@synchronized
      publish(state.value.copy(ready = true))
      if (state.value.pending || pauseBarrier) scheduleControl() else refresh()
    }

  private fun submit(priority: Int, action: () -> Unit): Work? {
    if (closed) return null
    val work = Work(priority, sequence.incrementAndGet(), action)
    return try {
      executor.execute(work)
      work
    } catch (_: RejectedExecutionException) {
      null
    }
  }

  // stateLock is reentrant; at most one control pass and one delayed confirmation are queued.
  private fun scheduleControl() =
    synchronized(stateLock) {
      if (closed || controlQueued) return@synchronized
      controlQueued = true
      submit(0) {
        synchronized(stateLock) { controlQueued = false }
        controlPass()
      }
    }

  private fun controlPass() {
    synchronized(engineLock) {
      val version: Long
      val media: Long
      val target: Boolean
      val restart: Boolean
      val write: Boolean
      synchronized(stateLock) {
        if (closed || !state.value.pending) return
        version = revision
        media = generation
        target = pauseBarrier || state.value.needsPause
        restart = !target && state.value.restarting && !restarted
        if (attemptedRevision != version || attemptedTarget != target) {
          attempts = 0
          attemptedRevision = version
          attemptedTarget = target
        }
        write = attempts < 3
        if (write) attempts++
      }
      var paused: Boolean? = null
      var position: Double? = null
      try {
        if (restart) {
          engine.restart()
          synchronized(stateLock) { if (generation == media) restarted = true }
        }
        if (write) engine.setPaused(target)
        paused = engine.paused()
        if (paused == true) position = engine.positionSeconds()
      } catch (_: Exception) {
        synchronized(stateLock) { lastNativeError = true }
      }
      synchronized(stateLock) {
        if (closed || generation != media) return
        // A newer command is still pending; this real pause acknowledgement also satisfies its
        // barrier.
        if (paused == true && pauseBarrier) pauseBarrier = false
        val latest = state.value
        val desired = latest.needsPause
        val satisfied = paused != null && paused == desired && !pauseBarrier
        val valid = version == revision
        val measuredPosition = position?.takeIf { it.isFinite() }
        val base =
          latest.copy(
            confirmedPaused = paused ?: latest.confirmedPaused,
            pausePositionSeconds = measuredPosition ?: latest.pausePositionSeconds,
            pauseAdvanceMillis =
              if (measuredPosition != null && requestedPosition != null)
                (measuredPosition - requestedPosition!!) * 1000
              else latest.pauseAdvanceMillis,
          )
        when {
          valid && satisfied -> {
            retry?.cancel(false)
            retry = null
            publish(
              base.copy(
                pending = false,
                failure = null,
                commandMillis = (clock() - requestedAt).coerceAtLeast(0),
              )
            )
          }
          !valid || (target && paused == true && !desired) -> {
            publish(base)
            scheduleControl()
          }
          clock() - requestedAt >= confirmationTimeoutMillis -> {
            retry?.cancel(false)
            retry = null
            pauseBarrier = false
            publish(
              base.copy(
                pending = false,
                failure =
                  if (lastNativeError) PlaybackFailure.NativeError else PlaybackFailure.Unconfirmed,
                commandMillis = (clock() - requestedAt).coerceAtLeast(0),
              )
            )
          }
          else -> {
            publish(base)
            retry?.cancel(false)
            retry =
              timer.schedule(
                { scheduleControl() },
                confirmationIntervalMillis,
                TimeUnit.MILLISECONDS,
              )
          }
        }
      }
    }
  }

  /** Coalesced status reads cannot crowd urgent pause or overwrite unacknowledged user intent. */
  fun refresh() =
    synchronized(stateLock) {
      if (closed || refreshQueued) return@synchronized
      refreshQueued = true
      val media = generation
      submit(5) {
        synchronized(stateLock) { refreshQueued = false }
        synchronized(engineLock) {
          if (closed) return@submit
          val paused: Boolean?
          val cache: Boolean
          val eof: Boolean
          try {
            paused = engine.paused()
            cache = engine.buffering()
            eof = engine.ended()
          } catch (_: Exception) {
            return@submit
          }
          synchronized(stateLock) {
            if (closed || generation != media) return@synchronized
            val s = state.value
            val ended = eof && s.ready && !s.restarting
            val wants =
              when {
                ended -> false
                s.pending || s.seeking || s.focusBlocked || s.failure != null -> s.wantsPlay
                paused != null -> !paused
                else -> s.wantsPlay
              }
            publish(
              s.copy(
                confirmedPaused = paused,
                buffering = cache,
                ended = ended,
                wantsPlay = wants,
                restarting = s.restarting && eof,
              )
            )
            if (!eof) restarted = false
            if (s.pending) scheduleControl()
          }
        }
      }
    }

  /** Replace queued keyed work physically, not just when it eventually reaches the worker. */
  fun enqueue(key: String? = null, action: () -> Unit) = enqueueWork(key, true, action)

  /** Property subscriptions belong to the core, so a new media file must not discard them. */
  fun enqueueSession(action: () -> Unit) = enqueueWork(null, false, action)

  private fun enqueueWork(key: String?, mediaBound: Boolean, action: () -> Unit) =
    synchronized(stateLock) {
      if (closed) return@synchronized
      key?.let { keyed.remove(it)?.let(executor::remove) }
      val media = generation
      var work: Work? = null
      work =
        submit(10) {
          synchronized(engineLock) {
            synchronized(stateLock) {
              if (
                closed ||
                  (mediaBound && generation != media) ||
                  (key != null && keyed[key] !== work)
              )
                return@submit
              if (key != null) keyed.remove(key)
            }
            try {
              action()
            } catch (_: Exception) {
              /* Engine/file may have changed. */
            }
          }
        }
      if (key != null && work != null) keyed[key] = work
    }

  override fun close() {
    synchronized(stateLock) {
      closed = true
      generation++
      retry?.cancel(false)
      keyed.clear()
    }
    timer.shutdownNow()
    executor.shutdownNow()
    synchronized(
      engineLock
    ) {} // Only the single native call already in flight can delay destruction.
  }
}
