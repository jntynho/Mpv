package live.mehiz.mpvkt.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import live.mehiz.mpvkt.R

@Composable
internal fun LibraryFolderItem(
  entry: LibraryFolder,
  display: LibraryDisplayOptions,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  val context = LocalContext.current
  val fields = display.fields
  val values = buildList {
    if (fields.has(LibraryField.Path) && entry.path.isNotBlank()) add(entry.path)
    if (fields.has(LibraryField.Count))
      add(context.getString(R.string.mpv_media_count, entry.count))
    if (fields.has(LibraryField.TotalDuration)) add(formatDuration(entry.duration))
    if (fields.has(LibraryField.Size)) add(formatBytes(entry.sizeBytes))
    if (fields.has(LibraryField.Date) && entry.dateAdded > 0) add(formatDate(entry.dateAdded))
  }
  val text: @Composable ColumnScope.() -> Unit = {
    Text(
      entry.name,
      maxLines = if (fields.has(LibraryField.FullName)) Int.MAX_VALUE else 2,
      overflow = TextOverflow.Ellipsis,
      style = MaterialTheme.typography.titleMedium,
      textAlign =
        if (display.grid && fields.has(LibraryField.CenterTitles))
          androidx.compose.ui.text.style.TextAlign.Center
        else androidx.compose.ui.text.style.TextAlign.Start,
    )
    LibraryMetadataRow(values)
  }
  val base =
    modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .clickable(onClick = onClick)
      .padding(vertical = 12.dp)
  if (display.grid)
    Column(
      base.padding(horizontal = 8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      horizontalAlignment =
        if (fields.has(LibraryField.CenterTitles)) Alignment.CenterHorizontally else Alignment.Start,
    ) {
      Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { LibraryFolderArtwork() }
      text()
    }
  else
    Row(
      base,
      horizontalArrangement = Arrangement.spacedBy(16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      LibraryFolderArtwork()
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), content = text)
    }
}

@Composable
internal fun LibraryVideoItem(
  video: LibraryVideo,
  display: LibraryDisplayOptions,
  lastPosition: Int = 0,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  val context = LocalContext.current
  val fields = display.fields
  val detailsNeeded = fields.any { it in setOf("Resolution", "Framerate", "Subtitles", "Codec") }
  val details by
    produceState<VideoDetails?>(null, video.uri, video.revision, detailsNeeded) {
      if (detailsNeeded) value = VideoDetailsLoader.load(context.applicationContext, video)
    }
  val progress =
    if (video.duration > 0) (lastPosition * 1000f / video.duration).coerceIn(0f, 1f) else 0f
  val values = buildList {
    if (fields.has(LibraryField.Path) && video.path.isNotBlank()) add(video.path)
    if (fields.has(LibraryField.Duration)) add(formatDuration(video.duration))
    if (fields.has(LibraryField.Size)) add(formatBytes(video.sizeBytes))
    if (fields.has(LibraryField.Date) && video.dateAdded > 0) add(formatDate(video.dateAdded))
    if (fields.has(LibraryField.Resolution)) {
      val w = details?.width ?: video.width
      val h = details?.height ?: video.height
      add(
        if (w > 0 && h > 0) "$w × $h" else context.getString(R.string.mpv_field_resolution) + " —"
      )
    }
    if (fields.has(LibraryField.Framerate))
      add(details?.fps?.let { "%.2f fps".format(java.util.Locale.ROOT, it) } ?: "fps —")
    if (fields.has(LibraryField.Subtitles))
      add(
        context.getString(R.string.mpv_field_subtitles) +
          when (details?.subtitles) {
            true -> " ✓"
            false -> " —"
            null -> " …"
          }
      )
    if (fields.has(LibraryField.Codec))
      add(
        details?.let { (if (it.hardware) "HW · " else "SW · ") + it.codec.uppercase() }
          ?: video.mimeType.removePrefix("video/").ifBlank { "Codec —" }
      )
    if (fields.has(LibraryField.Progress)) add("${(progress*100).toInt()}%")
  }
  val title =
    if (fields.has(LibraryField.Extension)) video.name
    else video.name.substringBeforeLast('.', video.name).ifBlank { video.name }
  val info: @Composable ColumnScope.() -> Unit = {
    Text(
      title,
      maxLines = if (fields.has(LibraryField.FullName)) Int.MAX_VALUE else 2,
      overflow = TextOverflow.Ellipsis,
      style = MaterialTheme.typography.titleSmall,
      textAlign =
        if (display.grid && fields.has(LibraryField.CenterTitles))
          androidx.compose.ui.text.style.TextAlign.Center
        else androidx.compose.ui.text.style.TextAlign.Start,
    )
    LibraryMetadataRow(values)
    if (fields.has(LibraryField.Progress))
      LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth().height(2.dp),
      )
  }
  val base =
    modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .clickable(onClick = onClick)
      .padding(vertical = 8.dp)
  if (display.grid)
    Column(
      base,
      verticalArrangement = Arrangement.spacedBy(8.dp),
      horizontalAlignment =
        if (fields.has(LibraryField.CenterTitles)) Alignment.CenterHorizontally else Alignment.Start,
    ) {
      if (fields.has(LibraryField.Thumbnails))
        VideoCover(video, fields, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
      info()
    }
  else
    Row(
      base,
      horizontalArrangement = Arrangement.spacedBy(14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (fields.has(LibraryField.Thumbnails))
        VideoCover(video, fields, Modifier.width(136.dp).height(88.dp))
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), content = info)
    }
}

@Composable
private fun VideoCover(video: LibraryVideo, fields: Set<String>, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val coverRadius = 12.dp
  val badgeInset = 4.dp
  Box(modifier.clip(RoundedCornerShape(coverRadius))) {
    VideoThumbnail(context, video.uri, Modifier.fillMaxSize(), video.revision)
    if (fields.has(LibraryField.Duration))
      Text(
        formatDuration(video.duration),
        Modifier.align(Alignment.BottomEnd)
          .padding(badgeInset)
          .clip(RoundedCornerShape(coverRadius - badgeInset))
          .background(Color.Black.copy(alpha = .75f))
          .padding(horizontal = 4.dp, vertical = 2.dp),
        color = Color.White,
        style = MaterialTheme.typography.labelSmall,
      )
  }
}
