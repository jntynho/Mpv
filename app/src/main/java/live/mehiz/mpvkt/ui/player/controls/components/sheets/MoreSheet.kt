package live.mehiz.mpvkt.ui.player.controls.components.sheets

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import `is`.xyz.mpv.MPVLib
import kotlinx.collections.immutable.ImmutableList
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.database.entities.CustomButtonEntity
import live.mehiz.mpvkt.preferences.AdvancedPreferences
import live.mehiz.mpvkt.preferences.PlayerPreferences
import live.mehiz.mpvkt.preferences.preference.collectAsState
import live.mehiz.mpvkt.ui.icons.*
import live.mehiz.mpvkt.ui.player.PlaybackControl
import live.mehiz.mpvkt.ui.player.controls.components.*
import live.mehiz.mpvkt.ui.player.execute
import live.mehiz.mpvkt.ui.player.executeLongClick
import org.koin.compose.koinInject

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MoreSheet(
  remainingTime: Int, onOpenTimer: () -> Unit,
  onEnterFiltersPanel: () -> Unit, customButtons: ImmutableList<CustomButtonEntity>, modifier: Modifier = Modifier,
) {
  val advancedPreferences = koinInject<AdvancedPreferences>()
  val playerPreferences = koinInject<PlayerPreferences>()
  val statisticsPage by advancedPreferences.enabledStatisticsPage.collectAsState()
  var statsExpanded by rememberSaveable { mutableStateOf(false) }
  PlayerScrollArea(modifier.fillMaxSize()) {
        PlayerPanelSection(stringResource(R.string.mpv_panel_quick))
        val background by playerPreferences.automaticBackgroundPlayback.collectAsState()
        PlayerActionRow(stringResource(R.string.pref_enable_background_playback), Icons.Headset,
          { playerPreferences.automaticBackgroundPlayback.set(!background) },
          subtitle = stringResource(R.string.pref_enable_background_playback_summary),
          trailing = { Switch(background, onCheckedChange = playerPreferences.automaticBackgroundPlayback::set) })
        PlayerActionRow(stringResource(R.string.timer_title), Icons.Timer, onOpenTimer,
          value = if (remainingTime > 0) DateUtils.formatElapsedTime(remainingTime.toLong()) else stringResource(R.string.player_sheets_tracks_off))
        PlayerActionRow(stringResource(R.string.player_sheets_filters_title), Icons.Tune, onEnterFiltersPanel)
        PlayerPanelSection(stringResource(R.string.mpv_panel_tools))
        PlayerActionRow(stringResource(R.string.player_sheets_stats_page_title), Icons.Code, { statsExpanded = !statsExpanded },
          value = if (statisticsPage == 0) stringResource(R.string.player_sheets_tracks_off) else statisticsPage.toString())
        if (statsExpanded) repeat(6) { page ->
          AudioTrackRow(if (page == 0) stringResource(R.string.player_sheets_tracks_off) else stringResource(R.string.player_sheets_stats_page_chip, page), statisticsPage == page, {
            PlaybackControl.enqueue {
              if ((page == 0) xor (statisticsPage == 0)) MPVLib.command("script-binding", "stats/display-stats-toggle")
              if (page != 0) MPVLib.command("script-binding", "stats/display-page-$page")
            }
            advancedPreferences.enabledStatisticsPage.set(page)
            statsExpanded = false
          })
        }
        AudioChannelsControl()
        if (customButtons.isNotEmpty()) {
          PlayerPanelSection(stringResource(R.string.player_sheets_custom_buttons_title))
          customButtons.forEach { button ->
            Row(Modifier.fillMaxWidth().combinedClickable(
              onClick = { PlaybackControl.enqueue { button.execute() } },
              onLongClick = { PlaybackControl.enqueue { button.executeLongClick() } },
            ).padding(horizontal = 20.dp, vertical = 14.dp),
              verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
              Icon(Icons.Terminal, null, Modifier.size(23.dp), tint = MaterialTheme.colorScheme.primary)
              Text(button.title, style = MaterialTheme.typography.bodyLarge)
            }
          }
        }
    Spacer(Modifier.height(16.dp))
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerPanel(remainingTime: Int, onTimeSelect: (Int) -> Unit, onNavigateBack: () -> Unit) {
  val state = rememberTimePickerState(remainingTime / 3600, (remainingTime % 3600) / 60, is24Hour = true)
  var clock by rememberSaveable { mutableStateOf(false) }
  PlayerScrollArea(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
      Text(stringResource(R.string.timer_picker_enter_timer), style = MaterialTheme.typography.bodyLarge)
      BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val canShowClock = maxWidth >= 300.dp
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
          if (clock && canShowClock) TimePicker(state) else TimeInput(state)
          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (canShowClock) IconButton(onClick = { clock = !clock }) {
              Icon(if (clock) Icons.KeyboardAlt else Icons.Schedule, stringResource(R.string.timer_picker_pick_time))
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onNavigateBack) { Text(stringResource(R.string.generic_cancel)) }
            TextButton(onClick = {
              onTimeSelect(state.hour * 3600 + state.minute * 60)
              onNavigateBack()
            }) { Text(stringResource(R.string.generic_ok)) }
          }
        }
      }
    }
  }
}
