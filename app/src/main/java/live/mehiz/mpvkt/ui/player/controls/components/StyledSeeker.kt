package live.mehiz.mpvkt.ui.player.controls.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.vivvvek.seeker.*
import kotlin.math.*
import kotlinx.coroutines.isActive
import live.mehiz.mpvkt.R

/** Seeker owns input, accessibility and seek commits; the canvas only owns the visual skin. */
@Composable
internal fun StyledSeeker(
  position: Float,
  duration: Float,
  readAhead: Float,
  chapters: List<Segment>,
  onChange: (Float) -> Unit,
  onFinish: () -> Unit,
  modifier: Modifier = Modifier,
  style: SeekbarStyle = SeekbarStyle.Normal,
  paused: Boolean = true,
  reduceMotion: Boolean = false,
) {
  val safeDuration = duration.takeIf { it.isFinite() && it > 0f } ?: .001f
  val safePosition = position.takeIf(Float::isFinite)?.coerceIn(0f, safeDuration) ?: 0f
  val segments =
    remember(chapters, safeDuration) {
      val valid =
        chapters
          .filter { it.start.isFinite() && it.start >= 0f && it.start < safeDuration }
          .distinctBy { it.start }
          .sortedBy { it.start }
      if (valid.isNotEmpty() && valid.first().start > 0f) listOf(Segment("", 0f)) + valid else valid
    }
  val interactions = remember { MutableInteractionSource() }
  val dragging by interactions.collectIsDraggedAsState()
  var phase by remember { mutableFloatStateOf(0f) }
  val moving =
    !paused && !reduceMotion && !dragging && style in listOf(SeekbarStyle.Wavy, SeekbarStyle.Liquid)
  LaunchedEffect(moving) {
    if (moving) {
      var previous = 0L
      while (isActive) withFrameNanos { time ->
        if (previous != 0L)
          phase =
            (phase + ((time - previous) / 1_000_000_000f).coerceAtMost(.05f) * 3f) %
              (2f * PI.toFloat())
        previous = time
      }
    }
  }
  val colors = MaterialTheme.colorScheme
  val seekLabel = stringResource(R.string.mpv_playback_position)
  Box(
    modifier.height(48.dp).semantics {
      contentDescription = seekLabel
      progressBarRangeInfo = ProgressBarRangeInfo(safePosition, 0f..safeDuration)
      if (!duration.isFinite() || duration <= 0f) disabled()
      setProgress { requested ->
        if (duration.isFinite() && duration > 0f && requested.isFinite()) {
          onChange(requested.coerceIn(0f, safeDuration))
          onFinish()
          true
        } else false
      }
    }
  ) {
    Canvas(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
      val fraction = (safePosition / safeDuration).coerceIn(0f, 1f)
      val buffered = ((readAhead.takeIf(Float::isFinite) ?: 0f) / safeDuration).coerceIn(0f, 1f)
      val height = style.trackDp.dp.toPx()
      val y = size.height / 2f
      val x = size.width * fraction
      drawLine(
        colors.onSurface.copy(alpha = .18f),
        Offset(0f, y),
        Offset(size.width, y),
        height,
        StrokeCap.Round,
      )
      drawLine(
        colors.primary.copy(alpha = .3f),
        Offset(0f, y),
        Offset(size.width * buffered, y),
        height,
        StrokeCap.Round,
      )
      if (style == SeekbarStyle.Wavy || style == SeekbarStyle.Liquid) {
        val path = Path()
        val amplitude = if (style == SeekbarStyle.Wavy) 3.dp.toPx() else 1.6.dp.toPx()
        val waveLength = if (style == SeekbarStyle.Wavy) 28.dp.toPx() else 52.dp.toPx()
        val steps = (x / 2.dp.toPx()).roundToInt().coerceIn(1, 1000)
        for (index in 0..steps) {
          val px = x * index / steps
          val taper = sin(PI * index.toDouble() / steps).toFloat()
          val py = y + sin(px / waveLength * 2f * PI.toFloat() + phase) * amplitude * taper
          if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(
          path,
          colors.primary,
          style =
            Stroke(if (style == SeekbarStyle.Wavy) 3.dp.toPx() else height, cap = StrokeCap.Round),
        )
      } else drawLine(colors.primary, Offset(0f, y), Offset(x, y), height, StrokeCap.Round)
      segments.drop(1).forEach { chapter ->
        val cx = size.width * (chapter.start / safeDuration)
        drawLine(
          colors.surface.copy(alpha = .8f),
          Offset(cx, y - height / 2),
          Offset(cx, y + height / 2),
          1.5.dp.toPx(),
          StrokeCap.Round,
        )
      }
      when (style) {
        SeekbarStyle.Normal ->
          drawCircle(colors.primary, (if (dragging) 7 else 5).dp.toPx(), Offset(x, y))
        SeekbarStyle.Slim ->
          drawCircle(colors.primary, (if (dragging) 5 else 3).dp.toPx(), Offset(x, y))
        SeekbarStyle.Standard ->
          drawRoundRect(
            colors.primary,
            Offset(x - 2.dp.toPx(), y - 8.dp.toPx()),
            Size(4.dp.toPx(), 16.dp.toPx()),
            CornerRadius(2.dp.toPx()),
          )
        SeekbarStyle.Thick -> if (dragging) drawCircle(colors.primary, 10.dp.toPx(), Offset(x, y))
        SeekbarStyle.Wavy -> drawCircle(colors.primary, 4.dp.toPx(), Offset(x, y))
        SeekbarStyle.Liquid ->
          drawCircle(colors.primary, (if (dragging) 9 else 7).dp.toPx(), Offset(x, y))
      }
    }
    Seeker(
      value = safePosition,
      range = 0f..safeDuration,
      onValueChange = onChange,
      onValueChangeFinished = onFinish,
      readAheadValue = (readAhead.takeIf(Float::isFinite) ?: 0f).coerceIn(0f, safeDuration),
      enabled = duration.isFinite() && duration > 0f,
      segments = segments,
      interactionSource = interactions,
      modifier = Modifier.fillMaxSize().alpha(0f).clearAndSetSemantics {},
      dimensions = SeekerDefaults.seekerDimensions(thumbRadius = 8.dp),
    )
  }
}
