package live.mehiz.mpvkt.preferences

import live.mehiz.mpvkt.ui.icons.Icons
import live.mehiz.mpvkt.ui.icons.FormatAlignLeft
import live.mehiz.mpvkt.ui.icons.FormatAlignRight
import live.mehiz.mpvkt.ui.icons.FormatAlignCenter
import live.mehiz.mpvkt.ui.icons.FormatAlignJustify
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import live.mehiz.mpvkt.preferences.preference.PreferenceStore
import live.mehiz.mpvkt.preferences.preference.getEnum
import live.mehiz.mpvkt.ui.player.controls.components.panels.SubtitlesBorderStyle

class SubtitlesPreferences(preferenceStore: PreferenceStore) {
  val autoLoadExternal = preferenceStore.getBoolean("sub_autoload", true)
  val preferredLanguages = preferenceStore.getString("sub_preferred_languages")

  val fontsFolder = preferenceStore.getString("sub_fonts_folder")
  val font = preferenceStore.getString("sub_font", "Sans Serif")
  val fontSize = preferenceStore.getInt("sub_font_size", 55)
  val subScale = preferenceStore.getFloat("sub_scale", 1f)
  val borderSize = preferenceStore.getInt("sub_border_size", 3)
  val bold = preferenceStore.getBoolean("sub_bold")
  val italic = preferenceStore.getBoolean("sub_italic")

  val textColor = preferenceStore.getInt("sub_color_text", Color.White.toArgb())

  val borderColor = preferenceStore.getInt("sub_color_border", Color.Black.toArgb())
  val borderStyle = preferenceStore.getEnum("sub_border_style", SubtitlesBorderStyle.OutlineAndShadow)
  val shadowOffset = preferenceStore.getInt("sub_shadow_offset")
  val backgroundColor = preferenceStore.getInt("sub_color_bg", Color.Transparent.toArgb())

  val justification = preferenceStore.getEnum("sub_justify", SubtitleJustification.Auto)
  val subPos = preferenceStore.getInt("sub_pos", 100)

  val overrideAssSubs = preferenceStore.getBoolean("sub_override_ass")

  val scaleByWindow = preferenceStore.getBoolean("sub_scale_by_window", true)
  val secondaryPinchZoom = preferenceStore.getBoolean("secondary_sub_pinch_zoom", false)
  val secondaryScale = preferenceStore.getFloat("secondary_sub_scale", 1f)
  val secondaryScaleSupported = preferenceStore.getBoolean("secondary_sub_scale_supported", false)
  val searchMode = preferenceStore.getString("subtitle_search_mode", "hybrid")
  val hubSources = preferenceStore.getStringSet("subtitle_hub_sources", setOf("all"))
  val wyzieSources = preferenceStore.getStringSet("subtitle_wyzie_sources", setOf("all"))
  val searchLanguages = preferenceStore.getStringSet("subtitle_search_languages", setOf("en"))
  val searchFormats = preferenceStore.getStringSet("subtitle_search_formats", setOf("srt", "ass"))
  val searchEncodings = preferenceStore.getStringSet("subtitle_search_encodings", setOf("utf-8"))
  val hearingImpaired = preferenceStore.getBoolean("subtitle_search_hi", false)
  val aiResults = preferenceStore.getString("subtitle_search_ai", "all")

  val defaultSubDelay = preferenceStore.getInt("sub_default_delay")
  val defaultSubSpeed = preferenceStore.getFloat("sub_default_speed", 1f)
  val defaultSecondarySubDelay = preferenceStore.getInt("sub_default_secondary_delay")
}

enum class SubtitleJustification(val value: String) {
  Left("left"), Center("center"), Right("right"), Auto("auto");

  val icon: ImageVector
    @androidx.compose.runtime.Composable get() = when (this) {
      Left -> Icons.AutoMirrored.FormatAlignLeft
      Center -> Icons.FormatAlignCenter
      Right -> Icons.AutoMirrored.FormatAlignRight
      Auto -> Icons.FormatAlignJustify
    }
}
