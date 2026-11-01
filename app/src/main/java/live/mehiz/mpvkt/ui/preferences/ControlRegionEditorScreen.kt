package live.mehiz.mpvkt.ui.preferences

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
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
    var dragged by remember { mutableStateOf<LandscapeControl?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val reduced by preferences.reduceMotion.collectAsState()
    var preview by remember { mutableStateOf<LandscapeLayout?>(null) }
    val shown = preview ?: layout
    val latestShown by rememberUpdatedState(shown)
    LaunchedEffect(layout, dragged) { if (dragged == null && preview == layout) preview = null }
    val defaultStep = with(LocalDensity.current) { 76.dp.toPx() }
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
        item { LandscapePreview(shown) }
        item {
          Text(
            stringResource(R.string.update_drag_controls),
            Modifier.padding(24.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        items(shown.buttons(region), key = { it.name }) { button ->
          var measuredHeight by remember(button) { mutableFloatStateOf(defaultStep) }
          val step by rememberUpdatedState(measuredHeight)
          val lift by
            animateFloatAsState(
              if (dragged == button) 1.015f else 1f,
              tween(if (reduced) 0 else 140),
              label = "drag_lift",
            )
          val translation by
            animateFloatAsState(
              if (dragged == button) offset else 0f,
              if (dragged == button || reduced) snap() else tween(180),
              label = "drag_settle",
            )
          SettingsCard(
            Modifier.animateItem(
                placementSpec = if (dragged == button) null else tween(if (reduced) 0 else 220)
              )
              .zIndex(if (dragged == button) 1f else 0f)
              .onSizeChanged { measuredHeight = it.height.toFloat().coerceAtLeast(1f) }
              .padding(horizontal = 20.dp, vertical = 4.dp)
              .graphicsLayer {
                translationY = translation
                scaleX = lift
                scaleY = lift
              }
          ) {
            SettingsRow(
              { Text(stringResource(button.titleRes)) },
              icon = { Icon(button.icon(), null) },
              trailing = {
                Row {
                  val index = shown.buttons(region).indexOf(button)
                  IconButton(
                    { preferences.landscapeLayout.set(latestShown.move(region, index, index - 1)) },
                    enabled = dragged == null && preview == null && index > 0,
                  ) {
                    Icon(Icons.ArrowDropUp, stringResource(R.string.mpv_move_earlier))
                  }
                  IconButton(
                    { preferences.landscapeLayout.set(latestShown.move(region, index, index + 1)) },
                    enabled =
                      dragged == null && preview == null && index < shown.buttons(region).lastIndex,
                  ) {
                    Icon(Icons.ArrowDropDown, stringResource(R.string.mpv_move_later))
                  }
                  IconButton(
                    { preferences.landscapeLayout.set(latestShown.assign(button, null)) },
                    enabled = dragged == null && preview == null,
                  ) {
                    Icon(Icons.Close, stringResource(R.string.update_remove_control))
                  }
                  Box(
                    Modifier.size(40.dp).pointerInput(button, region) {
                      detectDragGesturesAfterLongPress(
                        onDragStart = {
                          preview = latestShown
                          dragged = button
                          offset = 0f
                        },
                        onDragEnd = {
                          preview?.let(preferences.landscapeLayout::set)
                          dragged = null
                          offset = 0f
                        },
                        onDragCancel = {
                          preview = null
                          dragged = null
                          offset = 0f
                        },
                        onDrag = { change, delta ->
                          change.consume()
                          offset += delta.y
                          val working = preview ?: latestShown
                          val index = working.buttons(region).indexOf(button)
                          val direction =
                            when {
                              offset > step / 2 -> 1
                              offset < -step / 2 -> -1
                              else -> 0
                            }
                          val target = index + direction
                          if (direction != 0 && target in working.buttons(region).indices) {
                            preview = working.move(region, index, target)
                            offset -= direction * step
                          } else if (direction != 0) offset = offset.coerceIn(-step / 2, step / 2)
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
          LandscapeControl.entries.filter { it !in shown.buttons(region) },
          key = { "available:" + it.name },
        ) { button ->
          SettingsGroup {
            SettingsRow(
              { Text(stringResource(button.titleRes)) },
              icon = { Icon(button.icon(), null) },
              enabled = dragged == null && preview == null && shown.buttons(region).size < 6,
              trailing = { Icon(Icons.Add, null) },
            ) {
              preferences.landscapeLayout.set(latestShown.assign(button, region))
            }
          }
        }
      }
    }
  }
}
