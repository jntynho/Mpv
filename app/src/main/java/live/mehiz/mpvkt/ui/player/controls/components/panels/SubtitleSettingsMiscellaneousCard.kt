package live.mehiz.mpvkt.ui.player.controls.components.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.preferences.SubtitlesPreferences
import live.mehiz.mpvkt.preferences.preference.collectAsState
import live.mehiz.mpvkt.preferences.preference.deleteAndGet
import live.mehiz.mpvkt.presentation.components.ExpandableCard
import live.mehiz.mpvkt.presentation.components.SliderItem
import live.mehiz.mpvkt.ui.icons.AlignVerticalCenter
import live.mehiz.mpvkt.ui.icons.EditOff
import live.mehiz.mpvkt.ui.icons.FormatSize
import live.mehiz.mpvkt.ui.icons.Icons
import live.mehiz.mpvkt.ui.icons.Tune
import live.mehiz.mpvkt.ui.player.MpvProperties
import live.mehiz.mpvkt.ui.player.PanelProperties
import live.mehiz.mpvkt.ui.player.controls.CARDS_MAX_WIDTH
import live.mehiz.mpvkt.ui.player.controls.components.sheets.toFixed
import live.mehiz.mpvkt.ui.player.controls.panelCardsColors
import live.mehiz.mpvkt.ui.theme.spacing
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SwitchPreference
import org.koin.compose.koinInject

@Composable
fun SubtitlesMiscellaneousCard(modifier: Modifier = Modifier) {
  val preferences = koinInject<SubtitlesPreferences>()
  var isExpanded by rememberSaveable { mutableStateOf(true) }
  ExpandableCard(
    isExpanded,
    title = {
      Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
        Icon(Icons.Tune, null)
        Text(stringResource(R.string.player_sheets_sub_misc_card_title))
      }
    },
    onExpand = { isExpanded = !isExpanded },
    modifier.widthIn(max = CARDS_MAX_WIDTH),
    colors = panelCardsColors(),
  ) {
    ProvidePreferenceLocals {
      Column {
        val overrideAssSubs by preferences.overrideAssSubs.collectAsState()
        SwitchPreference(
          overrideAssSubs,
          onValueChange = {
            preferences.overrideAssSubs.set(it)
            PanelProperties.setString("sub-ass-override", if (it) "force" else "scale")
          },
          { Text(stringResource(R.string.player_sheets_sub_override_ass)) },
        )
        val subScale by MpvProperties.propFloat["sub-scale"].collectAsState()
        val subPos by MpvProperties.propInt["sub-pos"].collectAsState()
        SliderItem(
          label = stringResource(R.string.player_sheets_sub_scale),
          value = subScale ?: preferences.subScale.get(),
          valueText = (subScale ?: preferences.subScale.get()).toFixed(2).toString(),
          onChange = {
            preferences.subScale.set(it)
            PanelProperties.setFloat("sub-scale", it)
          },
          max = 5f,
          icon = { Icon(Icons.FormatSize, null) },
        )
        SliderItem(
          label = stringResource(R.string.player_sheets_sub_position),
          value = subPos ?: preferences.subPos.get(),
          valueText = (subPos ?: preferences.subPos.get()).toString(),
          onChange = {
            preferences.subPos.set(it)
            PanelProperties.setInt("sub-pos", it)
          },
          max = 150,
          icon = { Icon(Icons.AlignVerticalCenter, null) },
        )
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .padding(end = MaterialTheme.spacing.medium, bottom = MaterialTheme.spacing.medium),
          horizontalArrangement = Arrangement.End,
        ) {
          TextButton(
            onClick = {
              preferences.subPos.deleteAndGet().let { PanelProperties.setInt("sub-pos", it) }
              preferences.subScale.deleteAndGet().let { PanelProperties.setFloat("sub-scale", it) }
              preferences.overrideAssSubs.deleteAndGet()
              PanelProperties.setString("sub-ass-override", "scale") // mpv's default is 'scale'
            }
          ) {
            Row {
              Icon(Icons.EditOff, null)
              Text(stringResource(R.string.generic_reset))
            }
          }
        }
      }
    }
  }
}
