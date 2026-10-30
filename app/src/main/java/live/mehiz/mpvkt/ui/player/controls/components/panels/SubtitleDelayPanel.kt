package live.mehiz.mpvkt.ui.player.controls.components.panels

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import `is`.xyz.mpv.MPVLib
import kotlin.math.round
import kotlinx.coroutines.delay
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.preferences.SubtitlesPreferences
import live.mehiz.mpvkt.presentation.components.OutlinedNumericChooser
import live.mehiz.mpvkt.ui.icons.ArrowDropDown
import live.mehiz.mpvkt.ui.icons.Icons
import live.mehiz.mpvkt.ui.icons.Refresh
import live.mehiz.mpvkt.ui.player.MpvProperties
import live.mehiz.mpvkt.ui.player.PanelProperties
import live.mehiz.mpvkt.ui.player.controls.CARDS_MAX_WIDTH
import live.mehiz.mpvkt.ui.player.controls.components.PlayerScrollArea
import live.mehiz.mpvkt.ui.player.controls.panelCardsColors
import live.mehiz.mpvkt.ui.theme.spacing
import org.koin.compose.koinInject

@Composable
fun SubtitleDelayPanel(modifier: Modifier = Modifier, initialSlot: Int = 0) {
  val preferences = koinInject<SubtitlesPreferences>()

  PlayerScrollArea(modifier.fillMaxSize()) {
    var affectedSubtitle by
      remember(initialSlot) {
        mutableStateOf(
          if (initialSlot == 1) SubtitleDelayType.Secondary else SubtitleDelayType.Primary
        )
      }
    val delay by MpvProperties.propDouble["sub-delay"].collectAsState()
    val delayInt by remember { derivedStateOf { ((delay ?: 0.0) * 1000).toInt() } }
    val secondaryDelay by MpvProperties.propDouble["secondary-sub-delay"].collectAsState()
    val secondaryDelayInt by remember {
      derivedStateOf { ((secondaryDelay ?: 0.0) * 1000).toInt() }
    }
    val speed by MpvProperties.propFloat["sub-speed"].collectAsState()
    SubtitleDelayCard(
      delayMs =
        if (affectedSubtitle == SubtitleDelayType.Secondary) secondaryDelayInt else delayInt,
      onDelayChange = {
        when (affectedSubtitle) {
          SubtitleDelayType.Both -> {
            PanelProperties.setFloat("sub-delay", it / 1000f)
            PanelProperties.setFloat("secondary-sub-delay", it / 1000f)
          }

          SubtitleDelayType.Primary -> PanelProperties.setFloat("sub-delay", it / 1000f)
          else -> PanelProperties.setFloat("secondary-sub-delay", it / 1000f)
        }
      },
      speed = speed ?: preferences.defaultSubSpeed.get(),
      onSpeedChange = { PanelProperties.setFloat("sub-speed", round(it * 1000) / 1000f) },
      affectedSubtitle = affectedSubtitle,
      onTypeChange = { affectedSubtitle = it },
      onApply = {
        val target = affectedSubtitle
        live.mehiz.mpvkt.ui.player.PlaybackControl.enqueue("subtitle-delay-default") {
          if (target != SubtitleDelayType.Secondary) {
            MPVLib.getPropertyDouble("sub-delay")
              ?.takeIf { it.isFinite() }
              ?.let { preferences.defaultSubDelay.set((it * 1000).toInt()) }
            MPVLib.getPropertyFloat("sub-speed")
              ?.takeIf { it.isFinite() && it in .1f..10f }
              ?.let(preferences.defaultSubSpeed::set)
          }
          if (target != SubtitleDelayType.Primary)
            MPVLib.getPropertyDouble("secondary-sub-delay")
              ?.takeIf { it.isFinite() }
              ?.let { preferences.defaultSecondarySubDelay.set((it * 1000).toInt()) }
        }
      },
      onReset = {
        if (affectedSubtitle != SubtitleDelayType.Secondary) {
          PanelProperties.setFloat("sub-delay", preferences.defaultSubDelay.get() / 1000f)
          PanelProperties.setFloat("sub-speed", preferences.defaultSubSpeed.get())
        }
        if (affectedSubtitle != SubtitleDelayType.Primary)
          PanelProperties.setFloat(
            "secondary-sub-delay",
            preferences.defaultSecondarySubDelay.get() / 1000f,
          )
      },
      modifier = Modifier.fillMaxWidth().padding(12.dp),
    )
  }
}

@Composable
fun SubtitleDelayCard(
  delayMs: Int,
  onDelayChange: (Int) -> Unit,
  speed: Float,
  onSpeedChange: (Float) -> Unit,
  affectedSubtitle: SubtitleDelayType,
  onTypeChange: (SubtitleDelayType) -> Unit,
  onApply: () -> Unit,
  onReset: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val primaryEditable = live.mehiz.mpvkt.ui.player.controls.components.propertyEditable("sub-delay")
  val secondaryEditable =
    live.mehiz.mpvkt.ui.player.controls.components.propertyEditable("secondary-sub-delay")
  val editable =
    when (affectedSubtitle) {
      SubtitleDelayType.Primary -> primaryEditable
      SubtitleDelayType.Secondary -> secondaryEditable
      SubtitleDelayType.Both -> primaryEditable && secondaryEditable
    }
  DelayCard(
    delayMs = delayMs,
    measurementKey = affectedSubtitle,
    editable = editable,
    onDelayChange = onDelayChange,
    onApply = onApply,
    onReset = onReset,
    title = {
      SubtitleDelayTitle(affectedSubtitle = affectedSubtitle, onTypeChange = onTypeChange)
    },
    extraSettings = {
      live.mehiz.mpvkt.ui.preferences.ExpandableSettingsContent(
        affectedSubtitle == SubtitleDelayType.Primary
      ) {
        OutlinedNumericChooser(
          label = { Text(stringResource(R.string.player_sheets_sub_delay_card_speed)) },
          value = speed,
          onChange = onSpeedChange,
          max = 10f,
          step = .01f,
          min = .1f,
          enabled = live.mehiz.mpvkt.ui.player.controls.components.propertyEditable("sub-speed"),
        )
      }
    },
    delayType = DelayType.Subtitle,
    modifier = modifier,
  )
}

