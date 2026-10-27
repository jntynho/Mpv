// Modified for Luma Player: interface, appearance, or build configuration.
package live.mehiz.mpvkt.preferences

import live.mehiz.mpvkt.preferences.preference.PreferenceStore
import live.mehiz.mpvkt.preferences.preference.getEnum
import live.mehiz.mpvkt.ui.theme.DarkMode
import live.mehiz.mpvkt.ui.icons.IconStyle

class AppearancePreferences(preferenceStore: PreferenceStore) {
  val iconStyle = preferenceStore.getEnum("icon_style", IconStyle.Outline)
  val darkMode = preferenceStore.getEnum("dark_mode", DarkMode.Dark)
  val materialYou = preferenceStore.getBoolean("material_you", false)
  val oledBlack = preferenceStore.getBoolean("luma_oled_black", false)
}
