package live.mehiz.mpvkt.ui.player

import java.util.concurrent.*
import org.junit.Assert.*
import org.junit.Test

class PauseAcknowledgementTest {
  private open class Engine : PlaybackEngine {
    @Volatile var nativePaused = false
    val writes = CopyOnWriteArrayList<Boolean>()

    override fun paused(): Boolean? = nativePaused

    override fun buffering() = false

    override fun ended() = false

    override fun setPaused(value: Boolean) {
      writes += value
      nativePaused = value
    }

    override fun restart() {}
  }

  private fun CountDownLatch.waitFor() {
    assertTrue("Worker did not respond", await(3, TimeUnit.SECONDS))
  }

  private fun PlaybackCoordinator.drain() {
    val end = CountDownLatch(1)
    enqueue { end.countDown() }
    end.waitFor()
  }

  private fun PlaybackCoordinator.awaitResult() {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
    while (state.value.pending && System.nanoTime() < deadline) Thread.sleep(5)
    assertFalse("Playback confirmation remained pending", state.value.pending)
  }

  @Test
  fun urgentPauseOvertakesQueuedPropertiesAndConfirmedPauseFreezesPosition() {
    val order = CopyOnWriteArrayList<String>()
    val engine =
      object : Engine() {
        var origin = System.nanoTime()
        var frozen = 12.345

        override fun setPaused(value: Boolean) {
          val position = positionSeconds()
          order += "pause:$value"
          super.setPaused(value)
          frozen = position
          origin = System.nanoTime()
        }

        override fun positionSeconds() =
          frozen + if (nativePaused) 0.0 else (System.nanoTime() - origin) / 1_000_000_000.0
      }
    PlaybackCoordinator(engine).use { c ->
      val entered = CountDownLatch(1)
      val release = CountDownLatch(1)
      c.enqueue {
        entered.countDown()
        release.waitFor()
      }
      entered.waitFor()
      repeat(20) { index -> c.enqueue("setting-$index") { order += "setting" } }
      c.positionChanged(engine.positionSeconds())
      c.pause()
      assertTrue(c.state.value.pending)
      assertNull(c.state.value.confirmedPaused)
      Thread.sleep(60)
      release.countDown()
      c.drain()
      assertEquals("pause:true", order.first())
      assertTrue(c.state.value.confirmedPaused == true)
      assertTrue(c.state.value.pausePositionSeconds!! >= 12.345)
      assertTrue(c.state.value.pauseAdvanceMillis!! >= 40.0)
      val pausedPosition = engine.positionSeconds()
      Thread.sleep(60)
      assertEquals(pausedPosition, engine.positionSeconds(), .000001)
    }
  }

  @Test
  fun rapidPauseThenPlayMustActuallyPauseBeforeResuming() {
    val engine = Engine()
    PlaybackCoordinator(engine).use { c ->
      val entered = CountDownLatch(1)
      val release = CountDownLatch(1)
      c.enqueue {
        entered.countDown()
        release.waitFor()
      }
      entered.waitFor()
      c.pause()
      c.play(true)
      release.countDown()
      c.drain()
      assertEquals(listOf(true, false), engine.writes.toList())
      assertFalse(c.state.value.pending)
      assertFalse(engine.nativePaused)
    }
  }

  @Test
  fun wrongNativeValueStaysPendingThenFailsWithinABoundWithoutFalsePauseConfirmation() {
    val engine =
      object : Engine() {
        override fun setPaused(value: Boolean) {
          writes += value
        }
      }
    PlaybackCoordinator(engine, confirmationTimeoutMillis = 300, confirmationIntervalMillis = 15)
      .use { c ->
        c.pause()
        c.drain()
        assertTrue(c.state.value.pending)
        assertEquals(false, c.state.value.confirmedPaused)
        c.refresh()
        c.drain()
        assertFalse(c.state.value.wantsPlay)
        c.awaitResult()
        assertEquals(PlaybackFailure.Unconfirmed, c.state.value.failure)
        assertFalse(c.state.value.displayPaused)
        assertTrue(engine.writes.size in 1..3)
      }
  }

  @Test
  fun delayedNativeAcknowledgementCompletesWithoutLosingThePauseIntent() {
    val engine =
      object : Engine() {
        override fun setPaused(value: Boolean) {
          writes += value
        }
      }
    PlaybackCoordinator(engine, confirmationIntervalMillis = 15).use { c ->
      c.pause()
      c.drain()
      assertTrue(c.state.value.pending)
      engine.nativePaused = true
      c.refresh()
      c.awaitResult()
      assertNull(c.state.value.failure)
      assertTrue(c.state.value.displayPaused)
      assertFalse(c.state.value.wantsPlay)
    }
  }

  @Test
  fun failedResumeDoesNotBusyLoopOrRunTheRestartMoreThanOnce() {
    val engine =
      object : Engine() {
          override fun setPaused(value: Boolean) {
            writes += value
          }
        }
        .apply { nativePaused = true }
    PlaybackCoordinator(engine, confirmationTimeoutMillis = 100, confirmationIntervalMillis = 15)
      .use { c ->
        c.play(true)
        c.awaitResult()
        assertEquals(PlaybackFailure.Unconfirmed, c.state.value.failure)
        assertTrue(c.state.value.displayPaused)
        assertTrue(engine.writes.size in 1..3)
      }
  }

  @Test
  fun nativeExceptionIsReportedAndDoesNotLeaveAnEndlessPendingState() {
    val engine =
      object : Engine() {
        override fun setPaused(value: Boolean) {
          throw IllegalStateException("Native rejected")
        }
      }
    PlaybackCoordinator(engine, confirmationTimeoutMillis = 90, confirmationIntervalMillis = 15)
      .use { c ->
        c.pause()
        c.awaitResult()
        assertEquals(PlaybackFailure.NativeError, c.state.value.failure)
      }
  }

  @Test
  fun aNewFileCancelsOldSeekReleaseWithoutLeavingTheNewFilePaused() {
    val engine = Engine()
    PlaybackCoordinator(engine).use { c ->
      val entered = CountDownLatch(1)
      val release = CountDownLatch(1)
      c.enqueue {
        entered.countDown()
        release.waitFor()
      }
      entered.waitFor()
      var oldSeek = false
      c.beginSeek()
      c.enqueue("seek") { oldSeek = true }
      c.endSeek()
      c.mediaStarted()
      c.mediaLoaded()
      release.countDown()
      c.drain()
      assertFalse(oldSeek)
      assertFalse(c.state.value.seeking)
      assertTrue(c.state.value.wantsPlay)
      assertFalse(engine.nativePaused)
    }
  }

  @Test
  fun queuedSeekRunsBeforeTheReleaseCanResumePlayback() {
    val order = CopyOnWriteArrayList<String>()
    val engine =
      object : Engine() {
        override fun setPaused(value: Boolean) {
          order += "pause:$value"
          super.setPaused(value)
        }
      }
    PlaybackCoordinator(engine).use { c ->
      val entered = CountDownLatch(1)
      val release = CountDownLatch(1)
      c.enqueue {
        entered.countDown()
        release.waitFor()
      }
      entered.waitFor()
      c.beginSeek()
      c.enqueue("seek") { order += "seek" }
      c.endSeek()
      release.countDown()
      c.drain()
      assertEquals(listOf("pause:true", "seek", "pause:false"), order.toList())
    }
  }
}
