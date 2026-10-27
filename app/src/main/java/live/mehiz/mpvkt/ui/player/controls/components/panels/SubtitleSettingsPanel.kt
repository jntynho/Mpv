package live.mehiz.mpvkt.ui.player.controls.components.panels

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import live.mehiz.mpvkt.ui.player.controls.components.panels.components.MultiCardPanel

@Composable
fun SubtitleSettingsPanel(
  modifier: Modifier = Modifier,
) {
  MultiCardPanel(
    cardCount = 3,
    modifier = modifier,
  ) { index, cardModifier ->
    when (index) {
      0 -> SubtitleSettingsTypographyCard(cardModifier)
      1 -> SubtitleSettingsColorsCard(cardModifier)
      2 -> SubtitlesMiscellaneousCard(cardModifier)
      else -> {}
    }
  }
}
