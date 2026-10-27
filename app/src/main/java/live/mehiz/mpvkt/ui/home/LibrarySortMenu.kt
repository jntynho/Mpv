package live.mehiz.mpvkt.ui.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.ui.icons.Icons
import live.mehiz.mpvkt.ui.icons.Sort
import kotlin.math.roundToInt

internal class SortMenuPositionProvider(
  private val margin: Int,
  private val gap: Int,
  private val pointerPosition: (Float) -> Unit,
) : PopupPositionProvider {
  override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
    layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
    val maxX = (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)
    val x = (anchorBounds.center.x - popupContentSize.width * .68f).roundToInt().coerceIn(margin, maxX)
    val maxY = (windowSize.height - popupContentSize.height - margin).coerceAtLeast(margin)
    val y = (anchorBounds.bottom + gap).coerceIn(margin, maxY)
    pointerPosition((anchorBounds.center.x - x).toFloat())
    return IntOffset(x, y)
  }
}

/** Safe before the first window measurement and after multi-window/rotation changes. */
internal fun sortMenuMaxSize(window: IntSize, density: Density, anchorBottom: Int): DpSize = with(density) {
  val width = if (window.width > 0) window.width.toDp() else 360.dp
  val height = if (window.height > 0) window.height.toDp() else 640.dp
  DpSize((width - 24.dp).coerceIn(1.dp, 280.dp),
    (height - anchorBottom.toDp() - 16.dp).coerceAtLeast(96.dp).coerceAtMost((height - 24.dp).coerceAtLeast(1.dp)))
}

@Composable
internal fun LibrarySortButton(sort: LibrarySort, countEnabled: Boolean, reduceMotion: Boolean, enabled: Boolean = true, onSelect: (LibrarySort) -> Unit) {
  var expanded by remember { mutableStateOf(false) }
  val visible = remember { MutableTransitionState(false) }
  var menuLaidOut by remember { mutableStateOf(false) }
  visible.targetState = expanded && menuLaidOut
  val transition = rememberTransition(visible, label = "sort_menu")
  val progress by transition.animateFloat(transitionSpec = {
    tween(if (reduceMotion) 0 else if (targetState) 220 else 160, easing = FastOutSlowInEasing)
  }, label = "sort_menu_visibility") { if (it) 1f else 0f }
  val density = LocalDensity.current
  val window = LocalWindowInfo.current.containerSize
  var anchorBottom by remember { mutableIntStateOf(0) }
  var pointerX by remember { mutableFloatStateOf(0f) }
  val position = remember(density) {
    SortMenuPositionProvider(with(density) { 12.dp.roundToPx() }, with(density) { 2.dp.roundToPx() }) { pointerX = it }
  }
  Box(Modifier.onGloballyPositioned { anchorBottom = it.boundsInWindow().bottom.roundToInt() }) {
    IconButton(onClick = { expanded = true }, enabled = enabled) { Icon(Icons.Sort, stringResource(R.string.mpv_sort)) }
    if (expanded || visible.currentState || visible.targetState || !visible.isIdle) {
      Popup(popupPositionProvider = position, onDismissRequest = { expanded = false },
        properties = PopupProperties(focusable = true, clippingEnabled = true)) {
        DisposableEffect(Unit) { onDispose { menuLaidOut = false } }
        val shape = remember(pointerX, density) { SortMenuShape(32.dp, pointerX.takeIf { it > 0 }, 12.dp) }
        val bounds = sortMenuMaxSize(window, density, anchorBottom)
        val colors = MaterialTheme.colorScheme
        val dark = colors.surface.luminance() < .5f
        val background = if (dark) Color(0xFF142725) else colors.surfaceContainerHigh
        val caption = if (dark) Color(0xFF9DA9B5) else colors.onSurfaceVariant
        Column(Modifier.width(IntrinsicSize.Max).widthIn(min = minOf(240.dp, bounds.width), max = bounds.width).heightIn(max = bounds.height)
          .onGloballyPositioned { menuLaidOut = true }
          .graphicsLayer {
            alpha = progress
            translationY = if (reduceMotion) 0f else -8.dp.toPx() * (1f - progress)
            this.shape = shape; clip = true
          }.background(background).padding(top = 12.dp)) {
          Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 10.dp)) {
            Text(stringResource(R.string.mpv_sort), Modifier.padding(start = 10.dp, bottom = 12.dp),
              style = MaterialTheme.typography.titleSmall, color = caption)
            Column(Modifier.selectableGroup()) {
              LibrarySortKey.entries.forEach { key ->
                val enabled = key != LibrarySortKey.Count || countEnabled
                val title = stringResource(when (key) {
                  LibrarySortKey.Title -> R.string.mpv_sort_title
                  LibrarySortKey.Date -> R.string.mpv_sort_date
                  LibrarySortKey.Size -> R.string.mpv_sort_size
                  LibrarySortKey.Count -> R.string.mpv_sort_count
                })
                val disabledDescription = stringResource(R.string.mpv_sort_count_folders)
                SortChoice(title, sort.key == key, enabled = enabled, alignment = Alignment.CenterStart,
                  modifier = Modifier.fillMaxWidth().semantics { if (!enabled) stateDescription = disabledDescription }) {
                  onSelect(sort.copy(key = key)); expanded = false
                }
              }
            }
            Text(stringResource(R.string.mpv_sort_order), Modifier.padding(start = 10.dp, top = 16.dp, bottom = 8.dp),
              style = MaterialTheme.typography.titleSmall, color = caption)
            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              SortChoice(stringResource(R.string.mpv_sort_ascending), sort.ascending, tonal = true, modifier = Modifier.weight(1f).widthIn(min = 64.dp)) {
                onSelect(sort.copy(ascending = true)); expanded = false
              }
              SortChoice(stringResource(R.string.mpv_sort_descending), !sort.ascending, tonal = true, modifier = Modifier.weight(1f).widthIn(min = 64.dp)) {
                onSelect(sort.copy(ascending = false)); expanded = false
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SortChoice(
  title: String, selected: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, tonal: Boolean = false,
  alignment: Alignment = Alignment.Center, onClick: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  val dark = colors.surface.luminance() < .5f
  val selectedColor = if (dark) Color(0xFF2D514B) else colors.primary.copy(alpha = .18f)
  val tonalColor = if (dark) Color(0xFF343D44) else colors.onSurface.copy(alpha = .08f)
  val textColor = if (dark) Color(0xFFE6ECE9) else colors.onSurface
  val shape = remember { SortMenuShape(20.dp) }
  val labelStyle = MaterialTheme.typography.bodyLarge
  val height = with(LocalDensity.current) { labelStyle.lineHeight.toDp() + 20.dp }.coerceAtLeast(48.dp)
  Box(modifier.height(height).clip(shape).selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
    .padding(vertical = 2.dp), contentAlignment = alignment) {
    Box(Modifier.fillMaxSize().background(if (selected) selectedColor else if (tonal) tonalColor else Color.Transparent, shape)
      .padding(horizontal = 12.dp), contentAlignment = alignment) {
      Text(title, style = labelStyle, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = textColor.copy(alpha = if (enabled) 1f else .38f))
    }
  }
}
