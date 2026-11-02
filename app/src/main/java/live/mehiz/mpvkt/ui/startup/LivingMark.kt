package live.mehiz.mpvkt.ui.startup

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import live.mehiz.mpvkt.presentation.components.AppBrandMark
import live.mehiz.mpvkt.ui.motion.LocalReduceMotion

/** Activity indication only: no invented time or percentage of completed work. */
@Composable
internal fun LivingMark(modifier: Modifier = Modifier) {
  if (LocalReduceMotion.current) {
    AppBrandMark(modifier)
    return
  }
  val transition = rememberInfiniteTransition(label = "living_mark")
  val phase by
    transition.animateFloat(
      0f,
      1f,
      infiniteRepeatable(tween(1500, easing = LinearEasing)),
      label = "mark_light",
    )
  val color = MaterialTheme.colorScheme.primary
  val path = remember {
    Path().apply {
      moveTo(7f, 4f)
      cubicTo(5f, 3f, 4f, 4f, 4f, 6f)
      lineTo(4f, 18f)
      cubicTo(4f, 20f, 5f, 21f, 7f, 20f)
      lineTo(18f, 14f)
      cubicTo(20f, 13f, 20f, 11f, 18f, 10f)
      lineTo(7f, 4f)
      close()
    }
  }
  val measure = remember { PathMeasure().apply { setPath(path, true) } }
  val segment = remember { Path() }
  Canvas(modifier) {
    scale(size.width / 24f, size.height / 24f, pivot = androidx.compose.ui.geometry.Offset.Zero) {
      val stroke = Stroke(1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
      drawPath(path, color.copy(alpha = .22f), style = stroke)
      val start = phase * measure.length
      val end = start + measure.length * .4f
      segment.reset()
      measure.getSegment(start, end.coerceAtMost(measure.length), segment)
      drawPath(segment, color, style = stroke)
      if (end > measure.length) {
        segment.reset()
        measure.getSegment(0f, end - measure.length, segment)
        drawPath(segment, color, style = stroke)
      }
    }
  }
}
