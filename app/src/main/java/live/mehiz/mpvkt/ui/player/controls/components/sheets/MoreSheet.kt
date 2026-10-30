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
  remainingTime: Int,
  onOpenTimer: () -> Unit,
  onEnterFiltersPanel: () -> Unit,
  customButtons: ImmutableList<CustomButtonEntity>,
  modifier: Modifier = Modifier,
) {
  val advancedPreferences = koinInject<AdvancedPreferences>()
  val playerPreferences = koinInject<PlayerPreferences>()
  val statisticsPage by advancedPreferences.enabledStatisticsPage.collectAsState()
  var statsExpanded by rememberSaveable { mutableStateOf(false) }
  PlayerScrollArea(modifier.fillMaxSize()) {
    PlayerPanelSection(stringResource(R.string.mpv_panel_quick))
    val background by playerPreferences.automaticBackgroundPlayback.collectAsState()
    PlayerActionRow(
      stringResource(R.string.pref_enable_background_playback),
      Icons.Headset,
      { playerPreferences.automaticBackgroundPlayback.set(!background) },
      subtitle = stringResource(R.string.pref_enable_background_playback_summary),
      trailing = {
        Switch(background, onCheckedChange = playerPreferences.automaticBackgroundPlayback::set)
      },
    )
    PlayerActionRow(
      stringResource(R.string.timer_title),
      Icons.Timer,
      onOpenTimer,
      value =
        if (remainingTime > 0) DateUtils.formatElapsedTime(remainingTime.toLong())
        else stringResource(R.string.player_sheets_tracks_off),
    )
    PlayerActionRow(
      stringResource(R.string.player_sheets_filters_title),
      Icons.Tune,
      onEnterFiltersPanel,
    )
    PlayerPanelSection(stringResource(R.string.mpv_panel_tools))
    PlayerActionRow(
      stringResource(R.string.player_sheets_stats_page_title),
      Icons.Code,
      { statsExpanded = !statsExpanded },
      value =
        if (statisticsPage == 0) stringResource(R.string.player_sheets_tracks_off)
        else statisticsPage.toString(),
    )
    live.mehiz.mpvkt.ui.preferences.ExpandableSettingsContent(statsExpanded) {
      repeat(6) { page ->
        AudioTrackRow(
          if (page == 0) stringResource(R.string.player_sheets_tracks_off)
          else stringResource(R.string.player_sheets_stats_page_chip, page),
          statisticsPage == page,
          {
            PlaybackControl.enqueue {
              if ((page == 0) xor (statisticsPage == 0))
                MPVLib.command("script-binding", "stats/display-stats-toggle")
              if (page != 0) MPVLib.command("script-binding", "stats/display-page-$page")
            }
            advancedPreferences.enabledStatisticsPage.set(page)
            statsExpanded = false
          },
        )
      }
    }
    AudioChannelsControl()
    if (customButtons.isNotEmpty()) {
      PlayerPanelSection(stringResource(R.string.player_sheets_custom_buttons_title))
      customButtons.forEach { button ->
        Row(
          Modifier.fillMaxWidth()
            .combinedClickable(
              onClick = { PlaybackControl.enqueue { button.execute() } },
              onLongClick = { PlaybackControl.enqueue { button.executeLongClick() } },
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Icon(Icons.Terminal, null, Modifier.size(23.dp), tint = MaterialTheme.colorScheme.primary)
          Text(button.title, style = MaterialTheme.typography.bodyLarge)
        }
      }
    }
    Spacer(Modifier.height(16.dp))
  }
}

@Composable
fun SleepTimerPanel(remainingTime: Int, onTimeSelect: (Int) -> Unit, onNavigateBack: () -> Unit) {
  var hours by rememberSaveable { mutableStateOf((remainingTime / 3600).toString()) }
  var minutes by rememberSaveable { mutableStateOf(((remainingTime % 3600) / 60).toString()) }
  var seconds by rememberSaveable { mutableStateOf((remainingTime % 60).toString()) }
  var dirty by rememberSaveable { mutableStateOf(false) }
  val h = live.mehiz.mpvkt.presentation.components.validInteger(hours, 0, 99)
  val m = live.mehiz.mpvkt.presentation.components.validInteger(minutes, 0, 59)
  val s = live.mehiz.mpvkt.presentation.components.validInteger(seconds, 0, 59)
  val valid = h != null && m != null && s != null
  PlayerScrollArea(Modifier.fillMaxSize()) {
    Column(
      Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text(
        stringResource(R.string.timer_picker_enter_timer),
        style = MaterialTheme.typography.bodyLarge,
      )
      Text(
        DateUtils.formatElapsedTime(remainingTime.coerceAtLeast(0).toLong()),
        style = MaterialTheme.typography.headlineSmall,
      )
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        fun filtered(raw: String, change: (String) -> Unit) {
          if (raw.length <= 2 && raw.all(Char::isDigit)) {
            dirty = true
            change(raw)
          }
        }
        PanelTextField(
          hours,
          { raw -> filtered(raw) { hours = it } },
          Modifier.weight(1f),
          label = { Text(stringResource(R.string.panel_hours)) },
          isError = h == null,
          keyboardOptions =
            androidx.compose.foundation.text.KeyboardOptions(
              keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
            ),
        )
        PanelTextField(
          minutes,
          { raw -> filtered(raw) { minutes = it } },
          Modifier.weight(1f),
          label = { Text(stringResource(R.string.panel_minutes)) },
          isError = m == null,
          keyboardOptions =
            androidx.compose.foundation.text.KeyboardOptions(
              keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
            ),
        )
        PanelTextField(
          seconds,
          { raw -> filtered(raw) { seconds = it } },
          Modifier.weight(1f),
          label = { Text(stringResource(R.string.panel_seconds)) },
          isError = s == null,
          keyboardOptions =
            androidx.compose.foundation.text.KeyboardOptions(
              keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
            ),
        )
      }
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        if (remainingTime > 0)
          TextButton(
            onClick = {
              onTimeSelect(0)
              onNavigateBack()
            }
          ) {
            Text(stringResource(R.string.panel_stop_timer))
          }
        TextButton(onClick = onNavigateBack) { Text(stringResource(R.string.generic_cancel)) }
        TextButton(
          enabled = valid,
          onClick = {
            if (dirty && valid) onTimeSelect(h!! * 3600 + m!! * 60 + s!!)
            onNavigateBack()
          },
        ) {
          Text(stringResource(R.string.generic_ok))
        }
      }
    }
  }
}
