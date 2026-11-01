package live.mehiz.mpvkt.ui.preferences

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import live.mehiz.mpvkt.ui.motion.LocalReduceMotion
import live.mehiz.mpvkt.ui.theme.DarkMode

/** A single moving selection surface, without checkmarks or changing item widths. */
@Composable
internal fun AppearanceModeSelector(
  selected: DarkMode,
  onSelect: (DarkMode) -> Unit,
  modifier: Modifier = Modifier,
) {
  val modes = DarkMode.entries
  val position by animateFloatAsState(
    modes.indexOf(selected).toFloat(),
    tween(if (LocalReduceMotion.current) 0 else 240, easing = FastOutSlowInEasing),
    label = "appearance_mode_selection",
  )
  BoxWithConstraints(
    modifier.fillMaxWidth().padding(16.dp)
      .clip(RoundedCornerShape(20.dp))
      .background(MaterialTheme.colorScheme.surfaceContainerHigh)
      .padding(4.dp).height(48.dp).selectableGroup(),
  ) {
    val itemWidth = maxWidth / modes.size
    Box(
      Modifier.offset(x = itemWidth * position)
        .width(itemWidth).fillMaxHeight()
        .clip(RoundedCornerShape(16.dp))
        .background(MaterialTheme.colorScheme.primaryContainer)
    )
    Row(Modifier.fillMaxSize()) {
      modes.forEach { mode ->
        Box(
          Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp))
            .selectable(selected == mode, role = Role.RadioButton) {
              if (selected != mode) onSelect(mode)
            },
          contentAlignment = Alignment.Center,
        ) {
          Text(
            stringResource(mode.titleRes),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected == mode) MaterialTheme.colorScheme.onPrimaryContainer
              else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}
