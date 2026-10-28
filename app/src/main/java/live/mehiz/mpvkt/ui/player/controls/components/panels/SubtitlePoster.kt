package live.mehiz.mpvkt.ui.player.controls.components.panels

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.subtitles.*
import live.mehiz.mpvkt.ui.icons.*

internal val LocalSubtitlePosters = staticCompositionLocalOf { SubtitlePosters.Shared }

@Composable
internal fun SubtitlePoster(url: String?, reduceMotion: Boolean, modifier: Modifier = Modifier) {
  val loader = LocalSubtitlePosters.current
  key(url, loader) {
    val bitmap by produceState(loader.peek(url), url, loader) { value = loader.load(url) }
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
        .size(76.dp, 114.dp)
        .clip(RoundedCornerShape(6.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        Icons.Movie,
        null,
        Modifier.size(24.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .5f),
      )
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

@Composable
internal fun SubtitleMediaRow(media: MediaMatch, reduceMotion: Boolean, select: () -> Unit) {
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  Row(
    Modifier.fillMaxWidth()
      .padding(horizontal = 12.dp, vertical = 4.dp)
      .clip(RoundedCornerShape(18.dp))
      .clickable(onClick = select)
      .padding(8.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    SubtitlePoster(media.posterUrl, reduceMotion)
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(
        media.title,
        style = MaterialTheme.typography.titleSmall,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        listOf(
            if (media.television) stringResource(R.string.subtitle_series)
            else stringResource(R.string.subtitle_movie),
            media.year,
          )
          .filter(String::isNotBlank)
          .joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Icon(
      Icons.ArrowDropDown,
      null,
      Modifier.size(18.dp).graphicsLayer { rotationZ = if (rtl) 90f else -90f },
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
