package live.mehiz.mpvkt.ui.player.controls.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape

internal val PanelFieldHeight = live.mehiz.mpvkt.presentation.components.AppFieldHeight
internal val PanelFieldShape = live.mehiz.mpvkt.presentation.components.AppFieldShape

/** Shared measured geometry for text inputs, language pickers and episode controls. */
@Composable
internal fun PanelTextField(
  value: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  readOnly: Boolean = false,
  isError: Boolean = false,
  visualTransformation: androidx.compose.ui.text.input.VisualTransformation =
    androidx.compose.ui.text.input.VisualTransformation.None,
  label: (@Composable () -> Unit)? = null,
  singleLine: Boolean = true,
  leadingIcon: (@Composable () -> Unit)? = null,
  trailingIcon: (@Composable () -> Unit)? = null,
  shape: Shape = PanelFieldShape,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
  keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
  live.mehiz.mpvkt.presentation.components.AppTextField(
    value = value,
    onValueChange = onValueChange,
    modifier = modifier,
    enabled = enabled,
    readOnly = readOnly,
    isError = isError,
    visualTransformation = visualTransformation,
    label = label,
    singleLine = singleLine,
    leadingIcon = leadingIcon,
    trailingIcon = trailingIcon,
    shape = shape,
    keyboardOptions = keyboardOptions,
    keyboardActions = keyboardActions,
  )
}
