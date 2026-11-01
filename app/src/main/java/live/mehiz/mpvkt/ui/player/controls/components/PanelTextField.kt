package live.mehiz.mpvkt.ui.player.controls.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import live.mehiz.mpvkt.presentation.components.AppFieldHeight
import live.mehiz.mpvkt.presentation.components.AppFieldShape
import live.mehiz.mpvkt.presentation.components.AppTextField

internal val PanelFieldHeight = AppFieldHeight
internal val PanelFieldShape = AppFieldShape

/** Shared measured geometry for text inputs, language pickers and episode controls. */
@Composable
internal fun PanelTextField(
  value: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  readOnly: Boolean = false,
  label: (@Composable () -> Unit)? = null,
  singleLine: Boolean = true,
  leadingIcon: (@Composable () -> Unit)? = null,
  trailingIcon: (@Composable () -> Unit)? = null,
  shape: Shape = PanelFieldShape,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
  keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
  AppTextField(
    value = value,
    onValueChange = onValueChange,
    modifier = modifier,
    enabled = enabled,
    readOnly = readOnly,
    label = label,
    singleLine = singleLine,
    leadingIcon = leadingIcon,
    trailingIcon = trailingIcon,
    shape = shape,
    keyboardOptions = keyboardOptions,
    keyboardActions = keyboardActions,
  )
}
