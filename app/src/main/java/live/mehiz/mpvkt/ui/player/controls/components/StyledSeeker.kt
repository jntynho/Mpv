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
import androidx.compose.ui.graphics.drawscope.withTransform
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
  interactive: Boolean = true,
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
  val moving = !paused && !reduceMotion && !dragging && style == SeekbarStyle.Wavy
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
  val trackHeight by
    androidx.compose.animation.core.animateFloatAsState(
      when (style) {
        SeekbarStyle.Normal -> if (dragging) 6f else 4f
        SeekbarStyle.Standard -> if (paused || dragging) 5.6f else 8f
        SeekbarStyle.Thick -> if (paused || dragging) 11.2f else 16f
        SeekbarStyle.Slim -> if (dragging) 15f else if (paused) 6f else 8f
        SeekbarStyle.Wavy -> 8f
        SeekbarStyle.Liquid -> if (paused) 8f else 6f
      },
      androidx.compose.animation.core.tween(if (reduceMotion) 0 else 220),
      label = "seek_track_height",
    )
  val rtl =
    androidx.compose.ui.platform.LocalLayoutDirection.current ==
      androidx.compose.ui.unit.LayoutDirection.Rtl
  val colors = MaterialTheme.colorScheme
  val seekLabel = stringResource(R.string.mpv_playback_position)
  Box(
    modifier
      .height(48.dp)
      .semantics {
        contentDescription = seekLabel
        progressBarRangeInfo = ProgressBarRangeInfo(safePosition, 0f..safeDuration)
        if (!interactive || !duration.isFinite() || duration <= 0f) disabled()
        setProgress { requested ->
          if (interactive && duration.isFinite() && duration > 0f && requested.isFinite()) {
            onChange(requested.coerceIn(0f, safeDuration))
            onFinish()
            true
          } else false
        }
      }
      .then(if (!interactive) Modifier.clearAndSetSemantics {} else Modifier)
  ) {
    Canvas(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
      withTransform({ if (rtl) scale(-1f, 1f, center) }) {
        val fraction = (safePosition / safeDuration).coerceIn(0f, 1f)
        val buffered = ((readAhead.takeIf(Float::isFinite) ?: 0f) / safeDuration).coerceIn(0f, 1f)
        val height = trackHeight.dp.toPx()
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
        if (style == SeekbarStyle.Wavy) {
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
            drawCircle(colors.primary, (if (dragging) 9f else 6.5f).dp.toPx(), Offset(x, y))
          SeekbarStyle.Slim -> Unit
          SeekbarStyle.Standard ->
            drawRoundRect(
              colors.primary,
              Offset(x - 2.dp.toPx(), y - 8.dp.toPx()),
              Size(4.dp.toPx(), 16.dp.toPx()),
              CornerRadius(2.dp.toPx()),
            )
          SeekbarStyle.Thick -> if (dragging) drawCircle(colors.primary, 10.dp.toPx(), Offset(x, y))
          SeekbarStyle.Wavy -> drawCircle(colors.primary, 4.dp.toPx(), Offset(x, y))
          SeekbarStyle.Liquid -> {
            val thumbWidth = (if (dragging) 30 else 22).dp.toPx()
            val thumbHeight = (if (dragging) 26 else 20).dp.toPx()
            val rect = Offset(x - thumbWidth / 2, y - thumbHeight / 2)
            val geometry = Size(thumbWidth, thumbHeight)
            drawRoundRect(
              androidx.compose.ui.graphics.lerp(colors.surfaceContainerHigh, colors.primary, .25f),
              rect,
              geometry,
              CornerRadius(thumbHeight / 2),
            )
            drawRoundRect(
              colors.onSurface.copy(alpha = .35f),
              rect,
              geometry,
              CornerRadius(thumbHeight / 2),
              style = Stroke(1.dp.toPx()),
            )
            drawLine(
              colors.onSurface.copy(alpha = .5f),
              Offset(x - 3.dp.toPx(), y - 5.dp.toPx()),
              Offset(x - 3.dp.toPx(), y + 5.dp.toPx()),
              1.dp.toPx(),
              StrokeCap.Round,
            )
            drawLine(
              colors.onSurface.copy(alpha = .5f),
              Offset(x + 3.dp.toPx(), y - 5.dp.toPx()),
              Offset(x + 3.dp.toPx(), y + 5.dp.toPx()),
              1.dp.toPx(),
              StrokeCap.Round,
            )
          }
        }
      }
    }
    Seeker(
      value = safePosition,
      range = 0f..safeDuration,
      onValueChange = onChange,
      onValueChangeFinished = onFinish,
      readAheadValue = (readAhead.takeIf(Float::isFinite) ?: 0f).coerceIn(0f, safeDuration),
      enabled = interactive && duration.isFinite() && duration > 0f,
      segments = segments,
      interactionSource = interactions,
      modifier = Modifier.fillMaxSize().alpha(0f).clearAndSetSemantics {},
      dimensions = SeekerDefaults.seekerDimensions(thumbRadius = 8.dp),
    )
  }
}
