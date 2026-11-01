package live.mehiz.mpvkt.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.delay

/**
 * Native slider semantics/gestures, with a thin track and a local drag preview immune to delayed
 * engine echoes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlimPlayerSlider(
  value: Float,
  onValueChange: (Float) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
  steps: Int = 0,
  onValueChangeFinished: (() -> Unit)? = null,
  colors: SliderColors = SliderDefaults.colors(),
  interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
  val dragged by interactionSource.collectIsDraggedAsState()
  val min = valueRange.start.takeIf { it.isFinite() } ?: 0f
  val max = valueRange.endInclusive.takeIf { it.isFinite() }?.coerceAtLeast(min) ?: min
  val interactive = enabled && max > min
  val safeValue = value.takeIf { it.isFinite() }?.coerceIn(min, max) ?: min
  var editing by remember(min, max) { mutableStateOf(false) }
  var pending by remember(min, max) { mutableStateOf<Float?>(null) }
  LaunchedEffect(dragged) { if (!dragged) editing = false }
  LaunchedEffect(interactive) { if (!interactive) { editing = false; pending = null } }
  val range = min..max
  LaunchedEffect(safeValue, pending, editing, dragged) {
    if (!editing && !dragged && pending != null) {
      if (abs(safeValue - pending!!) > .001f) delay(800)
      pending = null
    }
  }
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  Slider(
    value = (pending ?: safeValue).coerceIn(range),
    onValueChange = {
      editing = true
      pending = it
      onValueChange(it)
    },
    modifier = modifier.heightIn(min = 48.dp),
    enabled = interactive,
    valueRange = range,
    steps = steps.coerceAtLeast(0),
    colors = colors,
    interactionSource = interactionSource,
    onValueChangeFinished = {
      editing = false
      onValueChangeFinished?.invoke()
    },
    thumb = {
      Box(
        // Relax the slider's 48dp minimum here: it is a touch target, not thumb height.
        Modifier.wrapContentSize().requiredSize(14.dp)
          .background(if (interactive) colors.thumbColor else colors.disabledThumbColor, CircleShape)
      )
    },
    track = { state ->
      Canvas(Modifier.fillMaxWidth().height(4.dp)) {
        val fraction = if (max > min) ((state.value - min) / (max - min)).coerceIn(0f, 1f) else 0f
        val width = size.width * fraction
        drawRoundRect(
          if (interactive) colors.inactiveTrackColor else colors.disabledInactiveTrackColor,
          size = size,
          cornerRadius = CornerRadius(size.height),
        )
        drawRoundRect(
          if (interactive) colors.activeTrackColor else colors.disabledActiveTrackColor,
          topLeft = Offset(if (rtl) size.width - width else 0f, 0f),
          size = Size(width, size.height),
          cornerRadius = CornerRadius(size.height),
        )
      }
    },
  )
}
