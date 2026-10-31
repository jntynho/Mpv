package live.mehiz.mpvkt.ui.home

import org.junit.Assert.*
import org.junit.Test

class LibraryProgressTest {
  @Test
  fun invalidOrUnknownTimesNeverProduceAnInvalidFraction() {
    assertEquals(0f, libraryProgressFraction(-1, 1000), 0f)
    assertEquals(0f, libraryProgressFraction(50, 0), 0f)
    assertEquals(1f, libraryProgressFraction(Int.MAX_VALUE, 1000), 0f)
    assertTrue(libraryProgressFraction(Int.MAX_VALUE, Long.MAX_VALUE).isFinite())
  }

  @Test
  fun secondsAndMillisecondsAreComparedInTheSameUnit() {
    assertEquals(.5f, libraryProgressFraction(30, 60000), .0001f)
  }
}
