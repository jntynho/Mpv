package live.mehiz.mpvkt.ui.player.controls.components.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.ui.icons.*
import live.mehiz.mpvkt.ui.player.TrackNode
import live.mehiz.mpvkt.ui.player.controls.components.*

@Composable
fun SubtitlesSheet(
  tracks: ImmutableList<TrackNode>, onSelect: (Int, Int) -> Unit,
  onAddSubtitle: () -> Unit, onOpenSubtitleSettings: () -> Unit,
  onOpenSubtitleDelay: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var slot by rememberSaveable { mutableIntStateOf(0) }
  val labels = listOf(stringResource(R.string.player_sheets_sub_delay_subtitle_type_primary), stringResource(R.string.player_sheets_sub_delay_subtitle_type_secondary))
  GenericTracksSheet(tracks, modifier,
    header = {
      Row(Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        labels.forEachIndexed { index, label ->
          Box(Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
            .background(if (slot == index) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest)
            .selectable(slot == index, role = Role.Tab, onClick = { slot = index }).padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge)
          }
        }
      }
    },
    beforeTracks = {
      PlayerPanelSection(stringResource(R.string.mpv_panel_tracks))
      AudioTrackRow(stringResource(R.string.player_sheets_tracks_off), tracks.none { it.mainSelection?.toInt() == slot }, { onSelect(-1, slot) })
    },
    track = { track ->
      val selected = track.mainSelection?.toInt()
      AudioTrackRow(getTrackTitle(track), selected == slot, { onSelect(track.id, slot) },
        subtitle = trackDescription(track), badge = selected?.let { labels.getOrNull(it) })
    },
    footer = {
      PlayerPanelSection(stringResource(R.string.mpv_panel_adjustments))
      PlayerActionRow(stringResource(R.string.player_sheets_sub_delay_card_title), Icons.MoreTime, onOpenSubtitleDelay)
      PlayerActionRow(stringResource(R.string.player_sheets_subtitles_settings_title), Icons.Palette, onOpenSubtitleSettings)
    },
    bottomBar = { PlayerPanelFooter(stringResource(R.string.player_sheets_add_ext_sub), onAddSubtitle,
      stringResource(R.string.player_sheets_subtitles_footer_secondary_sid_no_styles)) },
  )
}
