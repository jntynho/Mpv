package live.mehiz.mpvkt.ui.player.controls.components

import androidx.compose.runtime.*
import kotlin.math.abs

/** Local preview only. Exactly one target is released, and cancellation never yields a seek. */
@Stable
internal class ScrubSession {
  var draft by mutableStateOf<Float?>(null)
    private set

  var dragging by mutableStateOf(false)
    private set

  var awaiting by mutableStateOf(false)
    private set

  var revision by mutableIntStateOf(0)
    private set

  fun preview(value: Float, duration: Float) {
    if (!value.isFinite() || !duration.isFinite() || duration <= 0f) return
    draft = value.coerceIn(0f, duration)
    dragging = true
    awaiting = false
  }

  fun release(): Float? {
    if (!dragging) return null
    dragging = false
    awaiting = true
    revision++
    return draft
  }

  fun acknowledge(value: Float) {
    if (
      awaiting && !dragging && value.isFinite() && draft?.let { abs(it - value) <= 1.25f } == true
    )
      cancel()
  }

  fun cancel() {
    draft = null
    dragging = false
    awaiting = false
    revision++
  }
}
