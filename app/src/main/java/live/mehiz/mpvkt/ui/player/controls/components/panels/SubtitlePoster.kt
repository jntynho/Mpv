package live.mehiz.mpvkt.ui.player.controls.components.panels

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import live.mehiz.mpvkt.subtitles.*
import live.mehiz.mpvkt.ui.icons.*

internal val LocalSubtitlePosters = staticCompositionLocalOf { SubtitlePosters.Shared }

@Composable
internal fun SubtitlePoster(url: String?, reduceMotion: Boolean, modifier: Modifier = Modifier) {
  val loader = LocalSubtitlePosters.current
  key(url, loader) {
    var retry by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(loader.peek(url) == null && url != null) }
    val bitmap by
      produceState(loader.peek(url), url, loader, retry) {
        loading = true
        try {
          value = loader.load(url)
        } finally {
          loading = false
        }
      }
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(bitmap) { revealed = bitmap != null }
    val alpha by
      animateFloatAsState(
        if (revealed) 1f else 0f,
        tween(if (reduceMotion) 0 else 220),
        label = "subtitle_poster",
      )
    Box(
      modifier
        .defaultMinSize(minWidth = 76.dp, minHeight = 114.dp)
        .clip(RoundedCornerShape(6.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
      contentAlignment = Alignment.Center,
    ) {
      if (bitmap != null || url == null)
        Icon(
          Icons.Movie,
          null,
          Modifier.size(24.dp),
          tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .5f),
        )
      if (bitmap == null && url != null) {
        if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        else
          IconButton(onClick = { retry++ }) {
            Icon(
              Icons.Refresh,
              androidx.compose.ui.res.stringResource(live.mehiz.mpvkt.R.string.panel_retry),
            )
          }
      }
      bitmap?.let {
        Image(
          it.asImageBitmap(),
          null,
          Modifier.fillMaxSize().alpha(alpha),
          contentScale = ContentScale.Crop,
        )
      }
    }
  }
}
