package live.mehiz.mpvkt.presentation.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import live.mehiz.mpvkt.R

@Composable
fun ConfirmDialog(
  title: String,
  subtitle: String,
  onConfirm: () -> Unit,
  onCancel: () -> Unit,
  modifier: Modifier = Modifier,
) {
  AppDialog(
    onDismissRequest = onCancel,
    modifier = modifier,
    title = { Text(title) },
    text = { Text(subtitle) },
    confirmButton = { DialogButton(onConfirm) { Text(stringResource(R.string.generic_confirm)) } },
    dismissButton = { DialogButton(onCancel) { Text(stringResource(R.string.generic_cancel)) } },
  )
}
