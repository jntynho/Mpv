package live.mehiz.mpvkt.ui.player

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class PlaybackCoordinatorTest {
  private class Engine : PlaybackEngine {
    @Volatile var nativePaused = false
    @Volatile var cache = false
    @Volatile var eof = false
    var restarts = 0
    val writes = CopyOnWriteArrayList<Boolean>()
    val threads = CopyOnWriteArrayList<Thread>()

    override fun paused() = nativePaused

    override fun buffering() = cache

    override fun ended() = eof

    override fun setPaused(value: Boolean) {
      threads += Thread.currentThread()
      writes += value
      nativePaused = value
    }

    override fun restart() {
      restarts++
      eof = false
    }
  }

  private fun PlaybackCoordinator.drain() {
    val done = CountDownLatch(1)
    enqueue { done.countDown() }
    assertTrue("Playback queue did not settle", done.await(3, TimeUnit.SECONDS))
  }

  private fun CountDownLatch.awaitChecked() = assertTrue(await(3, TimeUnit.SECONDS))

  @Test
  fun inputIntentIsImmediateButTheGlyphWaitsForNativeConfirmation() {
    val engine = Engine()
    PlaybackCoordinator(engine).use { c ->
      c.refresh()
      c.drain()
      val entered = CountDownLatch(1)
      val release = CountDownLatch(1)
      c.enqueue {
        entered.countDown()
        release.await(3, TimeUnit.SECONDS)
      }
      entered.awaitChecked()
      try {
        c.pause()
        assertTrue(c.state.value.displayPaused)
        assertFalse(c.state.value.iconPaused)
        assertTrue(c.state.value.pending)
        c.toggle { true }
        assertFalse(c.state.value.displayPaused)
        c.toggle { error("Pause must not request focus") }
        assertTrue(c.state.value.displayPaused)
        assertTrue(engine.writes.isEmpty())
      } finally {
        release.countDown()
      }
      c.drain()
      assertEquals(listOf(true), engine.writes.toList())
      assertFalse(c.state.value.pending)
      assertTrue(c.state.value.iconPaused)
      assertTrue(engine.threads.none { it === Thread.currentThread() })
    }
  }

  @Test
  fun focusGainDoesNotUndoManualPauseDuringAnInterruption() {
    val engine = Engine()
    PlaybackCoordinator(engine).use { c ->
      c.focusLost(true)
      c.drain()
      assertTrue(engine.nativePaused)
      assertTrue(c.state.value.wantsPlay)
      c.pause()
      c.focusGained()
      c.drain()
      assertTrue(engine.nativePaused)
      assertFalse(c.state.value.wantsPlay)
    }
  }

  @Test
  fun transientFocusResumesPlayingButPermanentLossDoesNot() {
    val engine = Engine()
    PlaybackCoordinator(engine).use { c ->
      c.focusLost(true)
      c.focusGained()
      c.drain()
      assertFalse(engine.nativePaused)
      c.focusLost(false)
      c.focusGained()
      c.drain()
      assertTrue(engine.nativePaused)
    }
  }

  @Test
  fun finishingSeekCannotResumeWhileFocusIsLost() {
    val engine = Engine()
    PlaybackCoordinator(engine).use { c ->
      c.beginSeek()
      c.drain()
      assertTrue(engine.nativePaused)
      assertFalse(c.state.value.displayPaused)
      c.focusLost(true)
      c.endSeek()
      c.drain()
      assertTrue(engine.nativePaused)
      c.focusGained()
      c.drain()
      assertFalse(engine.nativePaused)
    }
  }

  @Test
  fun manualPauseDuringSeekIsPreserved() {
    val engine = Engine()
    PlaybackCoordinator(engine).use { c ->
      c.beginSeek()
      c.pause()
      c.endSeek()
      c.drain()
      assertTrue(engine.nativePaused)
      assertTrue(c.state.value.displayPaused)
    }
  }

  @Test
  fun nativeTemporarySeekPauseDoesNotBecomeUserIntent() {
    val engine = Engine()
    PlaybackCoordinator(engine).use { c ->
      c.beginSeek()
      c.drain()
      c.refresh()
      c.drain()
      assertTrue(c.state.value.wantsPlay)
      c.endSeek()
      c.drain()
      assertFalse(engine.nativePaused)
      engine.nativePaused = true
      c.refresh()
      c.drain()
      assertTrue(c.state.value.displayPaused) // A Lua/native pause outside a seek is authoritative.
    }
  }

  @Test
  fun bufferingKeepsPauseAvailableAndDoesNotLookLikeUserPause() {
    val engine = Engine()
    PlaybackCoordinator(engine).use { c ->
      c.mediaLoaded()
      engine.cache = true
      c.refresh()
      c.drain()
      assertTrue(c.state.value.isBuffering)
      assertFalse(c.state.value.displayPaused)
      c.pause()
      c.drain()
      assertTrue(c.state.value.displayPaused)
      assertFalse(c.state.value.isBuffering)
    }
  }

  @Test
  fun deniedFocusNeverStartsPlayback() {
    val engine = Engine().apply { nativePaused = true }
    PlaybackCoordinator(engine).use { c ->
      c.play(false)
      c.drain()
      assertTrue(engine.nativePaused)
      assertTrue(c.state.value.focusBlocked)
      c.focusGained()
      c.drain()
      assertTrue(engine.nativePaused)
      c.play(true)
      c.drain()
      assertFalse(engine.nativePaused)
    }
  }

  @Test
  fun restartAtEndSurvivesRapidPlayPausePlay() {
    val engine = Engine()
    PlaybackCoordinator(engine).use { c ->
      c.mediaLoaded()
      c.drain()
      engine.eof = true
      c.refresh()
      c.drain()
      assertTrue(c.state.value.ended)
      val entered = CountDownLatch(1)
      val release = CountDownLatch(1)
      c.enqueue {
        entered.countDown()
        release.await(3, TimeUnit.SECONDS)
      }
      entered.awaitChecked()
      try {
        c.play(true)
        c.pause()
        c.play(true)
      } finally {
        release.countDown()
      }
      c.drain()
      c.refresh()
      c.drain()
      assertEquals(1, engine.restarts)
      assertFalse(engine.nativePaused)
      assertFalse(c.state.value.ended)
      assertFalse(c.state.value.restarting)
    }
  }

  @Test
  fun queuedVolumeChangesKeepOnlyLatestValue() {
    PlaybackCoordinator(Engine()).use { c ->
      val entered = CountDownLatch(1)
      val release = CountDownLatch(1)
      val writes = CopyOnWriteArrayList<Int>()
      c.enqueue {
        entered.countDown()
        release.await(3, TimeUnit.SECONDS)
      }
      entered.awaitChecked()
      try {
        repeat(50) { value -> c.enqueue("volume") { writes += value } }
      } finally {
        release.countDown()
      }
      c.drain()
      assertEquals(listOf(49), writes.toList())
    }
  }

  @Test
  fun closeWaitsForInFlightNativeWorkAndDiscardsQueuedWork() {
    val c = PlaybackCoordinator(Engine())
    val entered = CountDownLatch(1)
    val release = CountDownLatch(1)
    val queued = CountDownLatch(1)
    val interrupted = CountDownLatch(1)
    c.enqueue {
      entered.countDown()
      while (release.count > 0) {
        try {
          release.await(3, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
          interrupted.countDown()
        }
      }
    }
    entered.awaitChecked()
    c.enqueue { queued.countDown() }
    val closer = Executors.newSingleThreadExecutor()
    try {
      val closing = closer.submit { c.close() }
      interrupted.awaitChecked() // close has shut down the worker but cannot destroy a busy engine.
      assertFalse(closing.isDone)
      release.countDown()
      closing.get(3, TimeUnit.SECONDS)
      c.pause()
      c.enqueue { queued.countDown() }
      assertEquals(1L, queued.count)
    } finally {
      release.countDown()
      c.close()
      closer.shutdownNow()
    }
  }
}
