package live.mehiz.mpvkt.presentation.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.ui.motion.SpringEngine
import live.mehiz.mpvkt.ui.motion.runSettleAnimation

private val paneEasing = CubicBezierEasing(.3f, 0f, .2f, 1f)

/** Drag and animation write one position, so interruption cannot double-apply an offset. */
@Stable
internal class PaneMotion {
  var position by mutableFloatStateOf(1f)
    private set

  private var job: Job? = null
  private var reboundFraction = 0f

  fun open(scope: CoroutineScope, distance: Float, rebound: Float, reduceMotion: Boolean) {
    stop()
    reboundFraction = rebound / distance.coerceAtLeast(1f)
    job =
      scope.launch {
        if (reduceMotion) {
          position = 0f
          return@launch
        }
        // A single analytical spring has continuous velocity, including at its overshoot.
        AnimationState(position).animateTo(
          0f,
          spring(dampingRatio = .72f, stiffness = 320f, visibilityThreshold = .0005f),
        ) {
          position = value
        }
        position = 0f
      }
  }

  fun settle(scope: CoroutineScope, distance: Float, reduceMotion: Boolean) {
    stop()
    job =
      scope.launch {
        if (!reduceMotion)
          SpringEngine()
            .runSettleAnimation(
              position * distance,
              initialVelocity = 0f,
              onFrame = { position = it / distance },
              onSettle = {},
            )
        position = 0f
      }
  }

  fun finishOpen() {
    stop()
    position = 0f
  }

  fun animate(scope: CoroutineScope, target: Float, duration: Int, finished: () -> Unit = {}) {
    stop()
    job =
      scope.launch {
        AnimationState(position).animateTo(target, tween(duration, easing = paneEasing)) {
          position = value
        }
        position = target
        finished()
      }
  }

  fun stop() {
    job?.cancel()
  }

  fun drag(delta: Float) {
    position = (position + delta).coerceIn(-reboundFraction, 1f)
  }
}

/** One surface for the lifetime of the player pane; only its inner page changes. */
@Composable
fun PlayerSheet(
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
  onNavigateBack: (() -> Unit)? = null,
  tonalElevation: Dp = 1.dp,
  reduceMotion: Boolean = false,
  onCloseStart: () -> Unit = {},
  interactive: Boolean = true,
  opacity: Float = 1f,
  edgeToEdge: Boolean = false,
  content: @Composable (onClose: () -> Unit, headerDrag: Modifier) -> Unit,
) {
  val scope = rememberCoroutineScope()
  val latestDismiss by rememberUpdatedState(onDismissRequest)
  val latestCloseStart by rememberUpdatedState(onCloseStart)
  val latestBack by rememberUpdatedState(onNavigateBack)
  val motion = remember { PaneMotion() }
  val dismissVelocity = with(LocalDensity.current) { 550.dp.toPx() }
  var widthPx by remember { mutableFloatStateOf(0f) }
  val reboundPx = with(LocalDensity.current) { 24.dp.toPx() }
  val extraPx = with(LocalDensity.current) { 24.dp.toPx() }
  var laidOut by remember { mutableStateOf(false) }
  var closing by remember { mutableStateOf(false) }
  val close: () -> Unit = {
    if (!closing) {
      closing = true
      latestCloseStart()
      motion.animate(scope, 1f, if (reduceMotion) 0 else 320) { latestDismiss() }
    }
  }
  // Geometry/inset updates during first layout must not cancel the entrance and snap it open.
  // Position is normalized, so resizing while moving keeps one animation and one surface.
  LaunchedEffect(laidOut) {
    if (laidOut && !closing) motion.open(scope, widthPx + extraPx, reboundPx, reduceMotion)
  }
  LaunchedEffect(reduceMotion) { if (reduceMotion && laidOut && !closing) motion.finishOpen() }
  DisposableEffect(motion) { onDispose(motion::stop) }
  BackHandler(enabled = interactive) { if (!closing) latestBack?.invoke() ?: close() }
  val closeLabel = stringResource(R.string.generic_close)
  val headerDrag =
    Modifier.draggable(
      state =
        rememberDraggableState { dx ->
          if (!closing) motion.drag(dx / (widthPx + extraPx).coerceAtLeast(1f))
        },
      orientation = Orientation.Horizontal,
      enabled = interactive && !closing && laidOut,
      onDragStarted = { motion.stop() },
      onDragStopped = { velocity ->
        if ((motion.position > .3f && velocity > -dismissVelocity) || velocity > dismissVelocity)
          close()
        else motion.settle(scope, widthPx + extraPx, reduceMotion)
      },
    )
  Box(Modifier.fillMaxSize()) {
    Box(
      Modifier.fillMaxSize()
        .drawBehind {
          drawRect(Color.Black.copy(alpha = .23f * (1f - motion.position).coerceIn(0f, 1f)))
        }
        .semantics { contentDescription = closeLabel }
        .clickable(
          remember { MutableInteractionSource() },
          indication = null,
          enabled = interactive,
          onClick = close,
        )
    )
    BoxWithConstraints(
      Modifier.fillMaxSize()
        .then(if (edgeToEdge) Modifier else Modifier.windowInsetsPadding(WindowInsets.safeDrawing))
        .imePadding()
        .padding(if (edgeToEdge) 0.dp else 12.dp)
    ) {
      val width =
        if (maxWidth > maxHeight)
          (maxWidth * (if (edgeToEdge) .52f else .48f))
            .coerceIn(336.dp, if (edgeToEdge) 560.dp else 504.dp)
            .coerceAtMost(maxWidth)
        else maxWidth.coerceAtMost(504.dp)
      Surface(
        modifier =
          Modifier.align(AbsoluteAlignment.CenterRight)
            .width(width)
            .fillMaxHeight()
            .onSizeChanged {
              widthPx = it.width.toFloat().coerceAtLeast(1f)
              laidOut = true
            }
            .graphicsLayer { // In edge mode, keep the right edge pinned during spring overshoot.
              translationX =
                (if (edgeToEdge) motion.position.coerceAtLeast(0f) else motion.position) *
                  (size.width + 24.dp.toPx())
            }
            .pointerInput(closing, interactive) {
              // Once exit starts, a late tap cannot change tracks or push another page.
              if (closing || !interactive)
                awaitPointerEventScope {
                  while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach {
                    it.consume()
                  }
                }
            }
            .then(modifier)
            .pointerInput(Unit) { detectTapGestures(onTap = {}) },
        shape =
          if (edgeToEdge) AbsoluteRoundedCornerShape(topLeft = 24.dp, bottomLeft = 24.dp)
          else RoundedCornerShape(24.dp),
        tonalElevation = tonalElevation,
        color =
          MaterialTheme.colorScheme.surfaceContainer.copy(
            alpha = opacity.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 1f
          ),
      ) {
        Box(
          Modifier.fillMaxSize()
            .then(
              if (edgeToEdge)
                Modifier.windowInsetsPadding(
                    WindowInsets.safeDrawing
                      .union(WindowInsets.statusBarsIgnoringVisibility)
                      .only(
                        WindowInsetsSides.Top +
                          WindowInsetsSides.Bottom +
                          (if (width == maxWidth) WindowInsetsSides.Horizontal
                          else WindowInsetsSides.Right)
                      )
                  )
                  .graphicsLayer {
                    // The surface stays flush; only its contents follow negative spring overshoot.
                    translationX = motion.position.coerceAtMost(0f) * (widthPx + extraPx)
                  }
              else Modifier
            )
        ) {
          content(close, headerDrag)
        }
      }
    }
  }
}
