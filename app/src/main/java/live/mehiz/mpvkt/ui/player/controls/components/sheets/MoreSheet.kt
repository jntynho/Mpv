package live.mehiz.mpvkt.ui.player.controls.components.sheets

import android.text.format.DateUtils
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Switch
import live.mehiz.mpvkt.presentation.components.LocalPlayerSheetClose
import live.mehiz.mpvkt.ui.player.PlaybackControl
import live.mehiz.mpvkt.ui.icons.*
import live.mehiz.mpvkt.ui.player.controls.components.*

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import `is`.xyz.mpv.MPVLib
import kotlinx.collections.immutable.ImmutableList
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.database.entities.CustomButtonEntity
import live.mehiz.mpvkt.preferences.AdvancedPreferences
import live.mehiz.mpvkt.preferences.PlayerPreferences
import live.mehiz.mpvkt.preferences.preference.collectAsState
import live.mehiz.mpvkt.presentation.components.PlayerSheet
import live.mehiz.mpvkt.ui.player.execute
import live.mehiz.mpvkt.ui.player.executeLongClick
import live.mehiz.mpvkt.ui.theme.spacing
import org.koin.compose.koinInject

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MoreSheet(
  remainingTime: Int, onStartTimer: (Int) -> Unit, onDismissRequest: () -> Unit,
  onEnterFiltersPanel: () -> Unit, customButtons: ImmutableList<CustomButtonEntity>, modifier: Modifier = Modifier,
) {
  val advancedPreferences = koinInject<AdvancedPreferences>()
  val playerPreferences = koinInject<PlayerPreferences>()
  val statisticsPage by advancedPreferences.enabledStatisticsPage.collectAsState()
  var statsExpanded by remember { mutableStateOf(false) }
  var timerShown by remember { mutableStateOf(false) }
  PlayerSheet(onDismissRequest, modifier) {
    val close = LocalPlayerSheetClose.current
    Column(Modifier.fillMaxSize()) {
      PlayerPanelHeader(stringResource(R.string.player_sheets_more_title), Icons.MoreVert)
      PlayerScrollArea(Modifier.weight(1f)) {
        PlayerPanelSection(stringResource(R.string.mpv_panel_quick))
        val background by playerPreferences.automaticBackgroundPlayback.collectAsState()
        PlayerActionRow(stringResource(R.string.pref_enable_background_playback), Icons.Headset,
          { playerPreferences.automaticBackgroundPlayback.set(!background) },
          subtitle = stringResource(R.string.pref_enable_background_playback_summary),
          trailing = { Switch(background, onCheckedChange = playerPreferences.automaticBackgroundPlayback::set) })
        PlayerActionRow(stringResource(R.string.timer_title), Icons.Timer, { timerShown = true },
          value = if (remainingTime > 0) DateUtils.formatElapsedTime(remainingTime.toLong()) else stringResource(R.string.player_sheets_tracks_off))
        PlayerActionRow(stringResource(R.string.player_sheets_filters_title), Icons.Tune, { close { onEnterFiltersPanel() } })
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
    if (timerShown) TimePickerDialog(remainingTime = remainingTime, onDismissRequest = { timerShown = false }, onTimeSelect = onStartTimer)
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
  onDismissRequest: () -> Unit,
  onTimeSelect: (Int) -> Unit,
  modifier: Modifier = Modifier,
  remainingTime: Int = 0,
) {
  Dialog(
    onDismissRequest = onDismissRequest,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      shape = MaterialTheme.shapes.medium,
      color = MaterialTheme.colorScheme.surface,
      modifier = modifier.padding(MaterialTheme.spacing.medium),
    ) {
      Column(
        modifier = Modifier
          .verticalScroll(rememberScrollState())
          .width(IntrinsicSize.Max)
          .animateContentSize()
          .padding(MaterialTheme.spacing.medium),
      ) {
        var currentLayoutType by rememberSaveable { mutableIntStateOf(0) }
        Text(
          text = stringResource(
            id = if (currentLayoutType == 1) {
              R.string.timer_picker_pick_time
            } else {
              R.string.timer_picker_enter_timer
            },
          ),
        )

        val state = rememberTimePickerState(
          remainingTime / 3600,
          (remainingTime % 3600) / 60,
          is24Hour = true,
        )
        Box(
          contentAlignment = Alignment.Center,
        ) {
          if (currentLayoutType == 1) {
            TimePicker(state = state)
          } else {
            TimeInput(state = state)
          }
        }
        Row(
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier.fillMaxWidth(),
        ) {
          IconButton(onClick = { currentLayoutType = if (currentLayoutType == 0) 1 else 0 }) {
            Icon(
              imageVector = if (currentLayoutType == 0) Icons.Schedule else Icons.KeyboardAlt,
              contentDescription = null,
            )
          }
          Row {
            TextButton(onClick = onDismissRequest) {
              Text(stringResource(id = R.string.generic_cancel))
            }
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(
              onClick = {
                onTimeSelect(state.hour * 3600 + state.minute * 60)
                onDismissRequest()
              },
            ) {
              Text(stringResource(id = R.string.generic_ok))
            }
          }
        }
      }
    }
  }
}
