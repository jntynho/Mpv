package live.mehiz.mpvkt.ui.player.controls.components.sheets

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
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
  var expanded by rememberSaveable { mutableStateOf(false) }
  PlayerActionRow(
    stringResource(R.string.pref_audio_channels),
    Icons.Audiotrack,
    { expanded = !expanded },
    value = stringResource(selected.title),
  )
  live.mehiz.mpvkt.ui.preferences.ExpandableSettingsContent(expanded) {
    AudioChannels.entries.forEach { choice ->
      AudioTrackRow(
        stringResource(choice.title),
        selected == choice,
        {
          preferences.audioChannels.set(choice)
          PlaybackControl.enqueue("audio-channels") {
            live.mehiz.mpvkt.ui.player.AudioChannelRouting.apply(choice)
          }
          expanded = false
        },
      )
    }
  }
}
