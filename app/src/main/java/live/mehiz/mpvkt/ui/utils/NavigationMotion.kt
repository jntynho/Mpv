package live.mehiz.mpvkt.ui.utils

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset

fun navigationMotion(back: Boolean, reduceMotion: Boolean = false): ContentTransform {
  if (reduceMotion) return fadeIn(tween(0)) togetherWith fadeOut(tween(0))
  val direction = if (back) -1 else 1
  return (fadeIn(tween(280)) + slideIn(tween(280, easing = FastOutSlowInEasing)) {
    IntOffset(it.width * direction / 2, 0)
  }) togetherWith (fadeOut(tween(280)) + slideOut(tween(280, easing = FastOutSlowInEasing)) {
    IntOffset(-it.width * direction / 2, 0)
  })
}
