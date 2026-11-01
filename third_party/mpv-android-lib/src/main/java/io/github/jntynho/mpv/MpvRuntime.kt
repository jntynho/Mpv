package io.github.jntynho.mpv

import `is`.xyz.mpv.MPV
import `is`.xyz.mpv.MPVNode
import java.util.concurrent.ConcurrentHashMap

/** Compatibility boundary for the app's one foreground/background playback session.
 * The native MPV instance belongs to its SurfaceView; no second engine is created here.
 */
object MpvRuntime {
  private val sessions = SessionLease<MPV>()
  private val current: MPV? get() = sessions.current
  private val observers = ConcurrentHashMap<EventObserver, MPV.EventObserver>()

  @Synchronized fun bind(engine: MPV) {
    check(current == null || current === engine || current?.isInitialized != true) {
      "Release the previous player before binding another MPV session"
    }
    if (current === engine) return
    current?.let(::unbind)
    sessions.bind(engine)
  }

  @Synchronized fun unbind(engine: MPV) {
    if (current !== engine) return
    sessions.release(engine)
    observers.values.forEach(engine::removeObserver)
    observers.clear()
  }

  private fun <R> access(action: (MPV) -> R): R? = sessions.use {
    if (it.isInitialized) action(it) else null
  }
  fun command(vararg arguments: String) { access { it.command(*arguments) } }
  fun commandNode(vararg arguments: String) = access { it.commandNode(*arguments) }
  fun setOptionString(name: String, value: String): Int = access { it.setOptionString(name, value) } ?: -1
  fun grabThumbnail(dimension: Int) = access { it.grabThumbnail(dimension) }
  fun observeProperty(name: String, format: Int) { access { it.observeProperty(name, format) } }
  fun getPropertyInt(name: String) = access { it.getPropertyInt(name) }
  fun getPropertyDouble(name: String) = access { it.getPropertyDouble(name) }
  fun getPropertyFloat(name: String) = access { it.getPropertyFloat(name) }
  fun getPropertyBoolean(name: String) = access { it.getPropertyBoolean(name) }
  fun getPropertyString(name: String) = access { it.getPropertyString(name) }
  fun getPropertyNode(name: String) = access { it.getPropertyNode(name) }
  fun setPropertyInt(name: String, value: Int) { access { it.setPropertyInt(name, value) } }
  fun setPropertyDouble(name: String, value: Double) { access { it.setPropertyDouble(name, value) } }
  fun setPropertyFloat(name: String, value: Float) { access { it.setPropertyFloat(name, value) } }
  fun setPropertyBoolean(name: String, value: Boolean) { access { it.setPropertyBoolean(name, value) } }
  fun setPropertyString(name: String, value: String) { access { it.setPropertyString(name, value) } }

  @Synchronized fun addObserver(listener: EventObserver) {
    val owner = current?.takeIf { it.isInitialized } ?: return
    if (observers.containsKey(listener)) return
    val bridge = object : MPV.EventObserver {
      private fun active() = current === owner && observers.containsKey(listener)
      override fun eventProperty(property: String) { if (active()) listener.eventProperty(property) }
      override fun eventProperty(property: String, value: Long) { if (active()) listener.eventProperty(property, value) }
      override fun eventProperty(property: String, value: Boolean) { if (active()) listener.eventProperty(property, value) }
      override fun eventProperty(property: String, value: String) { if (active()) listener.eventProperty(property, value) }
      override fun eventProperty(property: String, value: Double) { if (active()) listener.eventProperty(property, value) }
      override fun eventProperty(property: String, value: MPVNode) { if (active()) listener.eventProperty(property, value) }
      override fun event(eventId: Int, data: MPVNode) { if (active()) listener.event(eventId) }
    }
    observers[listener] = bridge
    owner.addObserver(bridge)
  }

  @Synchronized fun removeObserver(listener: EventObserver) {
    val bridge = observers.remove(listener) ?: return
    current?.removeObserver(bridge)
  }

  interface EventObserver {
    fun eventProperty(property: String) {}
    fun eventProperty(property: String, value: Long) {}
    fun eventProperty(property: String, value: Boolean) {}
    fun eventProperty(property: String, value: String) {}
    fun eventProperty(property: String, value: Double) {}
    fun eventProperty(property: String, value: MPVNode) {}
    fun event(eventId: Int) {}
  }

  object mpvFormat {
    const val MPV_FORMAT_NONE = MPV.mpvFormat.MPV_FORMAT_NONE
    const val MPV_FORMAT_STRING = MPV.mpvFormat.MPV_FORMAT_STRING
    const val MPV_FORMAT_FLAG = MPV.mpvFormat.MPV_FORMAT_FLAG
    const val MPV_FORMAT_INT64 = MPV.mpvFormat.MPV_FORMAT_INT64
    const val MPV_FORMAT_DOUBLE = MPV.mpvFormat.MPV_FORMAT_DOUBLE
    const val MPV_FORMAT_NODE = MPV.mpvFormat.MPV_FORMAT_NODE
  }

  object mpvEventId {
    const val MPV_EVENT_START_FILE = MPV.mpvEvent.MPV_EVENT_START_FILE
    const val MPV_EVENT_FILE_LOADED = MPV.mpvEvent.MPV_EVENT_FILE_LOADED
    const val MPV_EVENT_END_FILE = MPV.mpvEvent.MPV_EVENT_END_FILE
    const val MPV_EVENT_SHUTDOWN = MPV.mpvEvent.MPV_EVENT_SHUTDOWN
    const val MPV_EVENT_CLIENT_MESSAGE = MPV.mpvEvent.MPV_EVENT_CLIENT_MESSAGE
    const val MPV_EVENT_VIDEO_RECONFIG = MPV.mpvEvent.MPV_EVENT_VIDEO_RECONFIG
    const val MPV_EVENT_PLAYBACK_RESTART = MPV.mpvEvent.MPV_EVENT_PLAYBACK_RESTART
  }
}
