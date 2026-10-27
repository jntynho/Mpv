package live.mehiz.mpvkt.ui.player.controls.components.panels.components

import live.mehiz.mpvkt.ui.player.controls.components.PlayerPanelHeader
import live.mehiz.mpvkt.ui.player.controls.components.PlayerScrollArea
import androidx.compose.foundation.layout.padding
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
fun MultiCardPanel(
  onDismissRequest: () -> Unit,
  @StringRes titleRes: Int,
  cardCount: Int,
  modifier: Modifier = Modifier,
  cards: @Composable (Int, Modifier) -> Unit,
) {
  Column(modifier.fillMaxSize()) {
    PlayerPanelHeader(stringResource(titleRes))
    PlayerScrollArea(Modifier.weight(1f)) {
      repeat(cardCount) { cards(it, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) }
    }
  }
}
