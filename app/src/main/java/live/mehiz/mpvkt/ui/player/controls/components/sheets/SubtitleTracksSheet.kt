package live.mehiz.mpvkt.ui.player.controls.components.sheets

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.ui.icons.*
import live.mehiz.mpvkt.ui.motion.LocalReduceMotion
import live.mehiz.mpvkt.ui.player.TrackNode
import live.mehiz.mpvkt.ui.player.controls.components.*

@Composable
fun SubtitlesSheet(
  tracks: ImmutableList<TrackNode>,
  onSelect: (Int, Int) -> Unit,
  onAddSubtitle: (Int) -> Unit,
  onSearchOnline: (Int) -> Unit,
  onOpenSubtitleSettings: (Int) -> Unit,
  onOpenSubtitleDelay: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  var slot by rememberSaveable { mutableIntStateOf(0) }
  val reduceMotion = LocalReduceMotion.current
  val scrollStates = listOf(rememberLazyListState(), rememberLazyListState())
  val motionDirection = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1
  val labels =
    listOf(
      stringResource(R.string.subtitle_primary),
      stringResource(R.string.subtitle_secondary_track),
    )
  Column(modifier.fillMaxSize()) {
    SubtitleSlotSelector(slot, { slot = it }, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = { onOpenSubtitleSettings(slot) }) {
        Icon(Icons.Tune, stringResource(R.string.player_sheets_subtitles_settings_title))
      }
      IconButton(onClick = { onOpenSubtitleDelay(slot) }) {
        Icon(Icons.MoreTime, stringResource(R.string.player_sheets_sub_delay_card_title))
      }
      IconButton(onClick = { onAddSubtitle(slot) }) {
        Icon(Icons.Upload, stringResource(R.string.player_sheets_add_ext_sub))
      }
      IconButton(onClick = { onSearchOnline(slot) }) {
        Icon(Icons.Search, stringResource(R.string.subtitle_open_online))
      }
    }
    AnimatedContent(
      targetState = slot,
      modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds(),
      transitionSpec = {
        val direction = (if (targetState > initialState) 1 else -1) * motionDirection
        val duration = if (reduceMotion) 0 else 220
        ((fadeIn(tween(duration)) +
            slideInHorizontally(tween(duration)) { direction * it / 16 }) togetherWith
            (fadeOut(tween(if (reduceMotion) 0 else 140)) +
              slideOutHorizontally(tween(duration)) { -direction * it / 16 }))
          .using(SizeTransform(clip = true, sizeAnimationSpec = { _, _ -> tween(0) }))
      },
      label = "subtitle_tracks_slot",
    ) { selectedSlot ->
      PlayerLazyScrollArea(Modifier.fillMaxSize(), scrollStates[selectedSlot]) {
        item("tracks") { PlayerPanelSection(stringResource(R.string.mpv_panel_tracks)) }
        item("off") {
          AudioTrackRow(
            stringResource(R.string.player_sheets_tracks_off),
            tracks.none { it.mainSelection?.toInt() == selectedSlot },
            { if (slot == selectedSlot) onSelect(-1, selectedSlot) },
          )
        }
        items(tracks, key = { it.id }) { track ->
          val selected = track.mainSelection?.toInt()
          AudioTrackRow(
            getTrackTitle(track),
            selected == selectedSlot,
            { if (slot == selectedSlot) onSelect(track.id, selectedSlot) },
            subtitle = trackDescription(track),
            badge = selected?.let { labels.getOrNull(it) },
          )
        }
        if (selectedSlot == 1)
          item("secondary-note") {
            Text(
              stringResource(R.string.player_sheets_subtitles_footer_secondary_sid_no_styles),
              Modifier.padding(20.dp),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
      }
    }
  }
}
