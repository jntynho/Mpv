package live.mehiz.mpvkt.presentation.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import live.mehiz.mpvkt.preferences.PlayerPreferences
import live.mehiz.mpvkt.preferences.preference.collectAsState
import org.koin.compose.koinInject

private val paneEasing = CubicBezierEasing(.2f, .8f, .2f, 1f)

/** Drag and animation write one position, so interruption cannot double-apply an offset. */
@Stable
private class PaneMotion {
  var position by mutableFloatStateOf(1f); private set
  private var job: Job? = null

  fun animate(scope: CoroutineScope, target: Float, duration: Int, finished: () -> Unit = {}) {
    stop()
    job = scope.launch {
      AnimationState(position).animateTo(target, tween(duration, easing = paneEasing)) { position = value }
      position = target
      finished()
    }
  }
  fun stop() { job?.cancel() }
  fun drag(delta: Float) { position = (position + delta).coerceIn(0f, 1f) }
}

/** One surface for the lifetime of the player pane; only its inner page changes. */
@Composable
fun PlayerSheet(
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
  onNavigateBack: (() -> Unit)? = null,
  tonalElevation: Dp = 1.dp,
  content: @Composable (onClose: () -> Unit) -> Unit,
) {
  val scope = rememberCoroutineScope()
  val latestDismiss by rememberUpdatedState(onDismissRequest)
  val latestBack by rememberUpdatedState(onNavigateBack)
  val reduceMotion by koinInject<PlayerPreferences>().reduceMotion.collectAsState()
  val motion = remember { PaneMotion() }
  val dismissVelocity = with(LocalDensity.current) { 550.dp.toPx() }
  var widthPx by remember { mutableFloatStateOf(1f) }
  var closing by remember { mutableStateOf(false) }
  val close: () -> Unit = {
    if (!closing) {
      closing = true
      motion.animate(scope, 1f, if (reduceMotion) 0 else 240) { latestDismiss() }
    }
  }
  LaunchedEffect(Unit) { motion.animate(scope, 0f, if (reduceMotion) 0 else 360) }
  DisposableEffect(motion) { onDispose(motion::stop) }
  BackHandler { if (!closing) latestBack?.invoke() ?: close() }
  Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .23f * (1f - motion.position)))
      .clickable(remember { MutableInteractionSource() }, indication = null, onClick = close))
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
      val width = if (maxWidth > maxHeight) (maxWidth * .45f).coerceIn(320.dp, 480.dp).coerceAtMost(maxWidth)
        else maxWidth.coerceAtMost(480.dp)
      Surface(
        modifier = Modifier.align(AbsoluteAlignment.CenterRight).width(width).fillMaxHeight()
          .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }.padding(12.dp)
          .graphicsLayer { translationX = motion.position * (size.width + 24.dp.toPx()) }
          .draggable(
            state = rememberDraggableState { dx -> motion.drag(dx / widthPx) },
            orientation = Orientation.Horizontal, enabled = !closing,
            onDragStarted = { motion.stop() },
            onDragStopped = { velocity ->
              if (motion.position > .3f || velocity > dismissVelocity) close()
              else motion.animate(scope, 0f, if (reduceMotion) 0 else 220)
            },
          ).then(modifier)
          .clickable(remember { MutableInteractionSource() }, indication = null, onClick = {}),
        shape = RoundedCornerShape(24.dp), tonalElevation = tonalElevation,
        color = MaterialTheme.colorScheme.surfaceContainer,
      ) { content(close) }
    }
  }
}
