package live.mehiz.mpvkt.presentation.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp

/** Masks only the scroller, never the surrounding surface. */
internal fun Modifier.horizontalEdgeMask(start: () -> Boolean, end: () -> Boolean) =
  graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
      drawContent()
      val edge = (16.dp.toPx() / size.width.coerceAtLeast(1f)).coerceAtMost(.45f)
      drawRect(
        Brush.horizontalGradient(
          0f to if (start()) Color.Transparent else Color.Black,
          edge to Color.Black,
          (1f - edge) to Color.Black,
          1f to if (end()) Color.Transparent else Color.Black,
        ),
        blendMode = BlendMode.DstIn,
      )
    }

/** A restrained glass-like rim, without blur or an extra render pass. */
internal fun glassRimBrush(dark: Boolean): Brush =
  Brush.linearGradient(
    listOf(
      Color.White.copy(alpha = if (dark) .35f else .85f),
      Color.White.copy(alpha = .08f),
      Color.Gray.copy(alpha = .16f),
      Color.White.copy(alpha = if (dark) .23f else .65f),
    )
  )
