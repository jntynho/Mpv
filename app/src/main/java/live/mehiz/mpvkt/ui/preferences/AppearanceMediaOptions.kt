package live.mehiz.mpvkt.ui.preferences

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.preferences.AppearancePreferences
import live.mehiz.mpvkt.preferences.preference.collectAsState
import live.mehiz.mpvkt.presentation.components.horizontalEdgeMask
import live.mehiz.mpvkt.ui.home.*
import live.mehiz.mpvkt.ui.icons.*
import live.mehiz.mpvkt.ui.icons.Icons
import live.mehiz.mpvkt.ui.icons.Palette
import live.mehiz.mpvkt.ui.motion.LocalReduceMotion
import live.mehiz.mpvkt.ui.theme.ColourAccent
import live.mehiz.mpvkt.ui.theme.DarkMode
import live.mehiz.mpvkt.ui.theme.previewScheme

@Composable
internal fun ColourAccentPreference(preferences: AppearancePreferences) {
  val accent by preferences.colourAccent.collectAsState()
  val dynamic by preferences.materialYou.collectAsState()
  val darkMode by preferences.darkMode.collectAsState()
  val dark = darkMode == DarkMode.Dark || (darkMode == DarkMode.System && isSystemInDarkTheme())
  val rtl =
    androidx.compose.ui.platform.LocalLayoutDirection.current ==
      androidx.compose.ui.unit.LayoutDirection.Rtl
  val listState = rememberLazyListState(initialFirstVisibleItemIndex = accent.ordinal)
  Column {
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Icon(Icons.Palette, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.mpv_colour_accent), style = MaterialTheme.typography.bodyLarge)
        Text(
          if (dynamic) stringResource(R.string.mpv_wallpaper_colours) else accent.label,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    LazyRow(
      modifier =
        Modifier.fillMaxWidth()
          .horizontalEdgeMask(
            start = { if (rtl) listState.canScrollForward else listState.canScrollBackward },
            end = { if (rtl) listState.canScrollBackward else listState.canScrollForward },
          )
          .selectableGroup(),
      state = listState,
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      items(ColourAccent.entries, key = { it.name }) { choice ->
        ColourAccentSwatch(choice, dark, selected = choice == accent && !dynamic) {
          preferences.colourAccent.set(choice)
          preferences.materialYou.set(false)
        }
      }
    }
  }
}

@Composable
private fun ColourAccentSwatch(
  accent: ColourAccent,
  dark: Boolean,
  selected: Boolean,
  onClick: () -> Unit,
) {
  val scheme = remember(accent, dark) { accent.previewScheme(dark) }
  val ringAlpha by
    animateFloatAsState(
      if (selected) 1f else 0f,
      tween(if (LocalReduceMotion.current) 0 else 180),
      label = "accent_selection",
    )
  Box(
    Modifier.size(56.dp)
      .clip(CircleShape)
      .selectable(selected, role = Role.RadioButton, onClick = onClick)
      .semantics { contentDescription = accent.label }
  ) {
    Canvas(Modifier.fillMaxSize()) {
      val stroke = 2.dp.toPx()
      drawCircle(
        scheme.primary.copy(alpha = ringAlpha),
        radius = (size.minDimension - stroke) / 2f,
        style = Stroke(stroke),
      )
    }
    Canvas(Modifier.fillMaxSize().padding(5.dp).clip(CircleShape)) {
      drawRect(scheme.surface)
      drawRect(
        scheme.primary,
        topLeft = Offset(0f, size.height / 2f),
        size = Size(size.width / 2f, size.height / 2f),
      )
      drawRect(
        scheme.tertiary,
        topLeft = Offset(size.width / 2f, size.height / 2f),
        size = Size(size.width / 2f, size.height / 2f),
      )
    }
  }
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
    icon = { Icon(optionIcon(R.string.mpv_show_thumbnails), null) },
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
      icon = { Icon(optionIcon(R.string.mpv_thumbnail_source), null) },
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
      icon = { Icon(optionIcon(R.string.mpv_thumbnail_quality), null) },
    )
    ExpandableSettingsContent(generator == ThumbnailGenerator.Frame) {
      SliderPreference(
        onValueChange = { preferences.thumbnailPosition.set(it.toInt().coerceIn(0, 100)) },
        title = { Text(stringResource(R.string.mpv_thumbnail_frame)) },
        summary = { Text("${position.coerceIn(0,100)}%") },
        valueRange = 0f..100f,
        sliderValue = position.coerceIn(0, 100).toFloat(),
        onSliderValueChange = {},
        icon = { Icon(optionIcon(R.string.mpv_thumbnail_frame), null) },
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
    icon = { Icon(optionIcon(R.string.mpv_clear_thumbnail_cache), null) },
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
