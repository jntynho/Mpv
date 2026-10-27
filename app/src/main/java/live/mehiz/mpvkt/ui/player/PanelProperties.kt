package live.mehiz.mpvkt.ui.player

import `is`.xyz.mpv.MPVLib

/** Keep synchronous JNI writes off the UI thread, including slider resets. */
internal object PanelProperties {
  fun setInt(name: String, value: Int) = PlaybackControl.enqueue("property:$name") { MPVLib.setPropertyInt(name, value) }
  fun setFloat(name: String, value: Float) = PlaybackControl.enqueue("property:$name") { MPVLib.setPropertyFloat(name, value) }
  fun setBoolean(name: String, value: Boolean) = PlaybackControl.enqueue("property:$name") { MPVLib.setPropertyBoolean(name, value) }
  fun setString(name: String, value: String) = PlaybackControl.enqueue("property:$name") { MPVLib.setPropertyString(name, value) }
}
