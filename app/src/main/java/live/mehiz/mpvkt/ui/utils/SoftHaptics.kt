package live.mehiz.mpvkt.ui.utils

import android.os.Build
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView

/**
 * Uses the system's quiet tick and honours its haptic switch; never drives the vibrator directly.
 */
internal object SoftHaptics {
  private var lastTick = 0L

  fun tick(view: View) {
    val now = SystemClock.uptimeMillis()
    if (now - lastTick < 65) return
    lastTick = now
    view.performHapticFeedback(
      if (Build.VERSION.SDK_INT >= 27) HapticFeedbackConstants.CLOCK_TICK
      else HapticFeedbackConstants.KEYBOARD_TAP
    )
  }
}

/**
 * Observes completed consumed taps, without consuming or delaying input/scroll/elastic gestures.
 */
internal fun Modifier.softTapFeedback(): Modifier = composed {
  val view = LocalView.current
  pointerInput(view) {
    awaitPointerEventScope {
      var tracking = false
      var moved = false
      var start = androidx.compose.ui.geometry.Offset.Zero
      var downTime = 0L
      while (true) {
        val event = awaitPointerEvent(PointerEventPass.Final)
        val change = event.changes.firstOrNull() ?: continue
        if (!change.previousPressed && change.pressed) {
          tracking = true
          moved = false
          start = change.position
          downTime = change.uptimeMillis
        }
        if (
          event.changes.size != 1 ||
            (change.position - start).getDistance() > viewConfiguration.touchSlop
        )
          moved = true
        if (tracking && change.previousPressed && !change.pressed) {
          if (
            !moved &&
              change.isConsumed &&
              change.uptimeMillis - downTime < viewConfiguration.longPressTimeoutMillis
          )
            SoftHaptics.tick(view)
          tracking = false
        }
      }
    }
  }
}
