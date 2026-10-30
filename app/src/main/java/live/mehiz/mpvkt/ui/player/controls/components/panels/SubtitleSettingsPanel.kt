package live.mehiz.mpvkt.ui.player.controls.components.panels

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.preferences.SubtitlesPreferences
import live.mehiz.mpvkt.preferences.preference.collectAsState
import live.mehiz.mpvkt.presentation.components.SliderItem
import live.mehiz.mpvkt.ui.player.PanelProperties
import live.mehiz.mpvkt.ui.player.controls.components.*
import org.koin.compose.koinInject

@Composable
fun SubtitleSettingsPanel(modifier: Modifier = Modifier, secondary: Boolean = false) {
  PlayerScrollArea(modifier.fillMaxSize()) {
    PanelConfigNotice()
    if (secondary) {
      val prefs = koinInject<SubtitlesPreferences>()
      val supported by prefs.secondaryScaleSupported.collectAsState()
      val scale by prefs.secondaryScale.collectAsState()
      val observedScale by
        live.mehiz.mpvkt.ui.player.MpvProperties.propFloat["secondary-sub-scale"].collectAsState()
      val currentScale = observedScale ?: scale
      Text(
        stringResource(R.string.player_sheets_subtitles_footer_secondary_sid_no_styles),
        Modifier.padding(16.dp),
      )
      if (supported)
        SliderItem(
          stringResource(R.string.player_sheets_sub_scale),
          currentScale,
          currentScale.toString(),
          {
            prefs.secondaryScale.set(it)
            PanelProperties.setFloat("secondary-sub-scale", it)
          },
          max = 5f,
          min = .1f,
          property = "secondary-sub-scale",
        )
    } else {
      SubtitleSettingsTypographyCard(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
      )
      SubtitleSettingsColorsCard(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
      )
      SubtitlesMiscellaneousCard(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
      )
    }
  }
}
