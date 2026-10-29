package live.mehiz.mpvkt.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp
import live.mehiz.mpvkt.R

internal enum class LibraryField(
  @StringRes val titleRes: Int,
  val folder: Boolean = false,
  val video: Boolean = false,
) {
  FullName(R.string.mpv_field_full_name, true, true),
  Path(R.string.mpv_field_path, true, true),
  Count(R.string.mpv_field_count, true),
  TotalDuration(R.string.mpv_field_total_duration, true),
  Size(R.string.mpv_field_size, true, true),
  Date(R.string.mpv_field_date, true, true),
  Thumbnails(R.string.mpv_field_thumbnails, video = true),
  Extension(R.string.mpv_field_extension, video = true),
  Duration(R.string.mpv_field_duration, video = true),
  Resolution(R.string.mpv_field_resolution, video = true),
  Framerate(R.string.mpv_field_framerate, video = true),
  Subtitles(R.string.mpv_field_subtitles, video = true),
  Codec(R.string.mpv_field_codec, video = true),
  Progress(R.string.mpv_field_progress, video = true),
  CenterTitles(R.string.mpv_field_center, true, true),
}

/** Offscreen compositing masks this row alone; the parent surface never becomes translucent. */
@Composable
internal fun FadingHorizontalRow(
  modifier: Modifier = Modifier,
  content: @Composable RowScope.() -> Unit,
) {
  val state = rememberScrollState()
  val rtl =
    androidx.compose.ui.platform.LocalLayoutDirection.current ==
      androidx.compose.ui.unit.LayoutDirection.Rtl
  Row(
    modifier
      .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
      .drawWithContent {
        drawContent()
        val edge = (16.dp.toPx() / size.width.coerceAtLeast(1f)).coerceAtMost(.45f)
        drawRect(
          Brush.horizontalGradient(
            0f to
              if (if (rtl) state.canScrollForward else state.canScrollBackward) Color.Transparent
              else Color.Black,
            edge to Color.Black,
            (1f - edge) to Color.Black,
            1f to
              if (if (rtl) state.canScrollBackward else state.canScrollForward) Color.Transparent
              else Color.Black,
          ),
          blendMode = BlendMode.DstIn,
        )
      }
      .horizontalScroll(state),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    content = content,
  )
}

@Composable
internal fun LibraryMetadataRow(
  values: List<String>,
  modifier: Modifier = Modifier,
  centered: Boolean = false,
) {
  if (values.isEmpty()) return
  BoxWithConstraints(modifier.fillMaxWidth()) {
    val viewport = maxWidth
    FadingHorizontalRow(Modifier.fillMaxWidth()) {
      Row(
        Modifier.widthIn(min = viewport),
        horizontalArrangement =
          Arrangement.spacedBy(
            8.dp,
            if (centered) androidx.compose.ui.Alignment.CenterHorizontally
            else androidx.compose.ui.Alignment.Start,
          ),
      ) {
        values.forEach { value ->
          Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = .65f),
          ) {
            Text(
              value,
              Modifier.widthIn(max = 260.dp).padding(horizontal = 8.dp, vertical = 4.dp),
              maxLines = 1,
              overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}

internal fun Set<String>.has(field: LibraryField) = field.name in this

internal fun formatBytes(bytes: Long): String {
  val safe = bytes.coerceAtLeast(0)
  return when {
    safe >= 1024L * 1024 * 1024 ->
      "%.1f GB".format(java.util.Locale.ROOT, safe / (1024.0 * 1024 * 1024))
    safe >= 1024 * 1024 -> "%.1f MB".format(java.util.Locale.ROOT, safe / (1024.0 * 1024))
    safe >= 1024 -> "%.1f KB".format(java.util.Locale.ROOT, safe / 1024.0)
    else -> "$safe B"
  }
}

internal fun formatDate(seconds: Long) =
  java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT)
    .format(java.util.Date(seconds.coerceAtLeast(0) * 1000))
