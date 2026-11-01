package live.mehiz.mpvkt.ui.preferences

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
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
    val reduced by preferences.reduceMotion.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var dragTop by remember { mutableFloatStateOf(0f) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf<LandscapeControl?>(null) }
    val settle = remember { Animatable(0f) }
    var settleTop by remember { mutableFloatStateOf(0f) }
    fun translation(button: LandscapeControl): Float {
      val position =
        listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == button.name }?.offset
          ?: return 0f
      return dragTop + dragDistance - position
    }
    var preview by remember { mutableStateOf<LandscapeLayout?>(null) }
    val shown = preview ?: layout
    val latestShown by rememberUpdatedState(shown)
    fun finishDrag(cancelled: Boolean) {
      val button = dragged ?: return
      settleTop = dragTop + dragDistance
      if (!cancelled) preview?.let(preferences.landscapeLayout::set) else preview = null
      settling = button
      dragged = null
      scope.launch(start = CoroutineStart.UNDISPATCHED) {
        try {
          settle.snapTo(0f)
          settle.animateTo(1f, tween(if (reduced) 0 else 200, easing = FastOutSlowInEasing))
        } finally {
          settling = null
        }
      }
    }
    LaunchedEffect(layout, dragged) { if (dragged == null && preview == layout) preview = null }
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
        state = listState,
        userScrollEnabled = dragged == null && settling == null,
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
          val lift by
            animateFloatAsState(
              if (dragged == button) 1.015f else 1f,
              tween(if (reduced) 0 else 140),
              label = "drag_lift",
            )
          SettingsCard(
            Modifier.animateItem(
                placementSpec =
                  if (dragged == button || settling == button) null
                  else tween(if (reduced) 0 else 220)
              )
              .zIndex(if (dragged == button || settling == button) 1f else 0f)
              .padding(horizontal = 20.dp, vertical = 4.dp)
              .graphicsLayer {
                translationY =
                  when (button) {
                    dragged -> translation(button)
                    settling -> {
                      val position =
                        listState.layoutInfo.visibleItemsInfo
                          .firstOrNull { it.key == button.name }
                          ?.offset
                      if (position == null) 0f else (settleTop - position) * (1f - settle.value)
                    }
                    else -> 0f
                  }
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
                    enabled = dragged == null && settling == null && preview == null && index > 0,
                  ) {
                    Icon(Icons.ArrowDropUp, stringResource(R.string.mpv_move_earlier))
                  }
                  IconButton(
                    { preferences.landscapeLayout.set(latestShown.move(region, index, index + 1)) },
                    enabled =
                      dragged == null &&
                        settling == null &&
                        preview == null &&
                        index < shown.buttons(region).lastIndex,
                  ) {
                    Icon(Icons.ArrowDropDown, stringResource(R.string.mpv_move_later))
                  }
                  IconButton(
                    { preferences.landscapeLayout.set(latestShown.assign(button, null)) },
                    enabled = dragged == null && settling == null && preview == null,
                  ) {
                    Icon(Icons.Close, stringResource(R.string.update_remove_control))
                  }
                  Box(
                    Modifier.size(40.dp).pointerInput(button, region) {
                      detectDragGesturesAfterLongPress(
                        onDragStart = {
                          if (settling == null && dragged == null && preview == null) {
                            val item =
                              listState.layoutInfo.visibleItemsInfo.firstOrNull {
                                it.key == button.name
                              }
                            if (item != null) {
                              preview = latestShown
                              dragTop = item.offset.toFloat()
                              dragDistance = 0f
                              dragged = button
                            }
                          }
                        },
                        onDragEnd = { if (dragged == button) finishDrag(false) },
                        onDragCancel = { if (dragged == button) finishDrag(true) },
                        onDrag = { change, delta ->
                          if (dragged == button) {
                            change.consume()
                            dragDistance += delta.y
                            val working = preview ?: latestShown
                            val buttons = working.buttons(region)
                            val index = buttons.indexOf(button)
                            val visible = listState.layoutInfo.visibleItemsInfo
                            val active = visible.firstOrNull { it.key == button.name }
                            val direction = if (delta.y > 0f) 1 else if (delta.y < 0f) -1 else 0
                            val target = index + direction
                            val neighbor =
                              buttons.getOrNull(target)?.let { other ->
                                visible.firstOrNull { it.key == other.name }
                              }
                            if (active != null && neighbor != null && direction != 0) {
                              val center = dragTop + dragDistance + active.size / 2f
                              val threshold = neighbor.offset + neighbor.size / 2f
                              if (
                                direction > 0 && center > threshold ||
                                  direction < 0 && center < threshold
                              )
                                preview = working.move(region, index, target)
                            }
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
          LandscapeControl.entries.filter { it !in shown.buttons(region) },
          key = { "available:" + it.name },
        ) { button ->
          SettingsGroup {
            SettingsRow(
              { Text(stringResource(button.titleRes)) },
              icon = { Icon(button.icon(), null) },
              enabled =
                dragged == null &&
                  settling == null &&
                  preview == null &&
                  shown.buttons(region).size < 6,
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
