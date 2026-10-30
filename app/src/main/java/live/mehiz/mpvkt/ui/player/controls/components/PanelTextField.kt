package live.mehiz.mpvkt.ui.player.controls.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp

internal val PanelFieldHeight = 48.dp
internal val PanelFieldShape = RoundedCornerShape(16.dp)

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
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()
  val colors = MaterialTheme.colorScheme
  BasicTextField(
    value,
    onValueChange,
    modifier.fillMaxWidth().heightIn(min = PanelFieldHeight),
    enabled = enabled,
    readOnly = readOnly,
    singleLine = singleLine,
    visualTransformation = visualTransformation,
    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
    cursorBrush = SolidColor(colors.primary),
    interactionSource = interaction,
    keyboardOptions = keyboardOptions,
    keyboardActions = keyboardActions,
    decorationBox = { input ->
      Row(
        Modifier.fillMaxWidth()
          .heightIn(min = PanelFieldHeight)
          .background(colors.surfaceContainerHigh.copy(alpha = .4f), shape)
          .border(
            1.dp,
            if (isError) colors.error
            else if (focused) colors.primary else colors.outlineVariant.copy(alpha = .65f),
            shape,
          )
          .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        leadingIcon?.invoke()
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
          if (label != null && value.isNotEmpty())
            CompositionLocalProvider(LocalContentColor provides colors.onSurfaceVariant) {
              ProvideTextStyle(MaterialTheme.typography.labelSmall) { label() }
            }
          Box {
            if (value.isEmpty() && label != null)
              CompositionLocalProvider(LocalContentColor provides colors.onSurfaceVariant) {
                ProvideTextStyle(MaterialTheme.typography.bodyMedium) { label() }
              }
            input()
          }
        }
        trailingIcon?.invoke()
      }
    },
  )
}
