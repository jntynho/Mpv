package live.mehiz.mpvkt.ui.icons

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource

/** Persisted names are independent of icon resource IDs and legacy style scopes. */
enum class IconStyle(@androidx.annotation.StringRes val titleRes: Int) {
  Outline(live.mehiz.mpvkt.R.string.mpv_icons_style_outline),
  Broken(live.mehiz.mpvkt.R.string.mpv_icons_style_broken),
}

internal val LocalIconStyle = staticCompositionLocalOf { IconStyle.Outline }

interface IconScope {
  val mirrored: Boolean get() = false
}

object Icons : IconScope {
  object AutoMirrored : IconScope {
    override val mirrored = true
  }
}

@Composable
internal fun appIcon(key: IconKey, mirrored: Boolean = false): ImageVector =
  ImageVector.vectorResource(key.drawable(LocalIconStyle.current, mirrored))
