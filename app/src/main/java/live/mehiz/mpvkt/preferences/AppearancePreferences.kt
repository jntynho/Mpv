// Modified for Luma Player: interface, appearance, or build configuration.
package live.mehiz.mpvkt.preferences

import live.mehiz.mpvkt.preferences.preference.PreferenceStore
import live.mehiz.mpvkt.preferences.preference.getEnum
import live.mehiz.mpvkt.ui.icons.IconStyle
import live.mehiz.mpvkt.ui.theme.DarkMode

class AppearancePreferences(preferenceStore: PreferenceStore) {
  val libraryFields = preferenceStore.getStringSet("library_fields", setOf("Path", "Thumbnails", "Extension", "Duration"))
  val libraryGrid = preferenceStore.getBoolean("library_grid", false)
  val libraryGridColumns = preferenceStore.getInt("library_grid_columns", 0)
  val colourAccent =
    preferenceStore.getEnum("colour_accent", live.mehiz.mpvkt.ui.theme.ColourAccent.Original)
  val showThumbnails = preferenceStore.getBoolean("show_video_thumbnails", true)
  val thumbnailGenerator =
    preferenceStore.getEnum("thumbnail_generator", live.mehiz.mpvkt.ui.home.ThumbnailGenerator.Auto)
  val thumbnailQuality =
    preferenceStore.getEnum("thumbnail_quality", live.mehiz.mpvkt.ui.home.ThumbnailQuality.Standard)
  val thumbnailPosition = preferenceStore.getInt("thumbnail_position", 33)
  val iconStyle = preferenceStore.getEnum("icon_style", IconStyle.Outline)
  val darkMode = preferenceStore.getEnum("dark_mode", DarkMode.Dark)
  val materialYou = preferenceStore.getBoolean("material_you", false)
  val oledBlack = preferenceStore.getBoolean("luma_oled_black", false)
}
