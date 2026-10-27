package live.mehiz.mpvkt.ui.player

import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One controller for the Activity, media session, PiP, gestures and background service. */
internal object PlaybackControl {
  private val mutable = MutableStateFlow(PlaybackSnapshot())
  val state = mutable.asStateFlow()
  @Volatile private var controller: PlaybackCoordinator? = null
  private var engineOwner: Any? = null
  private var focusOwner: Any? = null
  private var requestFocus: (() -> Boolean)? = null
  private var observer: MPVLib.EventObserver? = null

  fun attach(owner: Any) {
    engineOwner?.let(::detach)
    engineOwner = owner
    val c = PlaybackCoordinator(object : PlaybackEngine {
      override fun paused() = MPVLib.getPropertyBoolean("pause")
      override fun buffering() = MPVLib.getPropertyBoolean("paused-for-cache") == true
      override fun ended() = MPVLib.getPropertyBoolean("eof-reached") == true
      override fun setPaused(value: Boolean) = MPVLib.setPropertyBoolean("pause", value)
      override fun restart() = MPVLib.command("seek", "0", "absolute+exact")
    }, changed = { mutable.value = it })
    controller = c
    mutable.value = c.state.value
    observer = object : MPVLib.EventObserver {
      override fun eventProperty(property: String, value: Boolean) {
        if (property in setOf("pause", "paused-for-cache", "eof-reached")) c.refresh()
      }
      override fun event(eventId: Int) {
        when (eventId) {
          MPVLib.mpvEventId.MPV_EVENT_START_FILE -> c.mediaStarted()
          MPVLib.mpvEventId.MPV_EVENT_FILE_LOADED -> c.mediaLoaded()
        }
      }
      override fun eventProperty(property: String) {}
      override fun eventProperty(property: String, value: Long) {}
      override fun eventProperty(property: String, value: Double) {}
      override fun eventProperty(property: String, value: String) {}
      override fun eventProperty(property: String, value: MPVNode) {}
    }.also { MPVLib.addObserver(it) }
    listOf("pause", "paused-for-cache", "eof-reached").forEach { MPVLib.observeProperty(it, MPVLib.mpvFormat.MPV_FORMAT_FLAG) }
    listOf(MPVLib.propBoolean, MPVLib.propInt, MPVLib.propDouble, MPVLib.propString, MPVLib.propNode, MPVLib.propLong, MPVLib.propFloat).forEach { property ->
      property.map.keys.toList().forEach { MPVLib.observeProperty(it, property.type) }
    }
    c.refresh()
  }

  fun detach(owner: Any) {
    if (engineOwner !== owner) return
    observer?.let(MPVLib::removeObserver)
    observer = null
    controller?.close()
    controller = null
    engineOwner = null
    focusOwner = null
    requestFocus = null
    mutable.value = PlaybackSnapshot()
  }
  fun focusRequester(owner: Any, request: () -> Boolean) { focusOwner = owner; requestFocus = request }
  fun clearFocusRequester(owner: Any) { if (focusOwner === owner) { focusOwner = null; requestFocus = null } }
  fun toggle() { controller?.toggle { requestFocus?.invoke() == true } }
  fun play() { controller?.play(requestFocus?.invoke() == true) }
  fun pause() { controller?.pause() }
  fun focusLost(transient: Boolean) { controller?.focusLost(transient) }
  fun focusGained() { controller?.focusGained() }
  fun beginSeek() { controller?.beginSeek() }
  fun endSeek() { controller?.endSeek() }
  fun enqueue(key: String? = null, action: () -> Unit) { controller?.enqueue(key, action) }
}
