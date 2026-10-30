package live.mehiz.mpvkt.ui.player.controls.components.panels

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import live.mehiz.mpvkt.ui.player.controls.components.panels.components.MultiCardPanel

@Composable
fun VideoSettingsPanel(modifier: Modifier = Modifier) {
  MultiCardPanel(
    cardCount = 2,
    modifier = modifier,
    header = {
      live.mehiz.mpvkt.ui.player.controls.components.PanelConfigNotice(
        (live.mehiz.mpvkt.ui.player.VideoFilters.entries.map { it.mpvProperty } +
            live.mehiz.mpvkt.ui.player.DebandSettings.entries.map { it.mpvProperty } +
            "deband")
          .toSet()
      )
    },
  ) { index, cardModifier ->
    when (index) {
      0 -> VideoSettingsDebandCard(cardModifier)
      1 -> VideoSettingsFiltersCard(cardModifier)
      else -> {}
    }
  }
}
