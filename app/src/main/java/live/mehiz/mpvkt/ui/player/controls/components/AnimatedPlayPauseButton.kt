package live.mehiz.mpvkt.ui.player.controls.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.ui.icons.IconStyle
import live.mehiz.mpvkt.ui.icons.LocalIconStyle

/** One continuous shape transition, one stable touch target, including rapid reversals. */
@Composable
fun AnimatedPlayPauseButton(
  paused: Boolean, loading: Boolean, pending: Boolean,
  enabled: Boolean, reduceMotion: Boolean, onClick: () -> Unit,
) {
  var showPending by remember { mutableStateOf(false) }
  LaunchedEffect(pending) { showPending = false; if (pending) { delay(120); showPending = true } }
  val actionLabel = stringResource(if (paused) R.string.notification_play else R.string.notification_pause)
  val progress by animateFloatAsState(if (paused) 0f else 1f,
    tween(if (reduceMotion) 0 else 260, easing = FastOutSlowInEasing), label = "play_pause_morph")
  val broken = LocalIconStyle.current == IconStyle.Broken
  Box(Modifier.size(96.dp).clip(CircleShape).semantics { contentDescription = actionLabel }
    .clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
    Canvas(Modifier.size(64.dp)) {
      val shape = playPausePath(progress)
      scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
        drawPath(shape, Color.White, style = Stroke(1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round,
          pathEffect = if (broken) PathEffect.dashPathEffect(floatArrayOf(18f, 2.5f, 34f, 0f)) else null))
      }
    }
    if (loading || showPending) CircularProgressIndicator(Modifier.size(88.dp), color = Color.White.copy(alpha = .8f), strokeWidth = 2.dp)
  }
}

/** Compatible vertices let the triangle separate into two pause bars without swapping icons. */
internal fun playPausePath(progress: Float): Path {
  val t = progress.coerceIn(0f, 1f)
  fun polygon(from: FloatArray, to: FloatArray) = Path().apply {
    fun coordinate(i: Int) = from[i] + (to[i] - from[i]) * t
    moveTo(coordinate(0), coordinate(1))
    for (i in 2 until from.size step 2) lineTo(coordinate(i), coordinate(i + 1))
    close()
  }
  val left = polygon(floatArrayOf(8f, 5f, 12f, 7.333333f, 12f, 16.666667f, 8f, 19f),
    floatArrayOf(7f, 5f, 10f, 5f, 10f, 19f, 7f, 19f))
  val right = polygon(floatArrayOf(12f, 7.333333f, 20f, 12f, 20f, 12f, 12f, 16.666667f),
    floatArrayOf(14f, 5f, 17f, 5f, 17f, 19f, 14f, 19f))
  return Path.combine(PathOperation.Union, left, right)
}
