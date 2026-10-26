package live.mehiz.mpvkt.preferences

import live.mehiz.mpvkt.preferences.preference.PreferenceStore
import live.mehiz.mpvkt.preferences.preference.getEnum
import live.mehiz.mpvkt.ui.theme.DarkMode

class AppearancePreferences(preferenceStore: PreferenceStore) {
  val darkMode = preferenceStore.getEnum("dark_mode", DarkMode.Dark)
  val materialYou = preferenceStore.getBoolean("material_you", false)
  val oledBlack = preferenceStore.getBoolean("luma_oled_black", false)
  val compactHome = preferenceStore.getBoolean("luma_compact_home", false)
  val showTips = preferenceStore.getBoolean("luma_show_tips", true)
}
