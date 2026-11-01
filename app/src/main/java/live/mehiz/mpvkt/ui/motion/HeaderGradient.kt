package live.mehiz.mpvkt.ui.motion

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

internal val LocalHeaderMaskColor = staticCompositionLocalOf { Color.Unspecified }
internal val LocalHeaderGradient = staticCompositionLocalOf { false }

/** Draw-only feather below the header: no extra layout, hit target or saved layer. */
@Composable
internal fun Modifier.headerGradient(): Modifier {
  val enabled = LocalHeaderGradient.current
  val strength by
    animateFloatAsState(
      if (enabled) 1f else 0f,
      tween(if (LocalReduceMotion.current) 0 else 180),
      label = "header_mask",
    )
  if (!enabled && strength == 0f) return this
  val maskColor = LocalHeaderMaskColor.current
  val surface = if (maskColor == Color.Unspecified) MaterialTheme.colorScheme.surface else maskColor
  return zIndex(1f).drawWithCache {
    val depth = 24.dp.toPx()
    val brush =
      Brush.verticalGradient(
        listOf(
          surface.copy(alpha = surface.alpha * .9f * strength),
          surface.copy(alpha = surface.alpha * .35f * strength),
          surface.copy(alpha = 0f),
        ),
        startY = size.height,
        endY = size.height + depth,
      )
    onDrawWithContent {
      drawContent()
      drawRect(brush, topLeft = Offset(0f, size.height), size = Size(size.width, depth))
    }
  }
}
