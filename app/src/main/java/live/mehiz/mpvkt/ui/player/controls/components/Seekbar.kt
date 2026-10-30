package live.mehiz.mpvkt.ui.player.controls.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.vivvvek.seeker.Segment
import `is`.xyz.mpv.Utils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import live.mehiz.mpvkt.ui.player.controls.LocalPlayerButtonsClickEvent
import live.mehiz.mpvkt.ui.theme.spacing

@Composable
fun SeekbarWithTimers(
  position: Float,
  duration: Float,
  remaining: Float,
  readAheadValue: Float,
  onValueChange: (Float) -> Unit,
  onValueChangeFinished: () -> Unit,
  timersInverted: Pair<Boolean, Boolean>,
  positionTimerOnClick: () -> Unit,
  durationTimerOnCLick: () -> Unit,
  chapters: ImmutableList<Segment>,
  modifier: Modifier = Modifier,
  style: SeekbarStyle = SeekbarStyle.Normal,
  paused: Boolean = true,
  reduceMotion: Boolean = false,
  mediaKey: Long = 0L,
  onScrubbingChanged: (Boolean) -> Unit = {},
) {
  val clickEvent = LocalPlayerButtonsClickEvent.current
  val scrub = remember(mediaKey) { ScrubSession() }
  val scrubChanged by rememberUpdatedState(onScrubbingChanged)
  val displayed = scrub.draft ?: position
  LaunchedEffect(position, scrub.awaiting) { scrub.acknowledge(position) }
  LaunchedEffect(scrub.revision, scrub.awaiting) {
    if (scrub.awaiting) {
      delay(1200)
      scrub.cancel()
    }
  }
  DisposableEffect(mediaKey) {
    onDispose {
      scrub.cancel()
      scrubChanged(false)
    }
  }

  Row(
    modifier = modifier.height(48.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
  ) {
    VideoTimer(
      value = displayed,
      timersInverted.first,
      onClick = {
        clickEvent()
        positionTimerOnClick()
      },
      modifier = Modifier.width(92.dp),
    )
    StyledSeeker(
      displayed,
      duration,
      readAheadValue,
      chapters,
      {
        scrub.preview(it, duration)
        scrubChanged(scrub.dragging)
      },
      {
        val target = scrub.release()
        if (target != null) {
          onValueChange(target)
          onValueChangeFinished()
        }
        scrubChanged(false)
      },
      Modifier.weight(1f),
      style,
      paused,
      reduceMotion,
      inputKey = mediaKey,
      onCancel = {
        scrub.cancel()
        scrubChanged(false)
      },
    )
    VideoTimer(
      value =
        if (timersInverted.second)
          -(if (scrub.draft != null) (duration - displayed).coerceAtLeast(0f) else remaining)
        else duration,
      isInverted = timersInverted.second,
      onClick = {
        clickEvent()
        durationTimerOnCLick()
      },
      modifier = Modifier.width(92.dp),
    )
  }
}

@Composable
fun VideoTimer(
  value: Float,
  isInverted: Boolean,
  modifier: Modifier = Modifier,
  onClick: () -> Unit = {},
) {
  val interactionSource = remember { MutableInteractionSource() }
  Text(
    modifier =
      modifier
        .fillMaxHeight()
        .clickable(
          interactionSource = interactionSource,
          indication = ripple(bounded = false, radius = 24.dp),
          onClick = onClick,
        )
        .wrapContentHeight(Alignment.CenterVertically),
    text = Utils.prettyTime(value.toInt(), isInverted),
    color = Color.White,
    textAlign = TextAlign.Center,
  )
}

@Preview
@Composable
private fun PreviewSeekBar() {
  SeekbarWithTimers(
    5f,
    20f,
    15f,
    4f,
    {},
    {},
    Pair(false, true),
    {},
    {},
    persistentListOf<Segment>(),
  )
}
