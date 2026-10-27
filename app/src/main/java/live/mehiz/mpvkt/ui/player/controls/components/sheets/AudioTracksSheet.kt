package live.mehiz.mpvkt.ui.player.controls.components.sheets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import `is`.xyz.mpv.MPVLib
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import live.mehiz.mpvkt.presentation.components.SlimPlayerSlider
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.preferences.AudioPreferences
import live.mehiz.mpvkt.preferences.preference.collectAsState
import live.mehiz.mpvkt.ui.icons.*
import live.mehiz.mpvkt.ui.player.PlaybackControl
import live.mehiz.mpvkt.ui.player.TrackNode
import live.mehiz.mpvkt.ui.player.controls.components.*
import org.koin.compose.koinInject
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun AudioTracksSheet(
  tracks: ImmutableList<TrackNode>, onSelect: (TrackNode?) -> Unit,
  onAddAudioTrack: () -> Unit, onOpenDelayPanel: () -> Unit,
  modifier: Modifier = Modifier,
) {
  GenericTracksSheet(tracks, modifier,
    beforeTracks = {
      PlayerPanelSection(stringResource(R.string.mpv_panel_tracks))
      AudioTrackRow(stringResource(R.string.player_sheets_tracks_off), tracks.none { it.isSelected }, { onSelect(null) })
    },
    track = { AudioTrackRow(getTrackTitle(it), it.isSelected, { onSelect(it) }, subtitle = trackDescription(it)) },
    footer = {
      PlayerPanelSection(stringResource(R.string.mpv_panel_output))
      AudioVolumeControl()
      val delay by MPVLib.propDouble["audio-delay"].collectAsState()
      PlayerActionRow(stringResource(R.string.player_sheets_audio_delay_card_title), Icons.MoreTime,
        onOpenDelayPanel, value = "${((delay ?: 0.0) * 1000).roundToInt()} ms")
      AudioChannelsControl()
      val preferences = koinInject<AudioPreferences>()
      val correction by preferences.audioPitchCorrection.collectAsState()
      PlayerActionRow(stringResource(R.string.pref_audio_pitch_correction_title), Icons.Tune,
        { preferences.audioPitchCorrection.set(!correction); PlaybackControl.enqueue("pitch") { MPVLib.setPropertyBoolean("audio-pitch-correction", !correction) } },
        trailing = { Switch(correction, onCheckedChange = { checked ->
          preferences.audioPitchCorrection.set(checked)
          PlaybackControl.enqueue("pitch") { MPVLib.setPropertyBoolean("audio-pitch-correction", checked) }
        }) })
    },
    bottomBar = { PlayerPanelFooter(stringResource(R.string.player_sheets_add_ext_audio), onAddAudioTrack) },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudioVolumeControl() {
  val preferences = koinInject<AudioPreferences>()
  val boost by preferences.volumeBoostCap.collectAsState()
  val native by MPVLib.propInt["volume"].collectAsState()
  var requested by remember { mutableStateOf<Float?>(null) }
  var dragging by remember { mutableStateOf(false) }
  val max = (100 + boost).coerceAtLeast(100).toFloat()
  val value = (requested ?: (native ?: 100).toFloat()).coerceIn(0f, max)
  LaunchedEffect(native, requested, dragging) {
    if (!dragging && requested != null) {
      if (abs((native ?: 100) - requested!!) > 1f) delay(800)
      requested = null
    }
  }
  val color = MaterialTheme.colorScheme.primary
  val inactive = MaterialTheme.colorScheme.onSurface.copy(alpha = .18f)
  Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Icon(Icons.VolumeUp, null, Modifier.size(23.dp), tint = color)
      Text(stringResource(R.string.mpv_panel_volume), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
      Text("${value.roundToInt()}%", color = color, style = MaterialTheme.typography.labelMedium)
    }
    SlimPlayerSlider(value, onValueChange = { v ->
      requested = v; dragging = true
      PlaybackControl.enqueue("volume") { MPVLib.setPropertyInt("volume", v.roundToInt()) }
    }, valueRange = 0f..max, onValueChangeFinished = { dragging = false })
  }
}
