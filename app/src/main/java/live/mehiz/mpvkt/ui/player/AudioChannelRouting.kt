package live.mehiz.mpvkt.ui.player

import `is`.xyz.mpv.MPVLib
import live.mehiz.mpvkt.preferences.AudioChannels

internal object AudioChannelRouting {
  const val FILTER = "@mpv-channel-swap"

  fun apply(choice: AudioChannels) {
    // Never replace af: other filters can belong to mpv.conf, scripts or custom buttons.
    runCatching { MPVLib.command("af", "remove", FILTER) }
    if (choice == AudioChannels.ReverseStereo) {
      MPVLib.setPropertyString("audio-channels", "stereo")
      MPVLib.command("af", "add", "$FILTER:lavfi=[pan=stereo|c0=c1|c1=c0]")
    } else MPVLib.setPropertyString("audio-channels", choice.value)
  }
}
