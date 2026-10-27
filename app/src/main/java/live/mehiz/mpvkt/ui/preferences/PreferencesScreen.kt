// Modified for Luma Player: interface, appearance, or build configuration.
package live.mehiz.mpvkt.ui.preferences

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import live.mehiz.mpvkt.ui.icons.Icons
import live.mehiz.mpvkt.ui.icons.ArrowBack
import live.mehiz.mpvkt.ui.icons.Audiotrack
import live.mehiz.mpvkt.ui.icons.Code
import live.mehiz.mpvkt.ui.icons.Gesture
import live.mehiz.mpvkt.ui.icons.Info
import live.mehiz.mpvkt.ui.icons.Memory
import live.mehiz.mpvkt.ui.icons.Palette
import live.mehiz.mpvkt.ui.icons.PlayCircle
import live.mehiz.mpvkt.ui.icons.Subtitles
import live.mehiz.mpvkt.ui.icons.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kotlinx.serialization.Serializable
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.presentation.Screen
import live.mehiz.mpvkt.ui.custombuttons.CustomButtonsScreen
import live.mehiz.mpvkt.ui.utils.LocalNavigateBack
import live.mehiz.mpvkt.ui.utils.LocalBackStack
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.preference

@Serializable
object PreferencesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current
    val cardColor = MaterialTheme.colorScheme.surfaceContainer
    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text(text = stringResource(R.string.pref_preferences)) },
          navigationIcon = {
            IconButton(onClick = LocalNavigateBack.current) {
              Icon(Icons.AutoMirrored.ArrowBack, contentDescription = null)
            }
          },
        )
      },
    ) { padding ->
      ProvidePreferenceLocals {
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(padding),
          contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          preference(
            modifier = Modifier.clip(RoundedCornerShape(24.dp))
              .background(cardColor),
            key = "appearance",
            title = { Text(text = stringResource(id = R.string.pref_appearance_title)) },
            summary = { Text(text = stringResource(id = R.string.pref_appearance_summary)) },
            icon = { Icon(Icons.Palette, null) },
            onClick = { backstack.add(AppearancePreferencesScreen) },
          )
          preference(
            modifier = Modifier.clip(RoundedCornerShape(24.dp))
              .background(cardColor),
            key = "player",
            title = { Text(text = stringResource(id = R.string.pref_player)) },
            summary = { Text(text = stringResource(id = R.string.pref_player_summary)) },
            icon = { Icon(Icons.PlayCircle, null) },
            onClick = { backstack.add(PlayerPreferencesScreen) },
          )
          preference(
            modifier = Modifier.clip(RoundedCornerShape(24.dp))
              .background(cardColor),
            key = "gesture",
            title = { Text(text = stringResource(id = R.string.pref_gesture)) },
            summary = { Text(text = stringResource(id = R.string.pref_gesture_summary)) },
            icon = { Icon(Icons.Gesture, null) },
            onClick = { backstack.add(GesturePreferencesScreen) },
          )
          preference(
            modifier = Modifier.clip(RoundedCornerShape(24.dp))
              .background(cardColor),
            key = "decoder",
            title = { Text(text = stringResource(id = R.string.pref_decoder)) },
            summary = { Text(text = stringResource(id = R.string.pref_decoder_summary)) },
            icon = { Icon(Icons.Memory, null) },
            onClick = { backstack.add(DecoderPreferencesScreen) },
          )
          preference(
            modifier = Modifier.clip(RoundedCornerShape(24.dp))
              .background(cardColor),
            key = "subtitles",
            title = { Text(text = stringResource(id = R.string.pref_subtitles)) },
            summary = { Text(text = stringResource(id = R.string.pref_subtitles_summary)) },
            icon = { Icon(Icons.Subtitles, null) },
            onClick = { backstack.add(SubtitlesPreferencesScreen) },
          )
          preference(
            modifier = Modifier.clip(RoundedCornerShape(24.dp))
              .background(cardColor),
            key = "audio",
            title = { Text(text = stringResource(id = R.string.pref_audio)) },
            summary = { Text(text = stringResource(id = R.string.pref_audio_summary)) },
            icon = { Icon(Icons.Audiotrack, null) },
            onClick = { backstack.add(AudioPreferencesScreen) },
          )
          preference(
            modifier = Modifier.clip(RoundedCornerShape(24.dp))
              .background(cardColor),
            key = "customButtons",
            title = { Text(text = stringResource(id = R.string.pref_custom_buttons_title)) },
            summary = { Text(text = stringResource(id = R.string.pref_custom_buttons_summary)) },
            icon = { Icon(Icons.Terminal, null) },
            onClick = { backstack.add(CustomButtonsScreen) },
          )
          preference(
            modifier = Modifier.clip(RoundedCornerShape(24.dp))
              .background(cardColor),
            key = "advanced",
            title = { Text(text = stringResource(R.string.pref_advanced)) },
            summary = { Text(text = stringResource(id = R.string.pref_advanced_summary)) },
            icon = { Icon(Icons.Code, null) },
            onClick = { backstack.add(AdvancedPreferencesScreen) }
          )
          preference(
            modifier = Modifier.clip(RoundedCornerShape(24.dp))
              .background(cardColor),
            key = "about",
            title = { Text(text = stringResource(id = R.string.pref_about_title)) },
            summary = { Text(text = stringResource(id = R.string.pref_about_summary)) },
            icon = { Icon(Icons.Info, null) },
            onClick = { backstack.add(AboutScreen) },
          )
        }
      }
    }
  }
}
