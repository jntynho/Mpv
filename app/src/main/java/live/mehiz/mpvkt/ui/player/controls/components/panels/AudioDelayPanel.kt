package live.mehiz.mpvkt.ui.player.controls.components.panels

import live.mehiz.mpvkt.ui.player.MpvProperties
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import live.mehiz.mpvkt.ui.player.controls.components.PlayerScrollArea
import `is`.xyz.mpv.MPVLib
import live.mehiz.mpvkt.preferences.AudioPreferences
import org.koin.compose.koinInject

@Composable
fun AudioDelayPanel(
  modifier: Modifier = Modifier,
) {
  val preferences = koinInject<AudioPreferences>()

  PlayerScrollArea(modifier.fillMaxSize()) {
    val delay by MpvProperties.propDouble["audio-delay"].collectAsState()
    DelayCard(
      delayMs = ((delay ?: 0.0) * 1000).toInt(),
      onDelayChange = { value -> live.mehiz.mpvkt.ui.player.PlaybackControl.enqueue("audio-delay") { MPVLib.setPropertyDouble("audio-delay", value / 1000.0) } },
      onApply = { preferences.defaultAudioDelay.set(((delay ?: 0.0) * 1000).toInt()) },
      onReset = { MPVLib.setPropertyDouble("audio-delay", (preferences.defaultAudioDelay.get() / 1000.0)) },
      title = {},
      delayType = DelayType.Audio,
      modifier = Modifier.fillMaxWidth().padding(12.dp),
    )
  }
}
