package live.mehiz.mpvkt.ui.player.controls.components.panels.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import live.mehiz.mpvkt.ui.player.controls.components.PlayerScrollArea

@Composable
fun MultiCardPanel(cardCount: Int, modifier: Modifier = Modifier, cards: @Composable (Int, Modifier) -> Unit) {
  PlayerScrollArea(modifier.fillMaxSize()) {
    repeat(cardCount) { cards(it, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) }
  }
}
