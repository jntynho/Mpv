package io.github.jntynho.mpv

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class SessionLeaseTest {
  @Test fun destructionWaitsForAnInflightNativeAccessAndRejectsLateCalls() {
    val lease = SessionLease<Any>()
    val owner = Any()
    lease.bind(owner)
    val started = CountDownLatch(1)
    val unblock = CountDownLatch(1)
    val retired = CountDownLatch(1)
    val reader = Thread {
      lease.use { started.countDown(); unblock.await(5, TimeUnit.SECONDS) }
    }
    reader.start()
    assertTrue(started.await(5, TimeUnit.SECONDS))
    val destroyer = Thread { lease.release(owner); retired.countDown() }
    destroyer.start()
    try {
      val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
      while (lease.current != null && System.nanoTime() < deadline) Thread.yield()
      assertNull(lease.current)
      assertEquals(1L, retired.count)
      assertNull(lease.use { fail("Late access reached the retiring engine") })
    } finally {
      unblock.countDown()
      reader.join(5000)
      destroyer.join(5000)
    }
    assertEquals(0L, retired.count)
  }

  @Test fun anOldOwnerCannotReleaseAReplacementSession() {
    val lease = SessionLease<Any>()
    val old = Any()
    val next = Any()
    lease.bind(old)
    lease.release(old)
    lease.bind(next)
    lease.release(old)
    assertSame(next, lease.use { it })
    lease.release(next)
  }

  @Test fun failedNativeCallsStillReleaseTheirLease() {
    val lease = SessionLease<Any>()
    val owner = Any()
    lease.bind(owner)
    assertTrue(runCatching { lease.use { error("Native property rejected") } }.isFailure)
    lease.release(owner)
    assertNull(lease.current)
  }
}
