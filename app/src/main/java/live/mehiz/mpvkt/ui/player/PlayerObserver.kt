package live.mehiz.mpvkt.ui.player

import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVNode

class PlayerObserver(private val activity: PlayerActivity) : MPVLib.EventObserver {
  @Volatile private var active = true

  fun release() {
    active = false
  }

  private fun dispatch(action: () -> Unit) {
    if (!active) return
    activity.runOnUiThread { if (active && !activity.isDestroyed) action() }
  }

  override fun eventProperty(property: String) {
    dispatch { activity.onObserverEvent(property) }
  }

  override fun eventProperty(property: String, value: Long) {
    dispatch { activity.onObserverEvent(property, value) }
  }

  override fun eventProperty(property: String, value: Boolean) {
    dispatch { activity.onObserverEvent(property, value) }
  }

  override fun eventProperty(property: String, value: String) {
    dispatch { activity.onObserverEvent(property, value) }
  }

  override fun eventProperty(property: String, value: Double) {
    dispatch { activity.onObserverEvent(property, value) }
  }

  @Suppress("EmptyFunctionBlock")
  override fun eventProperty(property: String, value: MPVNode) {
    dispatch { activity.onObserverEvent(property, value) }
  }

  override fun event(eventId: Int) {
    dispatch { activity.event(eventId) }
  }
}
