package live.mehiz.mpvkt.presentation.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import live.mehiz.mpvkt.preferences.PlayerPreferences
import live.mehiz.mpvkt.preferences.preference.collectAsState
import org.koin.compose.koinInject

/** The action runs after the pane has left, so nested panels never overlap. */
val LocalPlayerSheetClose = staticCompositionLocalOf<((() -> Unit) -> Unit)> { { action -> action() } }

@Composable
fun PlayerSheet(
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
  tonalElevation: Dp = 1.dp,
  content: @Composable () -> Unit,
) {
  val scope = rememberCoroutineScope()
  val latestDismiss by rememberUpdatedState(onDismissRequest)
  val reduceMotion by koinInject<PlayerPreferences>().reduceMotion.collectAsState()
  val progress = remember { Animatable(1f) }
  var drag by remember { mutableFloatStateOf(0f) }
  var widthPx by remember { mutableFloatStateOf(1f) }
  var closing by remember { mutableStateOf(false) }
  val close: ((() -> Unit) -> Unit) = { after ->
    if (!closing) {
      closing = true
      scope.launch {
        progress.snapTo(progress.value + drag / widthPx); drag = 0f
        progress.animateTo(1f, tween(if (reduceMotion) 0 else 240))
        latestDismiss()
        after()
      }
    }
  }
  LaunchedEffect(Unit) {
    if (reduceMotion) progress.snapTo(0f)
    else progress.animateTo(0f, spring(dampingRatio = .84f, stiffness = 380f))
  }
  BackHandler { close {} }
  BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
    val width = if (maxWidth > maxHeight) (maxWidth * .4f).coerceIn(300.dp, 420.dp).coerceAtMost(maxWidth)
      else maxWidth.coerceAtMost(420.dp)
    LaunchedEffect(width) { drag = 0f }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .23f * (1f - progress.value).coerceIn(0f, 1f)))
      .clickable(remember { MutableInteractionSource() }, indication = null) { close {} })
    Surface(
      modifier = Modifier.align(AbsoluteAlignment.CenterRight).width(width).fillMaxHeight()
        .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }.padding(12.dp)
        .graphicsLayer { translationX = progress.value * widthPx + drag }
        .draggable(
          state = rememberDraggableState { dx -> drag = (drag + dx).coerceIn(-progress.value * widthPx, widthPx * (1f - progress.value)) },
          orientation = Orientation.Horizontal, enabled = !closing,
          onDragStarted = { progress.stop() },
          onDragStopped = { velocity ->
            if (progress.value + drag / widthPx > .3f || velocity > 800f) close {}
            else {
              progress.snapTo(progress.value + drag / widthPx); drag = 0f
              progress.animateTo(0f, if (reduceMotion) tween(0) else spring(dampingRatio = .88f, stiffness = 380f))
            }
          },
        ).then(modifier)
        .clickable(remember { MutableInteractionSource() }, indication = null, onClick = {}),
      shape = RoundedCornerShape(24.dp), tonalElevation = tonalElevation,
      color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
      CompositionLocalProvider(LocalPlayerSheetClose provides close) { content() }
    }
  }
}
