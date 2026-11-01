package live.mehiz.mpvkt.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import live.mehiz.mpvkt.R

/** Shared bare launcher artwork, without a launcher tile or system mask. */
@Composable
internal fun AppBrandMark(modifier: Modifier = Modifier, description: String? = null) {
  Image(
    painterResource(R.drawable.mpv_brand_mark),
    description,
    modifier,
    colorFilter =
      if (MaterialTheme.colorScheme.surface.luminance() > .5f)
        ColorFilter.tint(MaterialTheme.colorScheme.primary)
      else null,
  )
}
