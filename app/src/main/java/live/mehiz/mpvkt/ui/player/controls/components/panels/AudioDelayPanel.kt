package live.mehiz.mpvkt.ui.player.controls.components.panels

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `is`.xyz.mpv.MPVLib
import live.mehiz.mpvkt.preferences.AudioPreferences
import live.mehiz.mpvkt.ui.player.MpvProperties
import live.mehiz.mpvkt.ui.player.controls.components.PlayerScrollArea
import org.koin.compose.koinInject

@Composable
fun AudioDelayPanel(modifier: Modifier = Modifier) {
  val preferences = koinInject<AudioPreferences>()

  PlayerScrollArea(modifier.fillMaxSize()) {
    val delay by MpvProperties.propDouble["audio-delay"].collectAsState()
    DelayCard(
      delayMs = ((delay ?: 0.0) * 1000).toInt(),
      onDelayChange = { value ->
        live.mehiz.mpvkt.ui.player.PanelProperties.setFloat("audio-delay", value / 1000f)
      },
      onApply = {
        live.mehiz.mpvkt.ui.player.PlaybackControl.enqueue("audio-delay-default") {
          MPVLib.getPropertyDouble("audio-delay")
            ?.takeIf { it.isFinite() }
            ?.let { preferences.defaultAudioDelay.set((it * 1000).toInt()) }
        }
      },
      editable = live.mehiz.mpvkt.ui.player.controls.components.propertyEditable("audio-delay"),
      onReset = {
        live.mehiz.mpvkt.ui.player.PanelProperties.setFloat(
          "audio-delay",
          preferences.defaultAudioDelay.get() / 1000f,
        )
      },
      title = {},
      delayType = DelayType.Audio,
      modifier = Modifier.fillMaxWidth().padding(12.dp),
    )
  }
}
