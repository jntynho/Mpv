package live.mehiz.mpvkt.ui.player.controls.components.sheets

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlinx.collections.immutable.ImmutableList
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.ui.player.TrackNode
import live.mehiz.mpvkt.ui.player.controls.components.*

@Composable
fun <T> GenericTracksSheet(
  tracks: ImmutableList<T>,
  modifier: Modifier = Modifier,
  header: @Composable () -> Unit = {},
  beforeTracks: @Composable () -> Unit = {},
  track: @Composable (T) -> Unit = {},
  footer: @Composable () -> Unit = {},
  bottomBar: @Composable () -> Unit = {},
) {
  Column(modifier.fillMaxSize()) {
    header()
    PlayerLazyScrollArea(Modifier.weight(1f)) {
      item { beforeTracks() }
      items(tracks) { track(it) }
      item { footer() }
    }
    bottomBar()
  }
}

@Composable
fun AudioTrackRow(
  title: String,
  isSelected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  badge: String? = null,
  animateSelection: Boolean = false,
  reduceMotion: Boolean = false,
  multiple: Boolean = false,
) {
  val selectionColor by
    animateColorAsState(
      if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f)
      else Color.Transparent,
      tween(if (animateSelection && !reduceMotion) 220 else 0),
      label = "decoder_selection",
    )
  Row(
    modifier
      .fillMaxWidth()
      .padding(horizontal = 12.dp, vertical = 2.dp)
      .clip(RoundedCornerShape(14.dp))
      .background(selectionColor)
      .then(
        if (multiple)
          Modifier.toggleable(
            value = isSelected,
            role = Role.Checkbox,
            onValueChange = { onClick() },
          )
        else Modifier.selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
      )
      .heightIn(min = 48.dp)
      .padding(horizontal = 10.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    if (multiple)
      Checkbox(checked = isSelected, onCheckedChange = null, modifier = Modifier.size(24.dp))
    else RadioButton(selected = isSelected, onClick = null, modifier = Modifier.size(24.dp))
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
      Text(
        title,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
      )
      if (!subtitle.isNullOrBlank())
        Text(
          subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (badge != null)
      Text(
        badge,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
      )
  }
}

@Composable
fun getTrackTitle(track: TrackNode): String =
  track.title?.takeIf { it.isNotBlank() }
    ?: track.lang?.takeIf { it.isNotBlank() }
    ?: stringResource(
      if (track.isAudio) R.string.player_sheets_chapter_title_substitute_audio
      else R.string.player_sheets_chapter_title_substitute_subtitle,
      track.id,
    )

@Composable
fun trackDescription(track: TrackNode): String =
  listOfNotNull(
      stringResource(R.string.mpv_track_id, track.id),
      track.lang?.takeIf { it.isNotBlank() },
      track.codec?.uppercase(Locale.ROOT),
      track.demuxChannels,
    )
    .distinct()
    .joinToString(" · ")
