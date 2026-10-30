package live.mehiz.mpvkt.ui.player.controls.components.panels

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.alpha
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.core.graphics.toColorInt
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.preferences.SubtitlesPreferences
import live.mehiz.mpvkt.preferences.preference.Preference
import live.mehiz.mpvkt.preferences.preference.deleteAndGet
import live.mehiz.mpvkt.presentation.components.ExpandableCard
import live.mehiz.mpvkt.presentation.components.TintedSliderItem
import live.mehiz.mpvkt.ui.icons.BorderColor
import live.mehiz.mpvkt.ui.icons.FormatColorFill
import live.mehiz.mpvkt.ui.icons.FormatColorReset
import live.mehiz.mpvkt.ui.icons.FormatColorText
import live.mehiz.mpvkt.ui.icons.Icons
import live.mehiz.mpvkt.ui.icons.Palette
import live.mehiz.mpvkt.ui.player.MpvProperties
import live.mehiz.mpvkt.ui.player.PanelProperties
import live.mehiz.mpvkt.ui.player.controls.CARDS_MAX_WIDTH
import live.mehiz.mpvkt.ui.player.controls.panelCardsColors
import live.mehiz.mpvkt.ui.theme.spacing
import org.koin.compose.koinInject

@Composable
fun SubtitleSettingsColorsCard(modifier: Modifier = Modifier) {
  val preferences = koinInject<SubtitlesPreferences>()
  var isExpanded by rememberSaveable { mutableStateOf(true) }
  ExpandableCard(
    isExpanded = isExpanded,
    onExpand = { isExpanded = !isExpanded },
    title = {
      Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
        Icon(Icons.Palette, null)
        Text(stringResource(R.string.player_sheets_sub_colors_card_title))
      }
    },
    modifier = modifier.widthIn(max = CARDS_MAX_WIDTH),
    colors = panelCardsColors(),
  ) {
    Column {
      var currentColorType by rememberSaveable { mutableStateOf(SubColorType.Text) }
      val mediaKey = live.mehiz.mpvkt.ui.player.controls.components.LocalPanelMediaKey.current
      val editable =
        live.mehiz.mpvkt.ui.player.controls.components.propertyEditable(currentColorType.property)
      val native by MpvProperties.propString[currentColorType.property].collectAsState()
      val actual =
        runCatching { native?.toColorInt() }.getOrNull()
          ?: currentColorType.preference(preferences).get()
      var pending by remember(mediaKey, currentColorType) { mutableStateOf<Int?>(null) }
      val currentColor = pending ?: actual
      LaunchedEffect(actual, pending) { if (pending == actual) pending = null }
      LaunchedEffect(pending) {
        if (pending != null) {
          kotlinx.coroutines.delay(1500)
          pending = null
        }
      }
      Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier =
          Modifier.fillMaxWidth()
            .padding(start = MaterialTheme.spacing.extraSmall, end = MaterialTheme.spacing.medium),
      ) {
        SubColorType.entries.forEach { type ->
          IconToggleButton(
            checked = currentColorType == type,
            onCheckedChange = { currentColorType = type },
          ) {
            Icon(
              when (type) {
                SubColorType.Text -> Icons.FormatColorText
                SubColorType.Border -> Icons.BorderColor
                SubColorType.Background -> Icons.FormatColorFill
              },
              stringResource(type.titleRes),
            )
          }
        }
        Spacer(Modifier.weight(1f))
        TextButton(
          enabled = editable,
          onClick = { pending = resetColors(preferences, currentColorType) },
        ) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(Icons.FormatColorReset, null)
            Text(stringResource(R.string.generic_reset))
          }
        }
      }
      Text(
        stringResource(currentColorType.titleRes),
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelLarge,
      )
      SubtitlesColorPicker(
        currentColor,
        enabled = editable,
        onColorChange = {
          pending = it
          currentColorType.preference(preferences).set(it)
          PanelProperties.setString(currentColorType.property, it.toColorHexString())
        },
      )
    }
  }
}

fun Int.copyAsArgb(
  alpha: Int = this.alpha,
  red: Int = this.red,
  green: Int = this.green,
  blue: Int = this.blue,
) = (alpha shl 24) or (red shl 16) or (green shl 8) or blue

@OptIn(ExperimentalStdlibApi::class)
fun Int.toColorHexString() = "#" + this.toHexString().uppercase()

enum class SubColorType(
  @StringRes val titleRes: Int,
  val property: String,
  val preference: (SubtitlesPreferences) -> Preference<Int>,
) {
  Text(
    R.string.player_sheets_subtitles_color_text,
    "sub-color",
    preference = SubtitlesPreferences::textColor,
  ),
  Border(
    R.string.player_sheets_subtitles_color_border,
    "sub-border-color",
    preference = SubtitlesPreferences::borderColor,
  ),
  Background(
    R.string.player_sheets_subtitles_color_background,
    "sub-back-color",
    preference = SubtitlesPreferences::backgroundColor,
  ),
}

fun resetColors(preferences: SubtitlesPreferences, type: SubColorType): Int {
  if (!PanelProperties.editable(type.property))
    return getCurrentMPVColor(type, type.preference(preferences).get())
  val value = type.preference(preferences).deleteAndGet()
  PanelProperties.setString(type.property, value.toColorHexString())
  return value
}

/** Cached observations only: opening a card never reads JNI on the main thread. */
internal fun getCurrentMPVColor(type: SubColorType, fallback: Int): Int =
  runCatching {
      live.mehiz.mpvkt.ui.player.MpvProperties.propString.cached(type.property)?.toColorInt()
    }
    .getOrNull() ?: fallback

@Composable
fun SubtitlesColorPicker(
  color: Int,
  onColorChange: (Int) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  Column(modifier) {
    TintedSliderItem(
      stringResource(R.string.player_sheets_sub_color_red),
      color.red,
      color.red.toString(),
      onChange = { onColorChange(color.copyAsArgb(red = it)) },
      max = 255,
      enabled = enabled,
      tint = Color.Red,
    )

    TintedSliderItem(
      stringResource(R.string.player_sheets_sub_color_green),
      color.green,
      color.green.toString(),
      onChange = { onColorChange(color.copyAsArgb(green = it)) },
      max = 255,
      enabled = enabled,
      tint = Color.Green,
    )

    TintedSliderItem(
      stringResource(R.string.player_sheets_sub_color_blue),
      color.blue,
      color.blue.toString(),
      onChange = { onColorChange(color.copyAsArgb(blue = it)) },
      max = 255,
      enabled = enabled,
      tint = Color.Blue,
    )

    TintedSliderItem(
      stringResource(R.string.player_sheets_sub_color_alpha),
      color.alpha,
      color.alpha.toString(),
      onChange = { onColorChange(color.copyAsArgb(alpha = it)) },
      max = 255,
      enabled = enabled,
      tint = Color.White,
    )
  }
}
