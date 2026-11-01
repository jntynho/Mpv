package live.mehiz.mpvkt.ui.player

import org.junit.Assert.*
import org.junit.Test

class StablePropertySessionTest {
  @Test
  fun retainedFlowsRebindAfterDestructionAndRejectLateOldCallbacks() {
    val session = StablePropertySession()
    var native = 12.345
    var reads = 0
    val position =
      session.channel(5) { _: String ->
        reads++
        native
      }
    val retained = position["time-pos"]
    assertNull(retained.value)
    assertEquals(0, reads)
    val observations = mutableListOf<String>()
    val old = session.begin { name, _ -> observations += name }
    session.bindAll()
    assertEquals(12.345, retained.value!!, 0.0)
    session.end()
    assertNull(retained.value)
    session.accept(old) { position.emit("time-pos", 999.0) }
    assertNull(retained.value)
    native = 3.25
    val current = session.begin { name, _ -> observations += name }
    session.bindAll()
    assertSame(retained, position["time-pos"])
    assertEquals(3.25, retained.value!!, 0.0)
    session.accept(old) { position.emit("time-pos", 999.0) }
    assertEquals(3.25, retained.value!!, 0.0)
    session.accept(current) { position.emit("time-pos", 3.5) }
    assertEquals(3.5, retained.value!!, 0.0)
    assertEquals(listOf("time-pos", "time-pos"), observations)
  }

  @Test
  fun sharedNativeFormatsObserveOnceAndUnavailableValuesDoNotStayStale() {
    val session = StablePropertySession()
    val double = session.channel(5) { _: String -> 2.0 }
    val float = session.channel(5) { _: String -> 2f }
    var observations = 0
    val token = session.begin { _, _ -> observations++ }
    val a = double["speed"]
    val b = float["speed"]
    session.bindAll()
    assertEquals(1, observations)
    session.accept(token) { session.invalidate("speed") }
    assertNull(a.value)
    assertNull(b.value)
    session.accept(token) {
      double.emit("speed", 1.25)
      float.emit("speed", 1.25f)
    }
    assertEquals(1.25, a.value!!, 0.0)
    assertEquals(1.25f, b.value!!, 0f)
    assertThrows(IllegalStateException::class.java) { session.begin { _, _ -> } }
  }
}
