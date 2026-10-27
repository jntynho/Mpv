package live.mehiz.mpvkt.ui.player.controls

import androidx.compose.foundation.layout.Row
import live.mehiz.mpvkt.ui.icons.Icons
import live.mehiz.mpvkt.ui.icons.Audiotrack
import live.mehiz.mpvkt.ui.icons.Bookmarks
import live.mehiz.mpvkt.ui.icons.MoreVert
import live.mehiz.mpvkt.ui.icons.Subtitles
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import live.mehiz.mpvkt.ui.player.Decoder
import live.mehiz.mpvkt.ui.player.controls.components.ControlsButton

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

  modifier: Modifier = Modifier
) {
  Row(
    modifier,
    verticalAlignment = Alignment.CenterVertically
  ) {
    ControlsButton(
      decoder.title,
      onClick = onDecoderClick,
      onLongClick = onDecoderLongClick,
    )
    if (isChaptersVisible) {
      ControlsButton(
        Icons.Bookmarks,
        onClick = onChaptersClick,
      )
    }
    ControlsButton(
      Icons.Subtitles,
      onClick = onSubtitlesClick,
      onLongClick = onSubtitlesLongClick,
    )
    ControlsButton(
      Icons.Audiotrack,
      onClick = onAudioClick,
      onLongClick = onAudioLongClick,
    )
    ControlsButton(
      Icons.MoreVert,
      onClick = onMoreClick,
      onLongClick = onMoreLongClick,
    )
  }
}
