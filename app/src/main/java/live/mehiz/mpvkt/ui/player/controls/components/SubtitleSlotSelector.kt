package live.mehiz.mpvkt.ui.player.controls.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.ui.motion.LocalReduceMotion

/** One sliding indicator; changing slot never changes the selector's measured size. */
@Composable
internal fun SubtitleSlotSelector(
  slot: Int,
  onSelect: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val duration = if (LocalReduceMotion.current) 0 else 220
  val position by
    animateFloatAsState(slot.coerceIn(0, 1).toFloat(), tween(duration), label = "subtitle_slot")
  BoxWithConstraints(
    modifier
      .fillMaxWidth()
      .height(48.dp)
      .clip(RoundedCornerShape(24.dp))
      .background(MaterialTheme.colorScheme.surfaceContainerHighest)
      .padding(4.dp)
  ) {
    val width = maxWidth / 2
    Box(
      Modifier.offset { IntOffset((width * position).roundToPx(), 0) }
        .width(width)
        .fillMaxHeight()
        .clip(RoundedCornerShape(20.dp))
        .background(MaterialTheme.colorScheme.primaryContainer)
    )
    Row(Modifier.fillMaxSize().selectableGroup()) {
      listOf(R.string.subtitle_primary, R.string.subtitle_secondary_track).forEachIndexed {
        index,
        label ->
        val color by
          animateColorAsState(
            if (slot == index) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            tween(duration),
            label = "subtitle_slot_label",
          )
        Box(
          Modifier.weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(20.dp))
            .selectable(slot == index, role = Role.Tab, onClick = { onSelect(index) }),
          contentAlignment = Alignment.Center,
        ) {
          Text(stringResource(label), color = color, style = MaterialTheme.typography.labelLarge)
        }
      }
    }
  }
}
