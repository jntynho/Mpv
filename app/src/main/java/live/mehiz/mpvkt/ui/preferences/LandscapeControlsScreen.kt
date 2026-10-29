package live.mehiz.mpvkt.ui.preferences

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.preferences.PlayerPreferences
import live.mehiz.mpvkt.preferences.preference.collectAsState
import live.mehiz.mpvkt.presentation.Screen
import live.mehiz.mpvkt.ui.icons.*
import live.mehiz.mpvkt.ui.motion.ElasticSettingsScaffold
import live.mehiz.mpvkt.ui.motion.ElasticSettingsTopAppBar
import live.mehiz.mpvkt.ui.player.controls.*
import live.mehiz.mpvkt.ui.utils.LocalNavigateBack
import org.koin.compose.koinInject

@Serializable
object LandscapeControlsScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val preferences = koinInject<PlayerPreferences>()
    val layout by preferences.landscapeLayout.collectAsState()
    val backstack = live.mehiz.mpvkt.ui.utils.LocalBackStack.current
    var resetting by remember { mutableStateOf(false) }
    if (resetting)
      live.mehiz.mpvkt.presentation.components.ConfirmDialog(
        stringResource(R.string.mpv_reset_controls),
        stringResource(R.string.update_reset_controls_confirm),
        onConfirm = {
          preferences.landscapeLayout.set(LandscapeLayout())
          resetting = false
        },
        onCancel = { resetting = false },
      )
    ElasticSettingsScaffold(
      topBar = {
        ElasticSettingsTopAppBar(
          { Text(stringResource(R.string.mpv_landscape_controls)) },
          navigationIcon = {
            IconButton(LocalNavigateBack.current) {
              Icon(Icons.AutoMirrored.ArrowBack, stringResource(R.string.mpv_back))
            }
          },
        )
      }
    ) { padding ->
      LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(bottom = 24.dp),
      ) {
        item { LandscapePreview(layout) }
        item {
          SettingsGroup {
            ControlRegion.entries.forEach { region ->
              SettingsRow(
                { Text(stringResource(region.titleRes)) },
                {
                  Text(
                    layout.buttons(region).joinToString(" · ") { context.getString(it.titleRes) }
                  )
                },
                { Icon(Icons.Monitor, null) },
              ) {
                backstack.add(ControlRegionEditorScreen(region))
              }
            }
            SettingsRow(
              { Text(stringResource(R.string.mpv_seekbar_style)) },
              icon = { Icon(Icons.DoubleArrow, null) },
            ) {
              backstack.add(SeekbarStylesScreen)
            }
            SettingsRow(
              { Text(stringResource(R.string.mpv_reset_controls)) },
              icon = { Icon(Icons.Refresh, null) },
            ) {
              resetting = true
            }
          }
        }
      }
    }
  }
}

@Composable
internal fun LandscapePreview(layout: LandscapeLayout) {
  CompositionLocalProvider(
    androidx.compose.ui.platform.LocalLayoutDirection provides
      androidx.compose.ui.unit.LayoutDirection.Ltr
  ) {
    SettingsCard(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
      Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).padding(14.dp)) {
        Text("MPV", Modifier.align(Alignment.TopStart), style = MaterialTheme.typography.labelLarge)
        Icon(
          Icons.Play,
          null,
          Modifier.align(Alignment.Center).size(40.dp),
          tint = MaterialTheme.colorScheme.primary,
        )
        ControlRegion.entries.forEach { region ->
          val alignment =
            when (region) {
              ControlRegion.TopRight -> Alignment.TopEnd
              ControlRegion.BottomRight -> Alignment.BottomEnd
              ControlRegion.BottomLeft -> Alignment.BottomStart
            }
          Row(Modifier.align(alignment), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            layout.buttons(region).forEach {
              Icon(
                it.icon(),
                null,
                Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }
}
