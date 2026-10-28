package live.mehiz.mpvkt.ui.preferences

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.preferences.AppearancePreferences
import live.mehiz.mpvkt.preferences.preference.collectAsState
import live.mehiz.mpvkt.presentation.components.AppDialog
import live.mehiz.mpvkt.presentation.components.DialogButton
import live.mehiz.mpvkt.ui.home.*
import live.mehiz.mpvkt.ui.theme.ColourAccent

@Composable
internal fun ColourAccentPreference(preferences: AppearancePreferences) {
  val accent by preferences.colourAccent.collectAsState()
  val dynamic by preferences.materialYou.collectAsState()
  var open by rememberSaveable { mutableStateOf(false) }
  SettingsRow(
    { Text(stringResource(R.string.mpv_colour_accent)) },
    { Text(if (dynamic) stringResource(R.string.mpv_wallpaper_colours) else accent.label) },
  ) {
    open = true
  }
  if (open)
    AppDialog(
      onDismissRequest = { open = false },
      title = { Text(stringResource(R.string.mpv_colour_accent)) },
      confirmButton = {
        DialogButton({ open = false }) { Text(stringResource(R.string.subtitle_done)) }
      },
      text = {
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          ColourAccent.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              row.forEach { choice ->
                val selected = choice == accent && !dynamic
                Surface(
                  Modifier.weight(1f).selectable(selected, role = Role.RadioButton) {
                    preferences.colourAccent.set(choice)
                    preferences.materialYou.set(false)
                    open = false
                  },
                  shape = RoundedCornerShape(16.dp),
                  color =
                    if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                  border =
                    if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                ) {
                  Column(
                    Modifier.padding(8.dp).heightIn(min = 64.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    Surface(
                      Modifier.size(24.dp),
                      shape = CircleShape,
                      color = choice.preview(true),
                    ) {}
                    Text(
                      choice.label,
                      style = MaterialTheme.typography.labelSmall,
                      maxLines = 2,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                }
              }
              repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
          }
        }
      },
    )
}

@Composable
internal fun ThumbnailPreferences(preferences: AppearancePreferences) {
  val enabled by preferences.showThumbnails.collectAsState()
  val generator by preferences.thumbnailGenerator.collectAsState()
  val quality by preferences.thumbnailQuality.collectAsState()
  val position by preferences.thumbnailPosition.collectAsState()
  val context = androidx.compose.ui.platform.LocalContext.current
  var cleared by remember { mutableStateOf(false) }
  SwitchPreference(
    enabled,
    preferences.showThumbnails::set,
    { Text(stringResource(R.string.mpv_show_thumbnails)) },
    { Text(stringResource(R.string.mpv_show_thumbnails_summary)) },
  )
  ExpandableSettingsContent(enabled) {
    ListPreference(
      generator,
      {
        preferences.thumbnailGenerator.set(it)
        cleared = false
      },
      ThumbnailGenerator.entries,
      title = { Text(stringResource(R.string.mpv_thumbnail_source)) },
      summary = { Text(stringResource(generator.titleRes)) },
      valueToText = { AnnotatedString(context.getString(it.titleRes)) },
    )
    ListPreference(
      quality,
      {
        preferences.thumbnailQuality.set(it)
        cleared = false
      },
      ThumbnailQuality.entries,
      title = { Text(stringResource(R.string.mpv_thumbnail_quality)) },
      summary = { Text("${quality.width} × ${quality.height}") },
      valueToText = {
        AnnotatedString(
          "${context.getString(if (it == ThumbnailQuality.High) R.string.mpv_quality_high else R.string.mpv_quality_standard)} · ${it.width} × ${it.height}"
        )
      },
    )
    ExpandableSettingsContent(generator == ThumbnailGenerator.Frame) {
      SliderPreference(
        onValueChange = { preferences.thumbnailPosition.set(it.toInt().coerceIn(0, 100)) },
        title = { Text(stringResource(R.string.mpv_thumbnail_frame)) },
        summary = { Text("${position.coerceIn(0,100)}%") },
        valueRange = 0f..100f,
        sliderValue = position.coerceIn(0, 100).toFloat(),
        onSliderValueChange = {},
      )
    }
  }
  SettingsRow(
    { Text(stringResource(R.string.mpv_clear_thumbnail_cache)) },
    {
      Text(
        stringResource(
          if (cleared) R.string.mpv_cache_cleared else R.string.mpv_clear_thumbnail_cache_summary
        )
      )
    },
  ) {
    Thumbnails.clear()
    cleared = true
  }
}

private val ThumbnailGenerator.titleRes
  get() =
    when (this) {
      ThumbnailGenerator.Auto -> R.string.mpv_thumbnail_auto
      ThumbnailGenerator.MediaStore -> R.string.mpv_thumbnail_mediastore
      ThumbnailGenerator.Frame -> R.string.mpv_thumbnail_extract
    }
