package live.mehiz.mpvkt.ui.player.controls

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.vivvvek.seeker.Segment
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.ui.icons.*
import live.mehiz.mpvkt.ui.player.Decoder
import live.mehiz.mpvkt.ui.player.controls.components.ControlsButton
import live.mehiz.mpvkt.ui.player.controls.components.CurrentChapter

@Composable
internal fun LandscapeControl.icon(): ImageVector =
  when (this) {
    LandscapeControl.Decoder -> Icons.Memory
    LandscapeControl.Chapters,
    LandscapeControl.ChapterTitle -> Icons.Bookmarks
    LandscapeControl.Subtitles -> Icons.Subtitles
    LandscapeControl.Audio -> Icons.Audiotrack
    LandscapeControl.More -> Icons.MoreVert
    LandscapeControl.Custom -> Icons.Terminal
    LandscapeControl.Pip -> Icons.PictureInPictureAlt
    LandscapeControl.Aspect -> Icons.AspectRatio
    LandscapeControl.Lock -> Icons.LockOpen
    LandscapeControl.Rotation -> Icons.ScreenRotation
    LandscapeControl.Speed -> Icons.Timer
  }

@Composable
internal fun LandscapeControlStrip(
  buttons: List<LandscapeControl>,
  decoder: Decoder,
  speed: Float,
  chapter: Segment?,
  customTitle: String?,
  pipAvailable: Boolean,
  reduceMotion: Boolean,
  onClick: (LandscapeControl) -> Unit,
  onLongClick: (LandscapeControl) -> Unit,
) {
  val decoderWidth = rememberDecoderButtonWidth()
  val maxWidth =
    with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() * .48f }
      .coerceAtLeast(1.dp)
  Row(
    Modifier.widthIn(max = maxWidth).horizontalScroll(rememberScrollState()),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    buttons.forEach { button ->
      when (button) {
        LandscapeControl.Decoder ->
          ControlsButton(
            decoder.title,
            modifier = Modifier.width(decoderWidth),
            onClick = { onClick(button) },
            onLongClick = { onLongClick(button) },
            animateText = true,
            reduceMotion = reduceMotion,
          )
        LandscapeControl.Speed ->
          ControlsButton(
            stringResource(R.string.player_speed, speed),
            onClick = { onClick(button) },
            onLongClick = { onLongClick(button) },
          )
        LandscapeControl.ChapterTitle ->
          if (chapter != null) CurrentChapter(chapter, onClick = { onClick(button) })
        LandscapeControl.Chapters ->
          if (chapter != null)
            ControlsButton(
              button.icon(),
              { onClick(button) },
              title = stringResource(button.titleRes),
            )
        LandscapeControl.Custom ->
          if (customTitle != null)
            ControlsButton(
              customTitle,
              onClick = { onClick(button) },
              onLongClick = { onLongClick(button) },
            )
        LandscapeControl.Pip ->
          if (pipAvailable)
            ControlsButton(
              button.icon(),
              { onClick(button) },
              title = stringResource(button.titleRes),
            )
        else ->
          ControlsButton(
            button.icon(),
            { onClick(button) },
            onLongClick = { onLongClick(button) },
            title = stringResource(button.titleRes),
          )
      }
    }
  }
}
