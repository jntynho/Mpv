package live.mehiz.mpvkt.presentation.components

import androidx.compose.foundation.interaction.*
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay

/** Normal clicks remain accessible; a held press adds repeats after the long-press delay. */
@Composable
fun RepeatingIconButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
  maxDelayMillis: Long = 750,
  minDelayMillis: Long = 40,
  delayDecayFactor: Float = .25f,
  content: @Composable () -> Unit,
) {
  val click by rememberUpdatedState(onClick)
  val pressed by interactionSource.collectIsPressedAsState()
  var repeated by remember { mutableStateOf(false) }
  IconButton(
    onClick = {
      if (!repeated) click()
      repeated = false
    },
    modifier =
      modifier.semantics {
        onClick {
          if (enabled) {
            click()
            true
          } else false
        }
      },
    enabled = enabled,
    interactionSource = interactionSource,
    content = content,
  )
  LaunchedEffect(pressed, enabled) {
    if (pressed && enabled) {
      repeated = false
      var interval = maxDelayMillis.coerceAtLeast(1)
      delay(interval)
      while (true) {
        repeated = true
        click()
        interval =
          (interval * (1 - delayDecayFactor.coerceIn(0f, .9f)))
            .toLong()
            .coerceAtLeast(minDelayMillis.coerceAtLeast(16))
        delay(interval)
      }
    }
  }
}