enum class SubtitleDelayType(@StringRes val title: Int) {
  Primary(R.string.player_sheets_sub_delay_subtitle_type_primary),
  Secondary(R.string.player_sheets_sub_delay_subtitle_type_secondary),
  Both(R.string.player_sheets_sub_delay_subtitle_type_primary_and_secondary),
}

@Suppress("LambdaParameterInRestartableEffect") // Intentional
@Composable
fun DelayCard(
  delayMs: Int,
  onDelayChange: (Int) -> Unit,
  onApply: () -> Unit,
  onReset: () -> Unit,
  title: @Composable () -> Unit,
  delayType: DelayType,
  modifier: Modifier = Modifier,
  extraSettings: @Composable ColumnScope.() -> Unit = {},
  measurementKey: Any? = delayType,
  editable: Boolean = true,
) {
  val mediaKey = live.mehiz.mpvkt.ui.player.controls.components.LocalPanelMediaKey.current
  var measurement by
    remember(measurementKey, mediaKey, editable) { mutableStateOf<DelayMeasurement?>(null) }
  var preview by remember(measurementKey, mediaKey) { mutableIntStateOf(delayMs) }
  LaunchedEffect(measurement) {
    val active = measurement ?: return@LaunchedEffect
    while (true) {
      preview = active.valueAt(android.os.SystemClock.elapsedRealtime())
      delay(50)
    }
  }
  fun press(positive: Boolean) {
    if (!editable) return
    val active = measurement
    if (active == null) {
      measurement = DelayMeasurement(android.os.SystemClock.elapsedRealtime(), delayMs, positive)
      preview = delayMs
    } else {
      val result = active.valueAt(android.os.SystemClock.elapsedRealtime())
      measurement = null
      onDelayChange(result)
    }
  }
  Card(modifier = modifier.widthIn(max = CARDS_MAX_WIDTH), colors = panelCardsColors()) {
    Column(
      Modifier.padding(
        horizontal = MaterialTheme.spacing.medium,
        vertical = MaterialTheme.spacing.smaller,
      ),
      verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
    ) {
      title()
      OutlinedNumericChooser(
        label = { Text(stringResource(R.string.player_sheets_sub_delay_card_delay)) },
        value = if (measurement == null) delayMs else preview,
        enabled = editable && measurement == null,
        onChange = onDelayChange,
        step = 50,
        min = Int.MIN_VALUE,
        max = Int.MAX_VALUE,
        suffix = { Text(stringResource(R.string.generic_unit_ms)) },
      )
      extraSettings()
      if (!editable)
        Text(
          stringResource(R.string.panel_config_owned),
          style = MaterialTheme.typography.bodySmall,
        )
      Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller)) {
        Button(
          onClick = { press(delayType == DelayType.Audio) },
          modifier = Modifier.weight(1f),
          enabled = editable && measurement?.positive != (delayType == DelayType.Audio),
        ) {
          Text(
            stringResource(
              if (delayType == DelayType.Audio) {
                R.string.player_sheets_sub_delay_audio_sound_heard
              } else {
                R.string.player_sheets_sub_delay_subtitle_voice_heard
              }
            )
          )
        }
        Button(
          onClick = { press(delayType != DelayType.Audio) },
          modifier = Modifier.weight(1f),
          enabled = editable && measurement?.positive != (delayType == DelayType.Subtitle),
        ) {
          Text(
            stringResource(
              if (delayType == DelayType.Audio) {
                R.string.player_sheets_sub_delay_sound_sound_spotted
              } else {
                R.string.player_sheets_sub_delay_subtitle_text_seen
              }
            )
          )
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller)) {
        Button(
          onClick = onApply,
          modifier = Modifier.weight(1f),
          enabled = editable && measurement == null,
        ) {
          Text(stringResource(R.string.player_sheets_delay_set_as_default))
        }
        FilledIconButton(onClick = onReset, enabled = editable && measurement == null) {
          Icon(Icons.Refresh, stringResource(R.string.generic_reset))
        }
      }
    }
  }
}

@Composable
fun SubtitleDelayTitle(
  affectedSubtitle: SubtitleDelayType,
  onTypeChange: (SubtitleDelayType) -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    verticalAlignment = Alignment.Bottom,
    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
    modifier = modifier.fillMaxWidth(),
  ) {
    Text(
      stringResource(R.string.player_sheets_sub_delay_affected_subtitle),
      modifier = Modifier.weight(1f),
      style = MaterialTheme.typography.titleLarge,
    )
    var showDropDownMenu by remember { mutableStateOf(false) }
    Row(modifier = Modifier.clickable { showDropDownMenu = true }) {
      Text(
        stringResource(affectedSubtitle.title),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
      )
      Icon(Icons.ArrowDropDown, null)
      DropdownMenu(expanded = showDropDownMenu, onDismissRequest = { showDropDownMenu = false }) {
        SubtitleDelayType.entries.forEach {
          DropdownMenuItem(
            text = { Text(stringResource(it.title)) },
            onClick = {
              onTypeChange(it)
              showDropDownMenu = false
            },
          )
        }
      }
    }
  }
}

enum class DelayType {
  Audio,
  Subtitle,
}
