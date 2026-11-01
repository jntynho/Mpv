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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/** Body height of the Movie/Series search field; supporting messages live outside this box. */
internal val AppFieldHeight = 48.dp
internal val AppFieldShape = RoundedCornerShape(16.dp)

/** One input implementation for dialogs, settings, search and player panels. */
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
  prefix: (@Composable () -> Unit)? = null,
  suffix: (@Composable () -> Unit)? = null,
  supportingText: (@Composable () -> Unit)? = null,
  isError: Boolean = false,
  visualTransformation: VisualTransformation = VisualTransformation.None,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
  keyboardActions: KeyboardActions = KeyboardActions.Default,
  singleLine: Boolean = true,
  maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
  shape: Shape = AppFieldShape,
  outlined: Boolean = true,
) {
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()
  val colors = MaterialTheme.colorScheme
  val foreground = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = .38f)
  val border = when {
    !enabled -> colors.outlineVariant.copy(alpha = .38f)
    isError -> colors.error
    focused -> colors.primary
    else -> colors.outlineVariant.copy(alpha = .65f)
  }
  val externalLabel = label != null && LocalDensity.current.fontScale > 1.3f
  val errorDescription = androidx.compose.ui.res.stringResource(live.mehiz.mpvkt.R.string.mpv_input_invalid)
  // Messages are outside the fixed input body, so validation never stretches the field itself.
  Column(modifier.fillMaxWidth()) {
    if (externalLabel) {
      Box(Modifier.padding(start = 12.dp, bottom = 4.dp)) {
        ProvideTextStyle(MaterialTheme.typography.labelSmall) { label?.invoke() }
      }
    }
    BasicTextField(
      value = value,
      onValueChange = onValueChange,
      modifier = Modifier.fillMaxWidth().height(AppFieldHeight).clip(shape)
        .semantics { if (isError) error(errorDescription) },
      enabled = enabled,
      readOnly = readOnly,
      singleLine = singleLine,
      maxLines = if (singleLine) 1 else maxLines.coerceAtLeast(1),
      textStyle = textStyle.copy(color = if (textStyle.color == Color.Unspecified) foreground else textStyle.color),
      cursorBrush = SolidColor(if (isError) colors.error else colors.primary),
      visualTransformation = visualTransformation,
      interactionSource = interaction,
      keyboardOptions = keyboardOptions,
      keyboardActions = keyboardActions,
      decorationBox = { input ->
        Row(
          Modifier.fillMaxSize()
            .background(if (outlined) colors.surfaceContainerHigh.copy(alpha = .4f) else Color.Transparent, shape)
            .then(if (outlined) Modifier.border(1.dp, border, shape) else Modifier)
            .padding(horizontal = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          CompositionLocalProvider(LocalContentColor provides foreground) {
            leadingIcon?.invoke()
            prefix?.invoke()
            Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
              if (!externalLabel && label != null && (value.isNotEmpty() || focused)) {
                CompositionLocalProvider(LocalContentColor provides if (isError) colors.error else colors.onSurfaceVariant) {
                  ProvideTextStyle(MaterialTheme.typography.labelSmall) { label() }
                }
              }
              Box(Modifier.fillMaxWidth()) {
                if (value.isEmpty()) {
                  CompositionLocalProvider(LocalContentColor provides colors.onSurfaceVariant) {
                    ProvideTextStyle(textStyle) {
                      if (!externalLabel && !focused && label != null) label() else placeholder?.invoke()
                    }
                  }
                }
                input()
              }
            }
            suffix?.invoke()
            trailingIcon?.invoke()
          }
        }
      },
    )
    if (supportingText != null) {
      Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        CompositionLocalProvider(LocalContentColor provides if (isError) colors.error else colors.onSurfaceVariant) {
          ProvideTextStyle(MaterialTheme.typography.bodySmall) { supportingText() }
        }
      }
    }
  }
}
