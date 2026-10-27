package live.mehiz.mpvkt.ui.home

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** Two cubic segments per corner soften the junction with the straight edges. */
internal class SortMenuShape(
  private val radius: Dp,
  private val pointerX: Float? = null,
  private val pointerHeight: Dp = 0.dp,
) : Shape {
  override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
    val top = with(density) { pointerHeight.toPx() }.coerceAtMost(size.height / 3)
    val r = with(density) { radius.toPx() }.coerceIn(0f, minOf(size.width, size.height - top) / 2)
    val w = size.width; val h = size.height
    val path = Path().apply {
      moveTo(r, top)
      if (top > 0f && w > 2 * r + 24f) {
        val half = with(density) { 12.dp.toPx() }.coerceAtMost((w - 2 * r) / 2)
        val x = (pointerX ?: (w / 2)).coerceIn(r + half, w - r - half)
        lineTo(x - half, top)
        cubicTo(x - half * .55f, top, x - half * .55f, 0f, x, 0f)
        cubicTo(x + half * .55f, 0f, x + half * .55f, top, x + half, top)
      }
      lineTo(w - r, top)
      cubicTo(w - r * .5f, top, w - r * .25f, top, w - r * .125f, top + r * .125f)
      cubicTo(w, top + r * .25f, w, top + r * .5f, w, top + r)
      lineTo(w, h - r)
      cubicTo(w, h - r * .5f, w, h - r * .25f, w - r * .125f, h - r * .125f)
      cubicTo(w - r * .25f, h, w - r * .5f, h, w - r, h)
      lineTo(r, h)
      cubicTo(r * .5f, h, r * .25f, h, r * .125f, h - r * .125f)
      cubicTo(0f, h - r * .25f, 0f, h - r * .5f, 0f, h - r)
      lineTo(0f, top + r)
      cubicTo(0f, top + r * .5f, 0f, top + r * .25f, r * .125f, top + r * .125f)
      cubicTo(r * .25f, top, r * .5f, top, r, top)
      close()
    }
    return Outline.Generic(path)
  }
}
