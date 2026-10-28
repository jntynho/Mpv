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
import live.mehiz.mpvkt.presentation.components.AppDialog
import live.mehiz.mpvkt.presentation.components.DialogButton
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
    val preferences = koinInject<PlayerPreferences>()
    val layout by preferences.landscapeLayout.collectAsState()
    var editing by remember { mutableStateOf<LandscapeControl?>(null) }
    editing?.let { button ->
      val current = ControlRegion.entries.firstOrNull { button in layout.buttons(it) }
      AppDialog(
        onDismissRequest = { editing = null },
        title = { Text(stringResource(button.titleRes)) },
        icon = { Icon(button.icon(), null) },
        confirmButton = {
          DialogButton({ editing = null }) { Text(stringResource(R.string.subtitle_done)) }
        },
        text = {
          Column {
            (listOf<ControlRegion?>(null) + ControlRegion.entries).forEach { region ->
              SettingsRow(
                { Text(stringResource(region?.titleRes ?: R.string.mpv_control_hidden)) },
                enabled = region == null || region == current || layout.buttons(region).size < 6,
              ) {
                preferences.landscapeLayout.set(layout.assign(button, region))
                editing = null
              }
            }
          }
        },
      )
    }
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
          Text(
            stringResource(R.string.mpv_landscape_controls_summary),
            Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        ControlRegion.entries.forEach { region ->
          item(key = region.name) {
            PreferenceCategory({ Text(stringResource(region.titleRes)) })
            SettingsGroup {
              layout.buttons(region).forEachIndexed { index, button ->
                SettingsRow(
                  { Text(stringResource(button.titleRes)) },
                  icon = { Icon(button.icon(), null) },
                  trailing = {
                    Row {
                      IconButton(
                        { preferences.landscapeLayout.set(layout.reorder(region, index, -1)) },
                        enabled = index > 0,
                      ) {
                        Icon(Icons.ArrowDropUp, stringResource(R.string.mpv_move_earlier))
                      }
                      IconButton(
                        { preferences.landscapeLayout.set(layout.reorder(region, index, 1)) },
                        enabled = index < layout.buttons(region).lastIndex,
                      ) {
                        Icon(Icons.ArrowDropDown, stringResource(R.string.mpv_move_later))
                      }
                    }
                  },
                ) {
                  editing = button
                }
              }
              if (layout.buttons(region).isEmpty())
                SettingsRow(
                  { Text(stringResource(R.string.mpv_controls_empty)) },
                  enabled = false,
                ) {}
            }
          }
        }
        item {
          PreferenceCategory({ Text(stringResource(R.string.mpv_available_controls)) })
          SettingsGroup {
            val used = ControlRegion.entries.flatMap { layout.buttons(it) }.toSet()
            LandscapeControl.entries
              .filter { it !in used }
              .forEach { button ->
                SettingsRow(
                  { Text(stringResource(button.titleRes)) },
                  { Text(stringResource(R.string.mpv_control_hidden)) },
                  { Icon(button.icon(), null) },
                ) {
                  editing = button
                }
              }
            SettingsRow(
              { Text(stringResource(R.string.mpv_reset_controls)) },
              icon = { Icon(Icons.Refresh, null) },
            ) {
              preferences.landscapeLayout.set(LandscapeLayout())
            }
          }
        }
      }
    }
  }
}

@Composable
private fun LandscapePreview(layout: LandscapeLayout) {
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
