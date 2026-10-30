package live.mehiz.mpvkt.ui.player

import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVNode

/** Activity-owned binding; cached Flow references remain usable after a native core recreation. */
internal object MpvProperties {
  private val session = StablePropertySession { action ->
    PlaybackControl.enqueueSession(action = action)
  }
  val propBoolean =
    session.channel(MPVLib.mpvFormat.MPV_FORMAT_FLAG) { MPVLib.getPropertyBoolean(it) }
  val propString =
    session.channel(MPVLib.mpvFormat.MPV_FORMAT_STRING) { MPVLib.getPropertyString(it) }
  val propInt = session.channel(MPVLib.mpvFormat.MPV_FORMAT_INT64) { MPVLib.getPropertyInt(it) }
  val propLong =
    session.channel(MPVLib.mpvFormat.MPV_FORMAT_INT64) { MPVLib.getPropertyNode(it)?.asInt() }
  val propDouble =
    session.channel(MPVLib.mpvFormat.MPV_FORMAT_DOUBLE) { MPVLib.getPropertyDouble(it) }
  val propFloat =
    session.channel(MPVLib.mpvFormat.MPV_FORMAT_DOUBLE) { MPVLib.getPropertyFloat(it) }
  val propNode =
    session.channel(MPVLib.mpvFormat.MPV_FORMAT_NODE) {
      MPVLib.getPropertyNode(it)?.takeUnless { node -> node is MPVNode.None }
    }
  private var owner: Any? = null
  private var observer: MPVLib.EventObserver? = null

  fun attach(owner: Any) {
    check(this.owner == null) { "Another player already owns the property session" }
    this.owner = owner
    val token = session.begin { name, format -> MPVLib.observeProperty(name, format) }
    val listener =
      object : MPVLib.EventObserver {
        override fun eventProperty(property: String) =
          session.accept(token) { session.invalidate(property) }

        override fun eventProperty(property: String, value: Boolean) =
          session.accept(token) { propBoolean.emit(property, value) }

        override fun eventProperty(property: String, value: String) =
          session.accept(token) { propString.emit(property, value) }

        override fun eventProperty(property: String, value: Long) =
          session.accept(token) {
            propLong.emit(property, value)
            propInt.emit(property, value.toInt())
          }

        override fun eventProperty(property: String, value: Double) =
          session.accept(token) {
            propDouble.emit(property, value)
            propFloat.emit(property, value.toFloat())
          }

        override fun eventProperty(property: String, value: MPVNode) =
          session.accept(token) { propNode.emit(property, value.takeUnless { it is MPVNode.None }) }

        override fun event(eventId: Int) {}
      }
    observer = listener
    MPVLib.addObserver(listener)
    session.bindAll()
  }

  fun detach(owner: Any) {
    if (this.owner !== owner) return
    session.end() // Reject snapshots already copied by the library's native event thread.
    observer?.let(MPVLib::removeObserver)
    observer = null
    this.owner = null
  }
}
