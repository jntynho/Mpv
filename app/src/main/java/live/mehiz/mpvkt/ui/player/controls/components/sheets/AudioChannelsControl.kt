package live.mehiz.mpvkt.ui.player.controls.components.sheets

import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import `is`.xyz.mpv.MPVLib
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.preferences.AudioChannels
import live.mehiz.mpvkt.preferences.AudioPreferences
import live.mehiz.mpvkt.preferences.preference.collectAsState
import live.mehiz.mpvkt.ui.icons.*
import live.mehiz.mpvkt.ui.player.PlaybackControl
import live.mehiz.mpvkt.ui.player.controls.components.PlayerActionRow
import org.koin.compose.koinInject

@Composable
fun AudioChannelsControl() {
  val preferences = koinInject<AudioPreferences>()
  val selected by preferences.audioChannels.collectAsState()
  var expanded by remember { mutableStateOf(false) }
  PlayerActionRow(stringResource(R.string.pref_audio_channels), Icons.Audiotrack, { expanded = !expanded },
    value = stringResource(selected.title))
  if (expanded) AudioChannels.entries.forEach { choice ->
    AudioTrackRow(stringResource(choice.title), selected == choice, {
      preferences.audioChannels.set(choice)
      PlaybackControl.enqueue("audio-channels") {
        if (choice == AudioChannels.ReverseStereo) MPVLib.setPropertyString(AudioChannels.AutoSafe.property, AudioChannels.AutoSafe.value)
        else MPVLib.setPropertyString(AudioChannels.ReverseStereo.property, "")
        MPVLib.setPropertyString(choice.property, choice.value)
      }
      expanded = false
    })
  }
}
