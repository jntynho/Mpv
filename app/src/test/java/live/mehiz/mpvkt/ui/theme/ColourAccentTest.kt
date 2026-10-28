package live.mehiz.mpvkt.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test

class ColourAccentTest {
  @Test
  fun everyPaletteKeepsReadableForegroundsInBothModesAndOriginalIsUnchanged() {
    listOf(false, true).forEach { dark ->
      val base = if (dark) darkColorScheme() else lightColorScheme()
      assertSame(base, ColourAccent.Original.applyTo(base, dark))
      ColourAccent.entries
        .filter { it != ColourAccent.Original }
        .forEach { accent ->
          val scheme = accent.applyTo(base, dark)
          listOf(
              scheme.primary to scheme.onPrimary,
              scheme.secondary to scheme.onSecondary,
              scheme.tertiary to scheme.onTertiary,
              scheme.surface to scheme.onSurface,
              scheme.primaryContainer to scheme.onPrimaryContainer,
            )
            .forEach { (background, foreground) ->
              assertTrue(
                "${accent.name} dark=$dark contrast=${contrast(background,foreground)}",
                contrast(background, foreground) >= 4.5,
              )
            }
        }
    }
  }

  private fun contrast(a: Color, b: Color): Double =
    (maxOf(a.luminance(), b.luminance()) + .05).toDouble() /
      (minOf(a.luminance(), b.luminance()) + .05)
}
