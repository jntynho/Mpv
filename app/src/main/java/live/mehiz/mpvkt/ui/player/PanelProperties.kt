package live.mehiz.mpvkt.ui.player

import androidx.core.graphics.toColorInt
import `is`.xyz.mpv.MPVLib
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import live.mehiz.mpvkt.subtitles.SubtitleConfig

/** Coalesced media-owned writes and a read-back acknowledgement on the same native worker. */
internal object PanelProperties {
  private val mediaVersion = java.util.concurrent.atomic.AtomicLong()
  private val latest = mutableMapOf<String, Long>()
  private var sequence = 0L
  private val failed = MutableStateFlow<Set<String>>(emptySet())
  val rejectedProperties = failed.asStateFlow()

  fun clearFailures() {
    synchronized(mediaVersion) {
      mediaVersion.incrementAndGet()
      latest.clear()
      failed.value = emptySet()
    }
  }

  fun editable(name: String) = SubtitleConfig.canEdit(name)

  private fun write(name: String, operation: () -> Boolean) {
    val ticket =
      synchronized(mediaVersion) {
        failed.update { it - name }
        latest[name] = ++sequence
        mediaVersion.get() to sequence
      }
    PlaybackControl.enqueue("property:$name") {
      if (!editable(name)) return@enqueue
      val accepted = runCatching(operation).getOrDefault(false)
      synchronized(mediaVersion) {
        if (ticket.first == mediaVersion.get() && latest[name] == ticket.second)
          failed.update { if (accepted) it - name else it + name }
      }
    }
  }

  fun setInt(name: String, value: Int) =
    write(name) {
      MPVLib.setPropertyInt(name, value)
      val actual = MPVLib.getPropertyInt(name)
      if (actual != null) MpvProperties.propInt.emit(name, actual)
      actual == value
    }

  fun setFloat(name: String, value: Float) {
    if (!value.isFinite()) return
    write(name) {
      MPVLib.setPropertyFloat(name, value)
      val actual = MPVLib.getPropertyDouble(name)
      if (actual != null && actual.isFinite()) {
        MpvProperties.propDouble.emit(name, actual)
        MpvProperties.propFloat.emit(name, actual.toFloat())
      }
      actual != null &&
        actual.isFinite() &&
        abs(actual - value) <= max(.0001, abs(value.toDouble()) * .00001)
    }
  }

  fun setBoolean(name: String, value: Boolean) =
    write(name) {
      MPVLib.setPropertyBoolean(name, value)
      val actual = MPVLib.getPropertyBoolean(name)
      if (actual != null) MpvProperties.propBoolean.emit(name, actual)
      actual == value
    }

  fun setString(name: String, value: String) =
    write(name) {
      MPVLib.setPropertyString(name, value)
      val actual = MPVLib.getPropertyString(name)
      if (actual != null) MpvProperties.propString.emit(name, actual)
      equivalentString(name, actual, value)
    }

  internal fun equivalentString(name: String, actual: String?, value: String): Boolean {
    return if (
      name in
        setOf(
          "sub-color",
          "sub-border-color",
          "sub-outline-color",
          "sub-back-color",
          "sub-shadow-color",
        )
    )
      runCatching { actual != null && actual.toColorInt() == value.toColorInt() }
        .getOrDefault(false)
    else actual?.equals(value, ignoreCase = true) == true
  }
}
