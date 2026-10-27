package live.mehiz.mpvkt.ui.player.controls.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import live.mehiz.mpvkt.ui.icons.Icons
import live.mehiz.mpvkt.ui.icons.Bookmarks
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.vivvvek.seeker.Segment
import `is`.xyz.mpv.Utils
import live.mehiz.mpvkt.ui.theme.spacing

@Composable
fun CurrentChapter(
  chapter: Segment,
  modifier: Modifier = Modifier,
  onClick: () -> Unit = {},
) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(50))
      .background(MaterialTheme.colorScheme.background.copy(alpha = 0.6F))
      .clickable(interactionSource = remember { MutableInteractionSource() }, indication = ripple(bounded = false, radius = 24.dp), onClick = onClick)
      .padding(horizontal = MaterialTheme.spacing.small, vertical = MaterialTheme.spacing.smaller),
  ) {
    AnimatedContent(
      targetState = chapter,
      transitionSpec = {
        if (targetState.start > initialState.start) {
          (slideInVertically { height -> height } + fadeIn())
            .togetherWith(slideOutVertically { height -> -height } + fadeOut())
        } else {
          (slideInVertically { height -> -height } + fadeIn())
            .togetherWith(slideOutVertically { height -> height } + fadeOut())
        }.using(
          SizeTransform(clip = true),
        )
      },
      label = "Chapter",
    ) { currentChapter ->
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)
      ) {
        Icon(
          imageVector = Icons.Bookmarks,
          contentDescription = null,
          modifier = Modifier
            .padding(end = MaterialTheme.spacing.extraSmall)
            .size(16.dp),
          tint = MaterialTheme.colorScheme.onBackground
        )
        Text(
          text = Utils.prettyTime(currentChapter.start.toInt()),
          fontWeight = FontWeight.ExtraBold,
          style = MaterialTheme.typography.bodyMedium,
          maxLines = 1,
          overflow = TextOverflow.Clip,
          color = MaterialTheme.colorScheme.tertiary,
        )
        currentChapter.name.let {
          Text(
            text = Typography.bullet.toString(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurface,
            overflow = TextOverflow.Clip,
          )
          Text(
            text = it,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            modifier = Modifier.weight(1f, fill = false),
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
          )
        }
      }
    }
  }
}
