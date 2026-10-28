package live.mehiz.mpvkt.ui.motion

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import live.mehiz.mpvkt.R

@Composable
fun ElasticRefreshBox(
  loading: Boolean,
  onRefresh: suspend () -> Unit,
  modifier: Modifier = Modifier,
  reduceMotion: Boolean = false,
  content: @Composable BoxScope.() -> Unit,
) {
  val scope = rememberCoroutineScope()
  val density = LocalDensity.current
  val window = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
  val canRefresh by rememberUpdatedState(!loading)
  val refresh by rememberUpdatedState(onRefresh)
  val pull =
    remember(density, reduceMotion, window) {
      ElasticPullState(
        scope,
        window.height.toFloat().coerceAtLeast(with(density) { 144.dp.toPx() }),
        true,
        threshold = with(density) { 64.dp.toPx() },
        hold = with(density) { 48.dp.toPx() },
        canRefresh = { canRefresh },
        onRefresh = { refresh() },
        reduceMotion = reduceMotion,
        platformOverscroll = true,
      )
    }
  DisposableEffect(pull) { onDispose(pull::cancel) }
  CompositionLocalProvider(
    LocalOverscrollFactory provides if (reduceMotion) null else LibraryOverscrollFactory
  ) {
    Box(modifier.nestedScroll(pull.connection)) {
      val progress = (pull.offset / pull.threshold).coerceIn(0f, 1f)
      if (pull.offset > .1f || pull.refreshing) {
        val sweep by
          animateFloatAsState(
            if (pull.refreshing) 270f else 32f + progress * 268f,
            tween(if (reduceMotion) 0 else 180),
            label = "refresh_arc",
          )
        val rotation: State<Float> =
          if (pull.refreshing && !reduceMotion) {
            rememberInfiniteTransition(label = "refresh_rotation")
              .animateFloat(
                0f,
                360f,
                infiniteRepeatable(tween(900, easing = LinearEasing)),
                label = "refresh_angle",
              )
          } else rememberUpdatedState(progress * 120f)
        val opacity by
          animateFloatAsState(
            if (pull.refreshing) 1f else progress,
            tween(if (reduceMotion) 0 else 140),
            label = "refresh_opacity",
          )
        val label =
          stringResource(
            if (pull.refreshing) R.string.mpv_refreshing else R.string.mpv_pull_refresh
          )
        val color = MaterialTheme.colorScheme.primary
        Box(
          Modifier.align(Alignment.TopCenter)
            .size(40.dp)
            .graphicsLayer {
              alpha = opacity
              translationY = (pull.offset - 40.dp.toPx()) / 2
            }
            .semantics {
              contentDescription = label
              progressBarRangeInfo =
                if (pull.refreshing) ProgressBarRangeInfo.Indeterminate
                else ProgressBarRangeInfo(progress, 0f..1f)
            },
          contentAlignment = Alignment.Center,
        ) {
          Canvas(Modifier.size(22.dp)) {
            drawArc(
              color,
              -90f + rotation.value,
              sweep,
              false,
              style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),
            )
          }
        }
      }
      Box(
        Modifier.fillMaxSize().graphicsLayer {
          translationY = if (pull.offset >= 0f) pull.offset else pull.offset * .3f
        },
        content = content,
      )
    }
  }
}
