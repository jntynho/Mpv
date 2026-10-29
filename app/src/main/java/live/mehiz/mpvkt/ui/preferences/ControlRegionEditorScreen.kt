package live.mehiz.mpvkt.ui.preferences

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.preferences.PlayerPreferences
import live.mehiz.mpvkt.preferences.preference.collectAsState
import live.mehiz.mpvkt.presentation.Screen
import live.mehiz.mpvkt.ui.icons.*
import live.mehiz.mpvkt.ui.motion.*
import live.mehiz.mpvkt.ui.player.controls.*
import live.mehiz.mpvkt.ui.utils.LocalNavigateBack
import org.koin.compose.koinInject

@Serializable
data class ControlRegionEditorScreen(val region: ControlRegion) : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val preferences = koinInject<PlayerPreferences>()
    val layout by preferences.landscapeLayout.collectAsState()
    val latest by rememberUpdatedState(layout)
    var dragged by remember { mutableStateOf<LandscapeControl?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val step = with(LocalDensity.current) { 76.dp.toPx() }
    ElasticSettingsScaffold(
      topBar = {
        ElasticSettingsTopAppBar(
          { Text(stringResource(region.titleRes)) },
          navigationIcon = {
            IconButton(LocalNavigateBack.current) { Icon(Icons.AutoMirrored.ArrowBack, null) }
          },
        )
      }
    ) { padding ->
      LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(bottom = 32.dp),
      ) {
        item { LandscapePreview(layout) }
        item {
          Text(
            stringResource(R.string.update_drag_controls),
            Modifier.padding(24.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        items(layout.buttons(region), key = { it.name }) { button ->
          SettingsCard(
            Modifier.padding(horizontal = 20.dp, vertical = 4.dp).graphicsLayer {
              translationY = if (dragged == button) offset else 0f
            }
          ) {
            SettingsRow(
              { Text(stringResource(button.titleRes)) },
              icon = { Icon(button.icon(), null) },
              trailing = {
                Row {
                  val index = layout.buttons(region).indexOf(button)
                  IconButton(
                    { preferences.landscapeLayout.set(latest.move(region, index, index - 1)) },
                    enabled = index > 0,
                  ) {
                    Icon(Icons.ArrowDropUp, stringResource(R.string.mpv_move_earlier))
                  }
                  IconButton(
                    { preferences.landscapeLayout.set(latest.move(region, index, index + 1)) },
                    enabled = index < layout.buttons(region).lastIndex,
                  ) {
                    Icon(Icons.ArrowDropDown, stringResource(R.string.mpv_move_later))
                  }
                  IconButton({ preferences.landscapeLayout.set(latest.assign(button, null)) }) {
                    Icon(Icons.Close, stringResource(R.string.update_remove_control))
                  }
                  Box(
                    Modifier.size(40.dp).pointerInput(button, region) {
                      detectDragGesturesAfterLongPress(
                        onDragStart = {
                          dragged = button
                          offset = 0f
                        },
                        onDragEnd = {
                          dragged = null
                          offset = 0f
                        },
                        onDragCancel = {
                          dragged = null
                          offset = 0f
                        },
                        onDrag = { change, delta ->
                          change.consume()
                          offset += delta.y
                          val index = latest.buttons(region).indexOf(button)
                          val direction =
                            when {
                              offset > step / 2 -> 1
                              offset < -step / 2 -> -1
                              else -> 0
                            }
                          val target = index + direction
                          if (direction != 0 && target in latest.buttons(region).indices) {
                            preferences.landscapeLayout.set(latest.move(region, index, target))
                            offset -= direction * step
                          }
                        },
                      )
                    },
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                  ) {
                    Icon(Icons.List, stringResource(R.string.update_drag_controls))
                  }
                }
              },
            ) {}
          }
        }
        item { PreferenceCategory({ Text(stringResource(R.string.mpv_available_controls)) }) }
        items(
          LandscapeControl.entries.filter { it !in layout.buttons(region) },
          key = { "available:" + it.name },
        ) { button ->
          SettingsGroup {
            SettingsRow(
              { Text(stringResource(button.titleRes)) },
              icon = { Icon(button.icon(), null) },
              enabled = layout.buttons(region).size < 6,
              trailing = { Icon(Icons.Add, null) },
            ) {
              preferences.landscapeLayout.set(latest.assign(button, region))
            }
          }
        }
      }
    }
  }
}
