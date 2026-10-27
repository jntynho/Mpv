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

@Composable
internal fun LibrarySortButton(sort: LibrarySort, countEnabled: Boolean, reduceMotion: Boolean, onSelect: (LibrarySort) -> Unit) {
  var expanded by remember { mutableStateOf(false) }
  val visible = remember { MutableTransitionState(false) }
  visible.targetState = expanded
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
    IconButton(onClick = { expanded = true }) { Icon(Icons.Sort, stringResource(R.string.mpv_sort)) }
    if (visible.currentState || visible.targetState || !visible.isIdle) {
      Popup(popupPositionProvider = position, onDismissRequest = { expanded = false },
        properties = PopupProperties(focusable = true, clippingEnabled = true)) {
        val shape = remember(pointerX, density) { SortMenuShape(30.dp, pointerX.takeIf { it > 0 }, 8.dp) }
        val available = with(density) { (window.height - anchorBottom).coerceAtLeast(0).toDp() - 16.dp }
        val colors = MaterialTheme.colorScheme
        val maxHeight = available.coerceAtLeast(150.dp)
        Column(Modifier.width(IntrinsicSize.Max).widthIn(min = 168.dp, max = 280.dp).heightIn(max = maxHeight)
          .graphicsLayer {
            alpha = progress
            translationY = if (reduceMotion) 0f else -8.dp.toPx() * (1f - progress)
            this.shape = shape; clip = true
          }.background(colors.surfaceContainerHigh, shape).padding(top = 8.dp)) {
          Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 8.dp)) {
            Text(stringResource(R.string.mpv_sort), Modifier.padding(start = 12.dp, bottom = 8.dp),
              style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
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
                SortChoice(title, sort.key == key, enabled, alignment = Alignment.CenterStart,
                  modifier = Modifier.fillMaxWidth().semantics { if (!enabled) stateDescription = disabledDescription }) {
                  onSelect(sort.copy(key = key)); expanded = false
                }
              }
            }
            Text(stringResource(R.string.mpv_sort_order), Modifier.padding(start = 12.dp, top = 12.dp, bottom = 6.dp),
              style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
  title: String, selected: Boolean, enabled: Boolean = true, tonal: Boolean = false, modifier: Modifier = Modifier,
  alignment: Alignment = Alignment.Center, onClick: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  val shape = remember { SortMenuShape(20.dp) }
  val labelStyle = MaterialTheme.typography.bodyLarge
  val height = with(LocalDensity.current) { labelStyle.lineHeight.toDp() + 20.dp }.coerceAtLeast(48.dp)
  Box(modifier.height(height).clip(shape).selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
    .padding(vertical = 2.dp), contentAlignment = alignment) {
    Box(Modifier.fillMaxSize().background(if (selected) colors.primary.copy(alpha = .18f) else if (tonal) colors.onSurface.copy(alpha = .08f) else Color.Transparent, shape)
      .padding(horizontal = 12.dp), contentAlignment = alignment) {
      Text(title, style = labelStyle, maxLines = 1, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = colors.onSurface.copy(alpha = if (enabled) 1f else .38f))
    }
  }
}
