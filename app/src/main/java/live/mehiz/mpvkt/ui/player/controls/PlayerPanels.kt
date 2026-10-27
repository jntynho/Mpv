package live.mehiz.mpvkt.ui.player.controls

import live.mehiz.mpvkt.presentation.components.PlayerSheet
import live.mehiz.mpvkt.presentation.components.LocalPlayerSheetClose
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import live.mehiz.mpvkt.preferences.PlayerPreferences
import live.mehiz.mpvkt.ui.player.Panels
import live.mehiz.mpvkt.ui.player.controls.components.panels.AudioDelayPanel
import live.mehiz.mpvkt.ui.player.controls.components.panels.SubtitleDelayPanel
import live.mehiz.mpvkt.ui.player.controls.components.panels.SubtitleSettingsPanel
import live.mehiz.mpvkt.ui.player.controls.components.panels.VideoSettingsPanel
import org.koin.compose.koinInject

@Composable
fun PlayerPanels(
  panelShown: Panels,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  if (panelShown == Panels.None) return
  PlayerSheet(onDismissRequest, modifier) {
    val close = LocalPlayerSheetClose.current
    val dismiss = { close {} }
    when (panelShown) {
      Panels.None -> Unit
      Panels.SubtitleSettings -> SubtitleSettingsPanel(dismiss)
      Panels.SubtitleDelay -> SubtitleDelayPanel(dismiss)
      Panels.AudioDelay -> AudioDelayPanel(dismiss)
      Panels.VideoFilters -> VideoSettingsPanel(dismiss)
    }
  }
}

val CARDS_MAX_WIDTH = 420.dp
val panelCardsColors: @Composable () -> CardColors = {
  val playerPreferences = koinInject<PlayerPreferences>()

  val colors = CardDefaults.cardColors()
  colors.copy(
    containerColor = MaterialTheme.colorScheme.surface.copy(playerPreferences.panelTransparency.get()),
    disabledContainerColor = MaterialTheme.colorScheme.surfaceDim.copy(playerPreferences.panelTransparency.get()),
  )
}
