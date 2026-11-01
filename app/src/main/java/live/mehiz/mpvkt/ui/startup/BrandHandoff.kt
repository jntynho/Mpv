package live.mehiz.mpvkt.ui.startup

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import live.mehiz.mpvkt.presentation.components.AppBrandMark

@Stable
internal class BrandHandoff {
  var source by mutableStateOf<Rect?>(null)
  var target by mutableStateOf<Rect?>(null)
  var root by mutableStateOf(Rect.Zero)
  var moving by mutableStateOf(false)
}

internal val LocalBrandHandoff = staticCompositionLocalOf<BrandHandoff?> { null }
internal val LocalWarmupScene = staticCompositionLocalOf { false }

/** One image in window coordinates; neither endpoint is hard-coded to a phone size. */
@Composable
internal fun BrandFlight(state: BrandHandoff, progress: () -> Float) {
  val source = remember(state) { state.source } ?: return
  val target = state.target ?: return
  Box(
    Modifier.fillMaxSize().pointerInput(Unit) {
      awaitPointerEventScope {
        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
      }
    }
  ) {
    AppBrandMark(
      Modifier.size(96.dp).graphicsLayer {
        val rect = lerp(source, target, progress())
        transformOrigin = TransformOrigin(0f, 0f)
        translationX = rect.left - state.root.left
        translationY = rect.top - state.root.top
        scaleX = rect.width / size.width.coerceAtLeast(1f)
        scaleY = rect.height / size.height.coerceAtLeast(1f)
      }
    )
  }
}
