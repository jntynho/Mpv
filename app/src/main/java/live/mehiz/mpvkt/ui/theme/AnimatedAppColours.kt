package live.mehiz.mpvkt.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** One animation clock; interrupted transitions resume from the colours already on screen. */
@Composable
internal fun animatedAppColours(target: ColorScheme, reduceMotion: Boolean): ColorScheme {
  var visible by remember { mutableStateOf(target) }
  LaunchedEffect(target, reduceMotion) {
    if (visible === target) return@LaunchedEffect
    if (reduceMotion) visible = target
    else {
      val start = visible
      animate(0f, 1f, animationSpec = tween(240, easing = FastOutSlowInEasing)) { fraction, _ ->
        visible = interpolateAppColours(start, target, fraction)
      }
      visible = target
    }
  }
  return if (reduceMotion) target else visible
}

internal fun interpolateAppColours(from: ColorScheme, to: ColorScheme, progress: Float): ColorScheme {
  val fraction = progress.coerceIn(0f, 1f)
  if (fraction <= 0f) return from
  if (fraction >= 1f) return to
  val mixed = to.copy(
    primary = lerp(from.primary, to.primary, fraction),
    onPrimary = lerp(from.onPrimary, to.onPrimary, fraction),
    primaryContainer = lerp(from.primaryContainer, to.primaryContainer, fraction),
    onPrimaryContainer = lerp(from.onPrimaryContainer, to.onPrimaryContainer, fraction),
    inversePrimary = lerp(from.inversePrimary, to.inversePrimary, fraction),
    secondary = lerp(from.secondary, to.secondary, fraction),
    onSecondary = lerp(from.onSecondary, to.onSecondary, fraction),
    secondaryContainer = lerp(from.secondaryContainer, to.secondaryContainer, fraction),
    onSecondaryContainer = lerp(from.onSecondaryContainer, to.onSecondaryContainer, fraction),
    tertiary = lerp(from.tertiary, to.tertiary, fraction),
    onTertiary = lerp(from.onTertiary, to.onTertiary, fraction),
    tertiaryContainer = lerp(from.tertiaryContainer, to.tertiaryContainer, fraction),
    onTertiaryContainer = lerp(from.onTertiaryContainer, to.onTertiaryContainer, fraction),
    background = lerp(from.background, to.background, fraction),
    onBackground = lerp(from.onBackground, to.onBackground, fraction),
    surface = lerp(from.surface, to.surface, fraction),
    onSurface = lerp(from.onSurface, to.onSurface, fraction),
    surfaceVariant = lerp(from.surfaceVariant, to.surfaceVariant, fraction),
    onSurfaceVariant = lerp(from.onSurfaceVariant, to.onSurfaceVariant, fraction),
    surfaceTint = lerp(from.surfaceTint, to.surfaceTint, fraction),
    inverseSurface = lerp(from.inverseSurface, to.inverseSurface, fraction),
    inverseOnSurface = lerp(from.inverseOnSurface, to.inverseOnSurface, fraction),
    error = lerp(from.error, to.error, fraction),
    onError = lerp(from.onError, to.onError, fraction),
    errorContainer = lerp(from.errorContainer, to.errorContainer, fraction),
    onErrorContainer = lerp(from.onErrorContainer, to.onErrorContainer, fraction),
    outline = lerp(from.outline, to.outline, fraction),
    outlineVariant = lerp(from.outlineVariant, to.outlineVariant, fraction),
    scrim = lerp(from.scrim, to.scrim, fraction),
    surfaceBright = lerp(from.surfaceBright, to.surfaceBright, fraction),
    surfaceDim = lerp(from.surfaceDim, to.surfaceDim, fraction),
    surfaceContainer = lerp(from.surfaceContainer, to.surfaceContainer, fraction),
    surfaceContainerHigh = lerp(from.surfaceContainerHigh, to.surfaceContainerHigh, fraction),
    surfaceContainerHighest = lerp(from.surfaceContainerHighest, to.surfaceContainerHighest, fraction),
    surfaceContainerLow = lerp(from.surfaceContainerLow, to.surfaceContainerLow, fraction),
    surfaceContainerLowest = lerp(from.surfaceContainerLowest, to.surfaceContainerLowest, fraction),
    primaryFixed = lerp(from.primaryFixed, to.primaryFixed, fraction),
    primaryFixedDim = lerp(from.primaryFixedDim, to.primaryFixedDim, fraction),
    onPrimaryFixed = lerp(from.onPrimaryFixed, to.onPrimaryFixed, fraction),
    onPrimaryFixedVariant = lerp(from.onPrimaryFixedVariant, to.onPrimaryFixedVariant, fraction),
    secondaryFixed = lerp(from.secondaryFixed, to.secondaryFixed, fraction),
    secondaryFixedDim = lerp(from.secondaryFixedDim, to.secondaryFixedDim, fraction),
    onSecondaryFixed = lerp(from.onSecondaryFixed, to.onSecondaryFixed, fraction),
    onSecondaryFixedVariant = lerp(from.onSecondaryFixedVariant, to.onSecondaryFixedVariant, fraction),
    tertiaryFixed = lerp(from.tertiaryFixed, to.tertiaryFixed, fraction),
    tertiaryFixedDim = lerp(from.tertiaryFixedDim, to.tertiaryFixedDim, fraction),
    onTertiaryFixed = lerp(from.onTertiaryFixed, to.onTertiaryFixed, fraction),
    onTertiaryFixedVariant = lerp(from.onTertiaryFixedVariant, to.onTertiaryFixedVariant, fraction),
  )
  // Interpolating dark text to light text can otherwise make it vanish against the moving surface.
  return mixed.copy(
    onPrimary = transitionInk(mixed.primary, mixed.onPrimary),
    onPrimaryContainer = transitionInk(mixed.primaryContainer, mixed.onPrimaryContainer),
    onSecondary = transitionInk(mixed.secondary, mixed.onSecondary),
    onSecondaryContainer = transitionInk(mixed.secondaryContainer, mixed.onSecondaryContainer),
    onTertiary = transitionInk(mixed.tertiary, mixed.onTertiary),
    onTertiaryContainer = transitionInk(mixed.tertiaryContainer, mixed.onTertiaryContainer),
    onBackground = transitionInk(mixed.background, mixed.onBackground),
    onSurface = transitionInk(mixed.surface, mixed.onSurface),
    onSurfaceVariant = transitionInk(mixed.surfaceVariant, mixed.onSurfaceVariant),
    inverseOnSurface = transitionInk(mixed.inverseSurface, mixed.inverseOnSurface),
    onError = transitionInk(mixed.error, mixed.onError),
    onErrorContainer = transitionInk(mixed.errorContainer, mixed.onErrorContainer),
  )

}

private fun transitionInk(background: Color, foreground: Color): Color {
  val contrast = (maxOf(background.luminance(), foreground.luminance()) + .05f) /
    (minOf(background.luminance(), foreground.luminance()) + .05f)
  return if (contrast >= 4.5f) foreground else background.readableInk()
}
