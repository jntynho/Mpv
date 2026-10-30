package live.mehiz.mpvkt.ui.player.controls.components.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import `is`.xyz.mpv.MPVLib
import kotlin.math.pow
import kotlin.math.roundToInt
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.preferences.AudioPreferences
import live.mehiz.mpvkt.preferences.preference.collectAsState
import live.mehiz.mpvkt.presentation.components.SliderItem
import live.mehiz.mpvkt.ui.icons.Add
import live.mehiz.mpvkt.ui.icons.Close
import live.mehiz.mpvkt.ui.icons.Icons
import live.mehiz.mpvkt.ui.icons.RestartAlt
import live.mehiz.mpvkt.ui.player.PlaybackControl
import live.mehiz.mpvkt.ui.player.controls.components.PlayerScrollArea
import live.mehiz.mpvkt.ui.theme.spacing
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SwitchPreference
import org.koin.compose.koinInject

@Composable
fun PlaybackSpeedSheet(
  speed: Float,
  speedPresets: List<Float>,
  onSpeedChange: (Float) -> Unit,
  onAddSpeedPreset: (Float) -> Unit,
  onRemoveSpeedPreset: (Float) -> Unit,
  onResetPresets: () -> Unit,
  onMakeDefault: (Float) -> Unit,
  onResetDefault: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val editable = live.mehiz.mpvkt.ui.player.controls.components.propertyEditable("speed")
  val reduceMotion = live.mehiz.mpvkt.ui.motion.LocalReduceMotion.current
  PlayerScrollArea(modifier.fillMaxSize().padding(vertical = MaterialTheme.spacing.medium)) {
    live.mehiz.mpvkt.ui.player.controls.components.PanelConfigNotice(setOf("speed"))
    SliderItem(
      label = stringResource(id = R.string.player_sheets_speed_slider_label),
      value = speed,
      valueText = stringResource(id = R.string.player_speed, speed),
      onChange = onSpeedChange,
      max = 6f,
      min = 0.01f,
      property = "speed",
    )
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.medium),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
      FilledTonalIconButton(onClick = onResetPresets) {
        Icon(Icons.RestartAlt, stringResource(R.string.generic_reset))
      }
      LazyRow(
        modifier = Modifier.weight(1f),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
      ) {
        items(speedPresets, key = { it }) {
          InputChip(
            selected = speed == it,
            enabled = editable,
            onClick = { onSpeedChange(it) },
            label = { Text(stringResource(R.string.player_speed, it)) },
            modifier =
              Modifier.animateItem(
                fadeInSpec = androidx.compose.animation.core.tween(if (reduceMotion) 0 else 180),
                placementSpec = androidx.compose.animation.core.tween(if (reduceMotion) 0 else 220),
                fadeOutSpec = androidx.compose.animation.core.tween(if (reduceMotion) 0 else 120),
              ),
            trailingIcon = {
              Icon(
                Icons.Close,
                stringResource(R.string.panel_remove_speed),
                modifier =
                  Modifier.clickable(enabled = editable) { onRemoveSpeedPreset(it.toFixed(2)) },
              )
            },
          )
        }
      }
      FilledTonalIconButton(onClick = { onAddSpeedPreset(speed.toFixed(2)) }) {
        Icon(Icons.Add, stringResource(R.string.panel_add_speed))
      }
    }
    ProvidePreferenceLocals {
      val audioPreferences = koinInject<AudioPreferences>()
      val pitchCorrection by audioPreferences.audioPitchCorrection.collectAsState()
      SwitchPreference(
        value = pitchCorrection,
        onValueChange = {
          audioPreferences.audioPitchCorrection.set(it)
          PlaybackControl.enqueue("pitch") {
            MPVLib.setPropertyBoolean("audio-pitch-correction", it)
          }
        },
        title = { Text(text = stringResource(id = R.string.pref_audio_pitch_correction_title)) },
        summary = { Text(text = stringResource(id = R.string.pref_audio_pitch_correction_summary)) },
      )
    }
    Row(
      modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium),
      horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
    ) {
      Button(
        modifier = Modifier.weight(1f),
        enabled = editable,
        onClick = { onMakeDefault(speed) },
      ) {
        Text(text = stringResource(id = R.string.player_sheets_speed_make_default))
      }
      FilledIconButton(onClick = onResetDefault, enabled = editable) {
        Icon(
          imageVector = Icons.RestartAlt,
          contentDescription = stringResource(R.string.generic_reset),
        )
      }
    }
  }
}

fun Float.toFixed(precision: Int = 1): Float {
  val factor = 10.0f.pow(precision)
  return (this * factor).roundToInt() / factor
}
