package live.mehiz.mpvkt.presentation.components

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import live.mehiz.mpvkt.R

// Matches the original Movie / series search field's Material height.
internal val AppFieldHeight = 56.dp
internal val AppFieldShape = RoundedCornerShape(16.dp)

/**
 * One field shell across dialogs, settings and panes. Text scaling can grow it without clipping.
 */
@Composable
internal fun AppTextField(
  value: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  readOnly: Boolean = false,
  textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
  label: (@Composable () -> Unit)? = null,
  placeholder: (@Composable () -> Unit)? = null,
  leadingIcon: (@Composable () -> Unit)? = null,
  trailingIcon: (@Composable () -> Unit)? = null,
  supportingText: (@Composable () -> Unit)? = null,
  isError: Boolean = false,
  visualTransformation: VisualTransformation = VisualTransformation.None,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
  keyboardActions: KeyboardActions = KeyboardActions.Default,
  singleLine: Boolean = true,
  maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
  minLines: Int = 1,
  shape: Shape = AppFieldShape,
) {
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()
  val colors = MaterialTheme.colorScheme
  val ink = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = .38f)
  val hint = colors.onSurfaceVariant.copy(alpha = if (enabled) 1f else .38f)
  val errorMessage = stringResource(R.string.app_field_invalid)
  Column(modifier.fillMaxWidth()) {
    BasicTextField(
      value = value,
      onValueChange = onValueChange,
      modifier =
        Modifier.fillMaxWidth().heightIn(min = AppFieldHeight).semantics {
          if (isError) error(errorMessage)
        },
      enabled = enabled,
      readOnly = readOnly,
      singleLine = singleLine,
      maxLines = if (singleLine) 1 else maxLines.coerceAtLeast(1),
      minLines = if (singleLine) 1 else minLines.coerceIn(1, maxLines.coerceAtLeast(1)),
      visualTransformation = visualTransformation,
      textStyle = textStyle.copy(color = ink),
      cursorBrush = SolidColor(if (isError) colors.error else colors.primary),
      interactionSource = interaction,
      keyboardOptions = keyboardOptions,
      keyboardActions = keyboardActions,
      decorationBox = { input ->
        Row(
          Modifier.fillMaxWidth()
            .heightIn(min = AppFieldHeight)
            .background(colors.surfaceContainerHigh.copy(alpha = if (enabled) .4f else .2f), shape)
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
          CompositionLocalProvider(LocalContentColor provides hint) { leadingIcon?.invoke() }
          Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            // A stable label slot prevents the entered text from jumping when its first character
            // appears.
            if (label != null)
              CompositionLocalProvider(LocalContentColor provides hint) {
                ProvideTextStyle(MaterialTheme.typography.labelSmall) { label() }
              }
            Box {
              if (value.isEmpty() && placeholder != null)
                CompositionLocalProvider(LocalContentColor provides hint) {
                  ProvideTextStyle(textStyle) { placeholder() }
                }
              input()
            }
          }
          CompositionLocalProvider(LocalContentColor provides hint) { trailingIcon?.invoke() }
        }
      },
    )
    if (supportingText != null)
      CompositionLocalProvider(LocalContentColor provides if (isError) colors.error else hint) {
        ProvideTextStyle(MaterialTheme.typography.labelSmall) {
          Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) { supportingText() }
        }
      }
  }
}
