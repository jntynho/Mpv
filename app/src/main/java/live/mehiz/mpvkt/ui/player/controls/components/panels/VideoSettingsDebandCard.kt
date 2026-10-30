package live.mehiz.mpvkt.ui.player.controls.components.panels

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import `is`.xyz.mpv.MPVLib
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.preferences.DecoderPreferences
import live.mehiz.mpvkt.preferences.preference.collectAsState
import live.mehiz.mpvkt.preferences.preference.deleteAndGet
import live.mehiz.mpvkt.presentation.components.ExpandableCard
import live.mehiz.mpvkt.presentation.components.SliderItem
import live.mehiz.mpvkt.ui.icons.Gpu
import live.mehiz.mpvkt.ui.icons.Gradient
import live.mehiz.mpvkt.ui.icons.Icons
import live.mehiz.mpvkt.ui.icons.Memory
import live.mehiz.mpvkt.ui.icons.NotInterested
import live.mehiz.mpvkt.ui.icons.RestartAlt
import live.mehiz.mpvkt.ui.player.DebandSettings
import live.mehiz.mpvkt.ui.player.Debanding
import live.mehiz.mpvkt.ui.player.PanelProperties
import live.mehiz.mpvkt.ui.player.PlaybackControl
import live.mehiz.mpvkt.ui.player.controls.CARDS_MAX_WIDTH
import live.mehiz.mpvkt.ui.player.controls.panelCardsColors
import live.mehiz.mpvkt.ui.theme.spacing
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import org.koin.compose.koinInject

@Composable
fun VideoSettingsDebandCard(modifier: Modifier = Modifier) {
  val decoderPreferences = koinInject<DecoderPreferences>()
  val deband by decoderPreferences.debanding.collectAsState()
  var isExpanded by rememberSaveable { mutableStateOf(true) }

  ExpandableCard(
    isExpanded,
    title = {
      Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
        Icon(Icons.Gradient, null)
        Text(stringResource(R.string.player_sheets_deband_title))
      }
    },
    onExpand = { isExpanded = !isExpanded },
    modifier.widthIn(max = CARDS_MAX_WIDTH),
    colors = panelCardsColors(),
  ) {
    ProvidePreferenceLocals {
      Column {
        Row(
          Modifier.fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = MaterialTheme.spacing.extraSmall, end = MaterialTheme.spacing.medium),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Debanding.entries.forEach {
            IconToggleButton(
              checked = deband == it,
              enabled = live.mehiz.mpvkt.ui.player.controls.components.propertyEditable("deband"),
              onCheckedChange = { _ ->
                decoderPreferences.debanding.set(it)
                applyDebanding(it, decoderPreferences.debandCpuRadius.get())
              },
            ) {
              when (it) {
                Debanding.None -> Icon(Icons.NotInterested, stringResource(it.titleRes))
                Debanding.CPU -> Icon(Icons.Memory, stringResource(it.titleRes))
                Debanding.GPU -> Icon(Icons.Gpu, stringResource(it.titleRes))
              }
            }
          }

          Text(stringResource(deband.titleRes))

          Spacer(Modifier.weight(1f))
          TextButton(
            enabled = live.mehiz.mpvkt.ui.player.controls.components.propertyEditable("deband"),
            onClick = {
              decoderPreferences.debanding.set(Debanding.None)
              applyDebanding(Debanding.None)
              decoderPreferences.debandCpuRadius.delete()
              DebandSettings.entries.forEach {
                if (PanelProperties.editable(it.mpvProperty))
                  PanelProperties.setInt(
                    it.mpvProperty,
                    it.preference(decoderPreferences).deleteAndGet(),
                  )
              }
            },
          ) {
            Row(
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(Icons.RestartAlt, null)
              Text(stringResource(R.string.generic_reset))
            }
          }
        }

        live.mehiz.mpvkt.ui.preferences.ExpandableSettingsContent(deband == Debanding.CPU) {
          val radius by decoderPreferences.debandCpuRadius.collectAsState()
          SliderItem(
            stringResource(R.string.panel_cpu_radius),
            radius.coerceIn(4, 32),
            radius.coerceIn(4, 32).toString(),
            {
              decoderPreferences.debandCpuRadius.set(it)
              applyDebanding(Debanding.CPU, it)
            },
            max = 32,
            min = 4,
            property = "deband",
          )
        }
        live.mehiz.mpvkt.ui.preferences.ExpandableSettingsContent(deband == Debanding.GPU) {
          DebandSettings.entries.forEach { debandSettings ->
            val value by debandSettings.preference(decoderPreferences).collectAsState()
            SliderItem(
              label = stringResource(debandSettings.titleRes),
              value = value,
              valueText = value.toString(),
              onChange = {
                debandSettings.preference(decoderPreferences).set(it)
                PanelProperties.setInt(debandSettings.mpvProperty, it)
              },
              min = debandSettings.start,
              max = debandSettings.end,
              property = debandSettings.mpvProperty,
            )
          }
        }
      }
    }
  }
}

private fun applyDebanding(mode: Debanding, radius: Int = 12) {
  PlaybackControl.enqueue("deband-mode") {
    if (!PanelProperties.editable("deband")) return@enqueue
    MPVLib.setPropertyString("deband", if (mode == Debanding.GPU) "yes" else "no")
    MPVLib.command("vf", "remove", "@deband")
    if (mode == Debanding.CPU)
      MPVLib.command("vf", "add", "@deband:gradfun=radius=${radius.coerceIn(4,32)}")
  }
}
