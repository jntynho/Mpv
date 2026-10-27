package live.mehiz.mpvkt.ui.player.controls

import android.annotation.SuppressLint
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import live.mehiz.mpvkt.ui.icons.Icons
import live.mehiz.mpvkt.ui.icons.AspectRatio
import live.mehiz.mpvkt.ui.icons.PictureInPictureAlt
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import live.mehiz.mpvkt.database.entities.CustomButtonEntity
import live.mehiz.mpvkt.ui.player.controls.components.ControlsButton
import live.mehiz.mpvkt.ui.player.execute
import live.mehiz.mpvkt.ui.player.executeLongClick
import live.mehiz.mpvkt.ui.theme.spacing

@OptIn(ExperimentalFoundationApi::class)
@SuppressLint("NewApi")
@Composable
fun BottomRightPlayerControls(
  customButton: CustomButtonEntity?,
  customButtonTitle: String,
  isPipAvailable: Boolean,
  onAspectClick: () -> Unit,
  onPipClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(modifier) {
    if (customButton != null) {
      ControlsButton(text = customButtonTitle, onClick = customButton::execute,
        onLongClick = customButton::executeLongClick)
    }

    if (isPipAvailable) {
      ControlsButton(
        Icons.PictureInPictureAlt,
        onClick = onPipClick,
      )
    }

    ControlsButton(
      Icons.AspectRatio,
      onClick = onAspectClick,
    )
  }
}
