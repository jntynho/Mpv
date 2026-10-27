package live.mehiz.mpvkt.ui.player.controls.components.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import live.mehiz.mpvkt.ui.icons.Icons
import live.mehiz.mpvkt.ui.icons.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import live.mehiz.mpvkt.ui.player.controls.components.PlayerScrollArea
import `is`.xyz.mpv.MPVLib
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.preferences.AudioPreferences
import live.mehiz.mpvkt.ui.theme.spacing
import org.koin.compose.koinInject

@Composable
fun AudioDelayPanel(
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val preferences = koinInject<AudioPreferences>()

  PlayerScrollArea(modifier.fillMaxSize()) {
    val delay by MPVLib.propDouble["audio-delay"].collectAsState()
    DelayCard(
      delayMs = ((delay ?: 0.0) * 1000).toInt(),
      onDelayChange = { value -> live.mehiz.mpvkt.ui.player.PlaybackControl.enqueue("audio-delay") { MPVLib.setPropertyDouble("audio-delay", value / 1000.0) } },
      onApply = { preferences.defaultAudioDelay.set(((delay ?: 0.0) * 1000).toInt()) },
      onReset = { MPVLib.setPropertyDouble("audio-delay", (preferences.defaultAudioDelay.get() / 1000.0)) },
      title = { AudioDelayCardTitle(onClose = onDismissRequest) },
      delayType = DelayType.Audio,
      modifier = Modifier.fillMaxWidth().padding(12.dp),
    )
  }
}

@Composable
fun AudioDelayCardTitle(
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      stringResource(R.string.player_sheets_audio_delay_card_title),
      modifier = Modifier.weight(1f),
      style = MaterialTheme.typography.titleLarge,
    )
    IconButton(onClose) {
      Icon(
        Icons.Close,
        null,
        modifier = Modifier.size(32.dp),
      )
    }
  }
}
