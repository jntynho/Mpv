package live.mehiz.mpvkt.ui.utils

import androidx.compose.runtime.compositionLocalOf
import androidx.navigation3.runtime.NavBackStack

@Suppress("CompositionLocalAllowlist")
val LocalBackStack = compositionLocalOf<NavBackStack> {
  error("LocalBackStack not initialized!")
}

/** One committed back action shared by toolbar buttons and system gestures. */
val LocalNavigateBack = compositionLocalOf<() -> Unit> { error("Navigation back action not initialized") }
