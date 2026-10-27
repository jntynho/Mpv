package live.mehiz.mpvkt.ui.motion

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import kotlin.math.abs
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import live.mehiz.mpvkt.ui.icons.Icons
import live.mehiz.mpvkt.ui.icons.Refresh

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
  val canRefresh by rememberUpdatedState(!loading)
  val refresh by rememberUpdatedState(onRefresh)
  val pull = remember(density, reduceMotion) {
    ElasticPullState(scope, with(density) { 144.dp.toPx() }, true,
      threshold = with(density) { 64.dp.toPx() }, hold = with(density) { 56.dp.toPx() },
      canRefresh = { canRefresh }, onRefresh = { refresh() }, reduceMotion = reduceMotion)
  }
  DisposableEffect(pull) { onDispose(pull::cancel) }
  CompositionLocalProvider(LocalOverscrollFactory provides null) {
    Box(modifier.nestedScroll(pull.connection)) {
      Box(Modifier.align(Alignment.TopCenter).padding(top = 8.dp).size(40.dp).graphicsLayer {
        val progress = (pull.offset / pull.threshold).coerceIn(0f, 1f)
        alpha = if (pull.refreshing) 1f else progress
        translationY = (pull.offset / 2 - 20.dp.toPx()).coerceAtLeast(0f)
      }, contentAlignment = Alignment.Center) {
        if (pull.refreshing) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        else Icon(Icons.Refresh, null, Modifier.size(24.dp).graphicsLayer { rotationZ = if (reduceMotion) 0f else (pull.offset / pull.threshold).coerceIn(0f, 1f) * 180 })
      }
      Box(Modifier.fillMaxSize().graphicsLayer {
        val offset = pull.offset
        translationY = if (offset >= 0f) offset else offset * .3f
        if (!reduceMotion) {
          scaleY = 1f + abs(offset) / size.height.coerceAtLeast(1f) * .25f
          transformOrigin = TransformOrigin(.5f, if (offset >= 0f) 0f else 1f)
        }
      }, content = content)
    }
  }
}
