package live.mehiz.mpvkt.ui.player.controls.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.ui.icons.*
import kotlin.math.roundToInt

@Composable
fun PlayerPanelHeader(title: String, icon: ImageVector?, onClose: () -> Unit, onBack: (() -> Unit)? = null, animateTitle: Boolean = true) {
  Column {
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
      verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        if (onBack != null) IconButton(onClick = onBack) {
          Icon(Icons.AutoMirrored.ArrowBack, stringResource(R.string.mpv_back))
        } else if (icon != null) Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
      }
      Crossfade(title, Modifier.weight(1f), animationSpec = tween(if (animateTitle) 180 else 0), label = "pane_title") { text ->
        Text(text, style = MaterialTheme.typography.titleLarge, maxLines = 1,
          overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
      }
      IconButton(onClick = onClose) { Icon(Icons.Close, stringResource(R.string.generic_close)) }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))
  }
}

@Composable
fun PlayerActionRow(
  title: String,
  icon: ImageVector,
  onClick: () -> Unit,
  value: String? = null,
  subtitle: String? = null,
  trailing: (@Composable () -> Unit)? = null,
) {
  Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    Icon(icon, null, Modifier.size(23.dp), tint = MaterialTheme.colorScheme.primary)
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
      Text(title, style = MaterialTheme.typography.bodyLarge)
      if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (trailing != null) trailing() else {
      if (value != null) Text(value, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Icon(Icons.ArrowDropDown, null, Modifier.size(18.dp).graphicsLayer { rotationZ = -90f }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
fun PlayerPanelSection(title: String) {
  Text(title, Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp),
    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun PlayerPanelFooter(title: String, onClick: () -> Unit, note: String? = null) {
  Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .25f))
    Spacer(Modifier.height(8.dp))
    FilledTonalButton(onClick, Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
      Icon(Icons.Add, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(title)
    }
    if (note != null) Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
fun PlayerScrollArea(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
  val state = rememberScrollState()
  val scope = rememberCoroutineScope()
  val scrollJob = remember { arrayOfNulls<Job>(1) }
  Box(modifier) {
    Column(Modifier.fillMaxSize().verticalScroll(state).padding(end = 10.dp), content = content)
    ThinPlayerScrollbar(state.isScrollInProgress, state.maxValue > 0,
      fraction = { if (state.maxValue > 0) state.value.toFloat() / state.maxValue else 0f },
      proportion = { state.viewportSize.toFloat() / (state.viewportSize + state.maxValue).coerceAtLeast(1) },
      onFraction = { f -> scrollJob[0]?.cancel(); scrollJob[0] = scope.launch { state.scrollTo((f * state.maxValue).roundToInt()) } })
  }
}

@Composable
fun PlayerLazyScrollArea(modifier: Modifier = Modifier, state: LazyListState = rememberLazyListState(), content: LazyListScope.() -> Unit) {
  val scope = rememberCoroutineScope()
  val scrollJob = remember { arrayOfNulls<Job>(1) }
  Box(modifier) {
    LazyColumn(Modifier.fillMaxSize().padding(end = 10.dp), state = state, content = content)
    val overflow by remember(state) { derivedStateOf { state.canScrollForward || state.canScrollBackward } }
    ThinPlayerScrollbar(state.isScrollInProgress, overflow,
      fraction = {
        val info = state.layoutInfo
        val total = (info.totalItemsCount - info.visibleItemsInfo.size).coerceAtLeast(1)
        (state.firstVisibleItemIndex.toFloat() + state.firstVisibleItemScrollOffset.toFloat() /
          (info.visibleItemsInfo.firstOrNull()?.size ?: 1).coerceAtLeast(1)) / total
      },
      proportion = { state.layoutInfo.run { visibleItemsInfo.size.toFloat() / totalItemsCount.coerceAtLeast(1) } },
      onFraction = { f -> scrollJob[0]?.cancel(); scrollJob[0] = scope.launch {
        val info = state.layoutInfo
        val count = info.totalItemsCount
        if (count > 0) state.scrollToItem((f * (count - info.visibleItemsInfo.size).coerceAtLeast(0)).roundToInt().coerceIn(0, count - 1))
      } })
  }
}

@Composable
private fun BoxScope.ThinPlayerScrollbar(
  scrolling: Boolean, overflow: Boolean,
  fraction: () -> Float, proportion: () -> Float, onFraction: (Float) -> Unit,
) {
  val minThumb = with(LocalDensity.current) { 24.dp.toPx() }
  var dragging by remember { mutableStateOf(false) }
  var visible by remember { mutableStateOf(false) }
  var height by remember { mutableFloatStateOf(1f) }
  val move by rememberUpdatedState(onFraction)
  val currentFraction by rememberUpdatedState(fraction)
  val currentProportion by rememberUpdatedState(proportion)
  LaunchedEffect(scrolling, dragging) {
    if (scrolling || dragging) visible = true else { delay(700); visible = false }
  }
  val alpha by animateFloatAsState(if (visible && overflow) 1f else 0f, label = "player_scrollbar")
  val color = MaterialTheme.colorScheme.primary.copy(alpha = .65f)
  if (overflow) Canvas(Modifier.align(AbsoluteAlignment.CenterRight).fillMaxHeight().width(14.dp)
    .padding(vertical = 8.dp).onSizeChanged { height = it.height.toFloat().coerceAtLeast(1f) }
    .graphicsLayer { this.alpha = alpha }
    .draggable(orientation = Orientation.Vertical, enabled = visible || dragging,
      state = rememberDraggableState { dy ->
        val thumb = (height * currentProportion().coerceIn(0f, 1f)).coerceIn(minThumb.coerceAtMost(height), height)
        move((currentFraction() + dy / (height - thumb).coerceAtLeast(1f)).coerceIn(0f, 1f))
      }, onDragStarted = { dragging = true }, onDragStopped = { dragging = false })) {
    val thumb = (size.height * proportion().coerceIn(0f, 1f)).coerceIn(24.dp.toPx().coerceAtMost(size.height), size.height)
    val x = size.width - 5.dp.toPx()
    drawRoundRect(color.copy(alpha = .12f), Offset(x, 0f), Size(2.dp.toPx(), size.height), CornerRadius(2.dp.toPx()))
    drawRoundRect(color, Offset(x, (size.height - thumb) * fraction().coerceIn(0f, 1f)), Size(2.dp.toPx(), thumb), CornerRadius(2.dp.toPx()))
  }
}
