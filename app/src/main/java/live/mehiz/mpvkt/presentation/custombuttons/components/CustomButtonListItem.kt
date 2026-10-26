package live.mehiz.mpvkt.presentation.custombuttons.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import live.mehiz.mpvkt.ui.icons.Icons
import live.mehiz.mpvkt.ui.icons.outlined.ArrowDropDown
import live.mehiz.mpvkt.ui.icons.outlined.ArrowDropUp
import live.mehiz.mpvkt.ui.icons.outlined.Code
import live.mehiz.mpvkt.ui.icons.outlined.Delete
import live.mehiz.mpvkt.ui.icons.outlined.Edit
import live.mehiz.mpvkt.ui.icons.outlined.Star
import live.mehiz.mpvkt.ui.icons.outlined.StarOutline
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.database.entities.CustomButtonEntity
import live.mehiz.mpvkt.ui.theme.spacing

@Composable
fun CustomButtonListItem(
  customButton: CustomButtonEntity,
  isPrimary: Boolean,
  canMoveUp: Boolean,
  canMoveDown: Boolean,
  onMoveUp: (CustomButtonEntity) -> Unit,
  onMoveDown: (CustomButtonEntity) -> Unit,
  onRename: () -> Unit,
  onDelete: () -> Unit,
  onTogglePrimary: () -> Unit,
  modifier: Modifier = Modifier,
) {
  ElevatedCard(
    modifier = modifier,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clickable { onRename() }
        .padding(
          start = MaterialTheme.spacing.medium,
          top = MaterialTheme.spacing.medium,
          end = MaterialTheme.spacing.medium,
        ),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(imageVector = Icons.Outlined.Code, contentDescription = null)
      Text(
        text = customButton.title,
        modifier = Modifier
          .padding(start = MaterialTheme.spacing.medium),
      )
    }
    Text(
      text = customButton.content,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
      maxLines = 3,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier
        .padding(
          top = MaterialTheme.spacing.smaller,
          start = MaterialTheme.spacing.medium
        ),
    )
    Row {
      IconButton(
        onClick = { onMoveUp(customButton) },
        enabled = canMoveUp,
      ) {
        Icon(imageVector = Icons.Outlined.ArrowDropUp, contentDescription = null)
      }
      IconButton(
        onClick = { onMoveDown(customButton) },
        enabled = canMoveDown,
      ) {
        Icon(imageVector = Icons.Outlined.ArrowDropDown, contentDescription = null)
      }
      Spacer(modifier = Modifier.weight(1f))

      val starColor = if (isPrimary) Color(0xFFFDD835) else MaterialTheme.colorScheme.onSurfaceVariant
      val starImage = if (isPrimary) Icons.Outlined.Star else Icons.Outlined.StarOutline
      IconToggleButton(checked = isPrimary, onCheckedChange = { onTogglePrimary() }) {
        Icon(
          imageVector = starImage,
          tint = starColor,
          contentDescription = null,
        )
      }
      IconButton(onClick = onRename) {
        Icon(
          imageVector = Icons.Outlined.Edit,
          contentDescription = stringResource(id = R.string.pref_custom_button_edit_button),
        )
      }
      IconButton(onClick = onDelete) {
        Icon(
          imageVector = Icons.Outlined.Delete,
          contentDescription = stringResource(id = R.string.pref_custom_button_delete_button),
        )
      }
    }
  }
}
