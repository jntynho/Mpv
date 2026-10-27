// Modified for Luma Player: interface, appearance, or build configuration.
package live.mehiz.mpvkt.ui.preferences

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import live.mehiz.mpvkt.ui.icons.Icons
import live.mehiz.mpvkt.ui.icons.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import live.mehiz.mpvkt.ui.motion.ElasticSettingsScaffold as Scaffold
import androidx.compose.material3.Text
import live.mehiz.mpvkt.ui.motion.ElasticSettingsTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import live.mehiz.mpvkt.ui.icons.IconStyle
import live.mehiz.mpvkt.ui.icons.Palette
import me.zhanghai.compose.preference.ListPreference
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.Serializable
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.preferences.AppearancePreferences
import live.mehiz.mpvkt.preferences.preference.collectAsState
import live.mehiz.mpvkt.presentation.Screen
import live.mehiz.mpvkt.presentation.preferences.MultiChoiceSegmentedButton
import live.mehiz.mpvkt.ui.theme.DarkMode
import live.mehiz.mpvkt.ui.utils.LocalNavigateBack
import me.zhanghai.compose.preference.PreferenceCategory
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SwitchPreference
import org.koin.compose.koinInject

@Serializable
object AppearancePreferencesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val preferences = koinInject<AppearancePreferences>()
    val context = LocalContext.current
    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text(text = stringResource(R.string.pref_appearance_title)) },
          navigationIcon = {
            IconButton(onClick = LocalNavigateBack.current) {
              Icon(Icons.AutoMirrored.ArrowBack, null)
            }
          },
        )
      },
    ) { padding ->
      ProvidePreferenceLocals {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(padding),
        ) {
          PreferenceCategory(
            title = { Text(text = stringResource(id = R.string.pref_appearance_category_theme)) },
          )
          val iconStyle by preferences.iconStyle.collectAsState()
          ListPreference(
            value = iconStyle,
            onValueChange = preferences.iconStyle::set,
            values = IconStyle.entries,
            valueToText = { AnnotatedString(context.getString(it.titleRes)) },
            title = { Text(stringResource(R.string.mpv_icons_style_title)) },
            summary = { Text(stringResource(iconStyle.titleRes)) },
            icon = { Icon(Icons.Palette, null) },
          )
          val oledBlack by preferences.oledBlack.collectAsState()
          SwitchPreference(
            value = oledBlack,
            onValueChange = { preferences.oledBlack.set(it) },
            title = { Text(stringResource(R.string.luma_oled)) },
            summary = { Text(stringResource(R.string.luma_oled_summary)) },
          )
          val darkMode by preferences.darkMode.collectAsState()
          MultiChoiceSegmentedButton(
            choices = DarkMode.entries.map { context.getString(it.titleRes) }.toImmutableList(),
            selectedIndices = persistentListOf(DarkMode.entries.indexOf(darkMode)),
            onClick = { preferences.darkMode.set(DarkMode.entries[it]) },
          )
          val materialYou by preferences.materialYou.collectAsState()
          val isMaterialYouAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
          SwitchPreference(
            value = materialYou,
            onValueChange = { preferences.materialYou.set(it) },
            title = { Text(text = stringResource(id = R.string.pref_appearance_material_you_title)) },
            summary = {
              Text(
                text = stringResource(
                  if (isMaterialYouAvailable) {
                    R.string.pref_appearance_material_you_summary
                  } else {
                    R.string.pref_appearance_material_you_summary_disabled
                  },
                ),
              )
            },
            enabled = isMaterialYouAvailable,
          )
        }
      }
    }
  }
}
