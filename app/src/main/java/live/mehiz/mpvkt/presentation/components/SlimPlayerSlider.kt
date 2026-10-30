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
  var editing by remember { mutableStateOf(false) }
  var pending by remember { mutableStateOf<Float?>(null) }
  LaunchedEffect(dragged) { if (!dragged) editing = false }
  val min = valueRange.start.takeIf { it.isFinite() } ?: 0f
  val max = valueRange.endInclusive.takeIf { it.isFinite() }?.coerceAtLeast(min) ?: min
  val range = min..max
  LaunchedEffect(value, pending, editing, dragged) {
    if (!editing && !dragged && pending != null) {
      if (abs(value - pending!!) > .001f) delay(800)
      pending = null
    }
  }
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  Slider(
    value =
      (pending?.takeIf { it.isFinite() } ?: value.takeIf { it.isFinite() } ?: min).coerceIn(range),
    onValueChange = change@{
        if (!it.isFinite()) return@change
        editing = true
        pending = it
        onValueChange(it)
      },
    modifier = modifier.heightIn(min = 48.dp),
    enabled = enabled && max > min,
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
        Modifier.size(14.dp)
          .background(if (enabled) colors.thumbColor else colors.disabledThumbColor, CircleShape)
      )
    },
    track = { state ->
      Canvas(Modifier.fillMaxWidth().height(4.dp)) {
        val fraction = if (max > min) ((state.value - min) / (max - min)).coerceIn(0f, 1f) else 0f
        val width = size.width * fraction
        drawRoundRect(
          if (enabled) colors.inactiveTrackColor else colors.disabledInactiveTrackColor,
          size = size,
          cornerRadius = CornerRadius(size.height),
        )
        drawRoundRect(
          if (enabled) colors.activeTrackColor else colors.disabledActiveTrackColor,
          topLeft = Offset(if (rtl) size.width - width else 0f, 0f),
          size = Size(width, size.height),
          cornerRadius = CornerRadius(size.height),
        )
      }
    },
  )
}
