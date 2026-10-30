package live.mehiz.mpvkt.ui.player.controls

import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import live.mehiz.mpvkt.ui.player.Panels
import live.mehiz.mpvkt.ui.player.controls.components.panels.AudioDelayPanel
import live.mehiz.mpvkt.ui.player.controls.components.panels.SubtitleDelayPanel
import live.mehiz.mpvkt.ui.player.controls.components.panels.SubtitleSettingsPanel
import live.mehiz.mpvkt.ui.player.controls.components.panels.VideoSettingsPanel

@Composable
fun PlayerPanels(
  panelShown: Panels,
  sleepTimerTimeRemaining: Int,
  onStartSleepTimer: (Int) -> Unit,
  onNavigateBack: () -> Unit,
  subtitleSlot: Int = 0,
) {
  when (panelShown) {
    Panels.None -> Unit
    Panels.SubtitleSearch,
    Panels.SubtitleSearchOptions,
    Panels.SubtitleSearchLanguages,
    Panels.SubtitleSearchSeason,
    Panels.SubtitleSearchEpisode,
    Panels.SubtitleSearchFiles -> Unit // Owned by the persistent subtitle controller.
    Panels.SubtitleSettings -> SubtitleSettingsPanel(secondary = subtitleSlot == 1)
    Panels.SubtitleDelay -> SubtitleDelayPanel(initialSlot = subtitleSlot)
    Panels.AudioDelay -> AudioDelayPanel()
    Panels.VideoFilters -> VideoSettingsPanel()
    Panels.SleepTimer ->
      live.mehiz.mpvkt.ui.player.controls.components.sheets.SleepTimerPanel(
        sleepTimerTimeRemaining,
        onStartSleepTimer,
        onNavigateBack,
      )
  }
}

val CARDS_MAX_WIDTH = 480.dp
val panelCardsColors: @Composable () -> CardColors = {
  val opacity = live.mehiz.mpvkt.ui.player.controls.components.LocalPanelOpacity.current
  val colors = CardDefaults.cardColors()
  colors.copy(
    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .3f * opacity),
    disabledContainerColor = MaterialTheme.colorScheme.surfaceDim.copy(alpha = .3f * opacity),
  )
}
