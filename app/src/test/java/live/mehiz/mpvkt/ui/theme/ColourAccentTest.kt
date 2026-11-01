package live.mehiz.mpvkt.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test

class ColourAccentTest {
  @Test
  fun everyPaletteKeepsReadableForegroundsInBothModes() {
    listOf(false, true).forEach { dark ->
      val base = if (dark) darkColorScheme() else lightColorScheme()
      ColourAccent.entries
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

  @Test
  fun defaultPaletteRetainsItsExistingColours() {
    val light = ColourAccent.Default.applyTo(lightColorScheme(), false)
    val dark = ColourAccent.Default.applyTo(darkColorScheme(), true)
    assertEquals(Color(0xFF794F81), light.primary)
    assertEquals(Color(0xFFE8B5EF), dark.primary)
    assertEquals(Color(0xFFF7F5F8), light.surface)
    assertEquals(Color(0xFF161217), dark.surface)
  }

  @Test
  fun colourTransitionsReachBothEndpointsAndClampTheirProgress() {
    val from = ColourAccent.Default.applyTo(lightColorScheme(), false)
    val to = ColourAccent.Default.applyTo(darkColorScheme(), true)
    assertEquals(from.primary, interpolateAppColours(from, to, -1f).primary)
    assertEquals(to.surface, interpolateAppColours(from, to, 2f).surface)
    assertEquals(to.onPrimary, interpolateAppColours(from, to, 1f).onPrimary)
  }

  @Test
  fun intermediateFramesKeepSurfaceAndButtonTextReadable() {
    val from = ColourAccent.Default.applyTo(lightColorScheme(), false)
    val to = ColourAccent.Default.applyTo(darkColorScheme(), true)
    (1..19).forEach { frame ->
      val scheme = interpolateAppColours(from, to, frame / 20f)
      assertTrue(contrast(scheme.surface, scheme.onSurface) >= 4.5)
      assertTrue(contrast(scheme.primaryContainer, scheme.onPrimaryContainer) >= 4.5)
    }
  }

  private fun contrast(a: Color, b: Color): Double =
    (maxOf(a.luminance(), b.luminance()) + .05).toDouble() /
      (minOf(a.luminance(), b.luminance()) + .05)
}
