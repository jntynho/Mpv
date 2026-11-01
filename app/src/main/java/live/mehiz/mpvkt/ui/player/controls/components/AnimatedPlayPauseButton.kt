package live.mehiz.mpvkt.ui.player.controls.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.ui.icons.*

/** Original Iconsax artwork in both styles, two draw layers and one permanent accessible target. */
@Composable
fun AnimatedPlayPauseButton(
  paused: Boolean,
  pending: Boolean,
  enabled: Boolean,
  reduceMotion: Boolean,
  onClick: () -> Unit,
  pendingPauseTarget: Boolean? = null,
) {
  val actionLabel =
    stringResource(
      if (pending && pendingPauseTarget != null) {
        if (pendingPauseTarget) R.string.mpv_pausing else R.string.mpv_resuming
      } else if (paused) R.string.notification_play else R.string.notification_pause
    )
  val progress by
    animateFloatAsState(
      if (paused) 0f else 1f,
      tween(if (reduceMotion) 0 else 220, easing = FastOutSlowInEasing),
      label = "play_pause_iconsax",
    )
  Box(
    Modifier.size(96.dp)
      .clip(CircleShape)
      .semantics { contentDescription = actionLabel }
      .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      Icons.Play,
      null,
      Modifier.size(64.dp).graphicsLayer {
        alpha = 1f - progress
        scaleX = .86f + .14f * (1f - progress)
        scaleY = scaleX
      },
      tint = Color.White,
    )
    Icon(
      Icons.Pause,
      null,
      Modifier.size(64.dp).graphicsLayer {
        alpha = progress
        scaleX = .86f + .14f * progress
        scaleY = scaleX
      },
      tint = Color.White,
    )
  }
}
