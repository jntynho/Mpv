package live.mehiz.mpvkt.presentation.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import live.mehiz.mpvkt.R

/** One expanded height; Back, scrim, drag and selection all share the same exit. */
@Composable
internal fun StableBottomSheet(
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
  title: String? = null,
  dismissRequested: Boolean = false,
  reduceMotion: Boolean = false,
  compact: Boolean = false,
  content: @Composable ColumnScope.() -> Unit,
) {
  val scope = rememberCoroutineScope()
  var settleJob by remember { mutableStateOf<Job?>(null) }
  DisposableEffect(Unit) { onDispose { settleJob?.cancel() } }
  var closing by remember { mutableStateOf(false) }
  var closingHeight by remember { mutableFloatStateOf(0f) }
  var measured by remember { mutableStateOf(false) }
  var height by remember { mutableFloatStateOf(1f) }
  var drag by remember { mutableFloatStateOf(0f) }
  val position = remember { Animatable(1f) }
  val latestDismiss by rememberUpdatedState(onDismissRequest)
  val close: () -> Unit = {
    if (!closing) {
      closingHeight = height
      closing = true
    }
  }
  val label = stringResource(R.string.generic_close)
  val velocity = with(LocalDensity.current) { 650.dp.toPx() }
  Dialog(
    onDismissRequest = close,
    properties =
      DialogProperties(
        dismissOnBackPress = false,
        dismissOnClickOutside = false,
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false,
      ),
  ) {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    DisposableEffect(window) {
      window?.setDimAmount(0f)
      window?.setWindowAnimations(0)
      onDispose {}
    }
    BackHandler(onBack = close)
    LaunchedEffect(dismissRequested) { if (dismissRequested) close() }
    LaunchedEffect(measured, closing, reduceMotion) {
      if (closing) {
        settleJob?.cancel()
        position.snapTo((position.value + drag).coerceIn(0f, 1f))
        drag = 0f
        position.animateTo(
          1f,
          tween(if (reduceMotion) 0 else 360, easing = CubicBezierEasing(.25f, 0f, .2f, 1f)),
        )
        latestDismiss()
      } else if (measured) {
        position.animateTo(0f, tween(if (reduceMotion) 0 else 320))
      }
    }
    BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
      Box(
        Modifier.fillMaxSize()
          .drawBehind {
            drawRect(Color.Black.copy(alpha = .32f * (1f - position.value - drag).coerceIn(0f, 1f)))
          }
          .semantics { contentDescription = label }
          .clickable(remember { MutableInteractionSource() }, indication = null, onClick = close)
      )
      Surface(
        Modifier.align(Alignment.BottomCenter)
          .widthIn(max = 504.dp)
          .fillMaxWidth()
          .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
          .heightIn(
            max =
              if (compact) (maxHeight * .72f).coerceAtMost(600.dp)
              else (maxHeight - 32.dp).coerceAtLeast(1.dp)
          )
          .then(
            if (compact && closing && closingHeight > 0f)
              Modifier.height(with(LocalDensity.current) { closingHeight.toDp() })
            else Modifier
          )
          .onSizeChanged {
            height = it.height.toFloat().coerceAtLeast(1f)
            measured = true
          }
          .graphicsLayer { translationY = (position.value + drag) * size.height }
          .then(
            if (compact)
              Modifier.background(
                  MaterialTheme.colorScheme.surfaceContainer,
                  RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                )
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .animateContentSize(
                  tween(if (reduceMotion) 0 else 260),
                  alignment = Alignment.TopCenter,
                )
            else Modifier
          )
          .then(modifier)
          .semantics {
            if (title != null) paneTitle = title
            dismiss {
              close()
              true
            }
          }
          .pointerInput(closing) {
            if (closing)
              awaitPointerEventScope {
                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach {
                  it.consume()
                }
              }
          },
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
      ) {
        Column(
          Modifier.windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
        ) {
          Box(
            Modifier.fillMaxWidth()
              .height(32.dp)
              .semantics {
                onClick(label) {
                  close()
                  true
                }
              }
              .draggable(
                rememberDraggableState { dy ->
                  if (!closing) drag = (drag + dy / height).coerceIn(0f, 1f)
                },
                orientation = Orientation.Vertical,
                enabled = !closing && !position.isRunning,
                onDragStarted = { settleJob?.cancel() },
                onDragStopped = { speed ->
                  if (closing) return@draggable
                  if (drag > .2f || speed > velocity) close()
                  else
                    settleJob =
                      scope.launch {
                        AnimationState(drag).animateTo(0f, tween(280)) { drag = value }
                      }
                },
              ),
            contentAlignment = Alignment.Center,
          ) {
            Box(
              Modifier.size(32.dp, 4.dp)
                .background(
                  MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .4f),
                  RoundedCornerShape(50),
                )
            )
          }
          content()
        }
      }
    }
  }
}
