package live.mehiz.mpvkt.ui.preferences

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.preferences.PlayerPreferences
import live.mehiz.mpvkt.preferences.preference.collectAsState
import live.mehiz.mpvkt.presentation.Screen
import live.mehiz.mpvkt.ui.icons.*
import live.mehiz.mpvkt.ui.motion.*
import live.mehiz.mpvkt.ui.player.controls.components.*
import live.mehiz.mpvkt.ui.utils.LocalNavigateBack
import org.koin.compose.koinInject

@Serializable
object SeekbarStylesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val preferences = koinInject<PlayerPreferences>()
    val style by preferences.seekbarStyle.collectAsState()
    var position by remember { mutableFloatStateOf(42f) }
    var paused by remember { mutableStateOf(false) }
    val reduced = LocalReduceMotion.current
    ElasticSettingsScaffold(
      topBar = {
        ElasticSettingsTopAppBar(
          { Text(stringResource(R.string.mpv_seekbar_style)) },
          navigationIcon = {
            IconButton(LocalNavigateBack.current) { Icon(Icons.AutoMirrored.ArrowBack, null) }
          },
        )
      }
    ) { padding ->
      LazyColumn(
        Modifier.fillMaxSize().padding(padding).selectableGroup(),
        contentPadding = PaddingValues(bottom = 24.dp),
      ) {
        item {
          SettingsGroup {
            Row(
              Modifier.fillMaxWidth().padding(16.dp),
              verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
              Text(stringResource(R.string.update_live_preview), Modifier.weight(1f))
              IconButton({ paused = !paused }) {
                Icon(if (paused) Icons.Play else Icons.Pause, null)
              }
            }
            StyledSeeker(
              position,
              100f,
              72f,
              emptyList(),
              { position = it },
              {},
              Modifier.fillMaxWidth().padding(horizontal = 20.dp),
              style,
              paused,
              reduced,
            )
          }
        }
        SeekbarStyle.entries.forEach { choice ->
          item(key = choice.name) {
            SettingsCard(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
              Column(
                Modifier.selectable(
                    choice == style,
                    role = Role.RadioButton,
                    onClick = { preferences.seekbarStyle.set(choice) },
                  )
                  .padding(16.dp)
              ) {
                Row(
                  Modifier.fillMaxWidth(),
                  verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                  Text(stringResource(choice.titleRes), Modifier.weight(1f))
                  RadioButton(choice == style, onClick = null)
                }
                StyledSeeker(
                  42f,
                  100f,
                  70f,
                  emptyList(),
                  {},
                  {},
                  Modifier.fillMaxWidth(),
                  choice,
                  paused = true,
                  reduceMotion = true,
                  interactive = false,
                )
              }
            }
          }
        }
      }
    }
  }
}
