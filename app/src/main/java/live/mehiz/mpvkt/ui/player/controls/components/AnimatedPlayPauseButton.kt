package live.mehiz.mpvkt.ui.player.controls.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import kotlinx.coroutines.delay
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.ui.icons.*

@Composable
fun AnimatedPlayPauseButton(
  paused: Boolean, loading: Boolean, pending: Boolean,
  enabled: Boolean, reduceMotion: Boolean, onClick: () -> Unit,
) {
  var showPending by remember { mutableStateOf(false) }
  LaunchedEffect(pending) { showPending = false; if (pending) { delay(120); showPending = true } }
  val actionLabel = stringResource(if (paused) R.string.notification_play else R.string.notification_pause)
  // The click target stays in one place and is never duplicated by AnimatedContent.
  Box(Modifier.size(96.dp).clip(CircleShape).semantics { contentDescription = actionLabel }.clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
    AnimatedContent(targetState = paused, label = "play_pause_icon", transitionSpec = {
      if (reduceMotion) fadeIn(tween(80)) togetherWith fadeOut(tween(80))
      else (fadeIn(tween(160)) + scaleIn(tween(160), initialScale = .94f)) togetherWith
        (fadeOut(tween(110)) + scaleOut(tween(110), targetScale = .94f))
    }) { isPaused ->
      Icon(if (isPaused) Icons.Play else Icons.Pause,
        null,
        Modifier.size(64.dp), tint = Color.White)
    }
    if (loading || showPending) CircularProgressIndicator(Modifier.size(88.dp), color = Color.White.copy(alpha = .8f), strokeWidth = 2.dp)
  }
}
