package live.mehiz.mpvkt.ui.home

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** Circular corners measured from the reference, rather than the old squircle approximation. */
internal class SortMenuShape(
  private val radius: Dp,
  private val pointerX: Float? = null,
  private val pointerHeight: Dp = 0.dp,
  private val pointerWidth: Dp = 53.5.dp,
) : Shape {
  override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
    val top = with(density) { pointerHeight.toPx() }.coerceIn(0f, size.height / 3)
    val r = with(density) { radius.toPx() }.coerceIn(0f, minOf(size.width, size.height - top) / 2)
    val w = size.width
    val h = size.height
    if (top == 0f) return Outline.Rounded(RoundRect(Rect(0f, 0f, w, h), CornerRadius(r)))
    val half = with(density) { pointerWidth.toPx() / 2 }.coerceIn(0f, (w - 2 * r) / 2)
    val x = (pointerX ?: w * .625f).coerceIn(r + half, w - r - half)
    val path = Path().apply {
      moveTo(r, top)
      if (half > 0f) {
        lineTo(x - half, top)
        // Broad, tangent shoulders and a rounded crest, matching the reference silhouette.
        cubicTo(x - half * .237f, top, x - half * .438f, 0f, x, 0f)
        cubicTo(x + half * .438f, 0f, x + half * .237f, top, x + half, top)
      }
      lineTo(w - r, top)
      arcTo(Rect(w - 2 * r, top, w, top + 2 * r), -90f, 90f, false)
      lineTo(w, h - r)
      arcTo(Rect(w - 2 * r, h - 2 * r, w, h), 0f, 90f, false)
      lineTo(r, h)
      arcTo(Rect(0f, h - 2 * r, 2 * r, h), 90f, 90f, false)
      lineTo(0f, top + r)
      arcTo(Rect(0f, top, 2 * r, top + 2 * r), 180f, 90f, false)
      close()
    }
    return Outline.Generic(path)
  }
}
