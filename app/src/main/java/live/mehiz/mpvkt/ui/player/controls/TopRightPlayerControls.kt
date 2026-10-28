package live.mehiz.mpvkt.ui.player.controls

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import live.mehiz.mpvkt.ui.icons.Audiotrack
import live.mehiz.mpvkt.ui.icons.Bookmarks
import live.mehiz.mpvkt.ui.icons.Icons
import live.mehiz.mpvkt.ui.icons.MoreVert
import live.mehiz.mpvkt.ui.icons.Subtitles
import live.mehiz.mpvkt.ui.player.Decoder
import live.mehiz.mpvkt.ui.player.controls.components.ControlsButton
import live.mehiz.mpvkt.ui.theme.spacing

@Composable
fun TopRightPlayerControls(
  // decoder
  decoder: Decoder,
  onDecoderClick: () -> Unit,
  onDecoderLongClick: () -> Unit,
  // chapters
  isChaptersVisible: Boolean,
  onChaptersClick: () -> Unit,
  // subtitles
  onSubtitlesClick: () -> Unit,
  onSubtitlesLongClick: () -> Unit,
  // audio
  onAudioClick: () -> Unit,
  onAudioLongClick: () -> Unit,
  // more
  onMoreClick: () -> Unit,
  onMoreLongClick: () -> Unit,
  reduceMotion: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val measurer = rememberTextMeasurer()
  val style = MaterialTheme.typography.bodyMedium
  val labelWidth =
    remember(measurer, style) {
      Decoder.entries.maxOf { measurer.measure(AnnotatedString(it.title), style).size.width }
    }
  val decoderWidth =
    (with(LocalDensity.current) { labelWidth.toDp() } + MaterialTheme.spacing.medium * 2)
      .coerceAtLeast(48.dp)
  Row(modifier, verticalAlignment = Alignment.CenterVertically) {
    ControlsButton(
      decoder.title,
      modifier = Modifier.width(decoderWidth),
      animateText = true,
      reduceMotion = reduceMotion,
      onClick = onDecoderClick,
      onLongClick = onDecoderLongClick,
    )
    if (isChaptersVisible) {
      ControlsButton(Icons.Bookmarks, onClick = onChaptersClick)
    }
    ControlsButton(Icons.Subtitles, onClick = onSubtitlesClick, onLongClick = onSubtitlesLongClick)
    ControlsButton(Icons.Audiotrack, onClick = onAudioClick, onLongClick = onAudioLongClick)
    ControlsButton(Icons.MoreVert, onClick = onMoreClick, onLongClick = onMoreLongClick)
  }
}
