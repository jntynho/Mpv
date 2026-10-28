package live.mehiz.mpvkt.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.ui.icons.*
import live.mehiz.mpvkt.ui.motion.LocalReduceMotion

private val LocalDialogPrimary = compositionLocalOf<Boolean?> { null }

/**
 * Shared app-owned dialog: matte surface, quiet rim, concentric fields and equal capsule actions.
 */
@Composable
internal fun AppDialog(
  onDismissRequest: () -> Unit,
  confirmButton: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  dismissButton: (@Composable () -> Unit)? = null,
  title: (@Composable () -> Unit)? = null,
  text: (@Composable () -> Unit)? = null,
  icon: (@Composable () -> Unit)? = null,
) {
  val reduceMotion = LocalReduceMotion.current
  Dialog(onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    val window =
      (androidx.compose.ui.platform.LocalView.current.parent
          as? androidx.compose.ui.window.DialogWindowProvider)
        ?.window
    DisposableEffect(window, reduceMotion) {
      window?.setWindowAnimations(if (reduceMotion) 0 else R.style.MpvDialogMotion)
      onDispose {}
    }
    BoxWithConstraints(
      Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(20.dp),
      contentAlignment = Alignment.Center,
    ) {
      Box(
        Modifier.matchParentSize()
          .clickable(
            remember { MutableInteractionSource() },
            indication = null,
            onClick = onDismissRequest,
          )
      )
      Surface(
        modifier.widthIn(max = 420.dp).fillMaxWidth().heightIn(max = maxHeight).pointerInput(Unit) {
          detectTapGestures(onTap = {})
        },
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f)),
      ) {
        Column(Modifier.padding(20.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Surface(
              shape = RoundedCornerShape(14.dp),
              color =
                lerp(
                  MaterialTheme.colorScheme.surfaceContainer,
                  MaterialTheme.colorScheme.onSurface,
                  .045f,
                ),
              border =
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .3f)),
            ) {
              Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                CompositionLocalProvider(
                  LocalContentColor provides MaterialTheme.colorScheme.primary
                ) {
                  if (icon != null) icon() else Icon(Icons.Tune, null, Modifier.size(24.dp))
                }
              }
            }
            Box(Modifier.weight(1f)) {
              ProvideTextStyle(MaterialTheme.typography.titleLarge) { title?.invoke() }
            }
            IconButton(onClick = onDismissRequest, modifier = Modifier.size(40.dp)) {
              Icon(Icons.Close, stringResource(R.string.generic_close), Modifier.size(22.dp))
            }
          }
          Spacer(Modifier.height(20.dp))
          Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            CompositionLocalProvider(
              LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant
            ) {
              ProvideTextStyle(MaterialTheme.typography.bodyMedium) { text?.invoke() }
            }
          }
          Spacer(Modifier.height(20.dp))
          Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            if (dismissButton != null)
              Box(Modifier.weight(1f).fillMaxHeight()) {
                CompositionLocalProvider(LocalDialogPrimary provides false) { dismissButton() }
              }
            Box(Modifier.weight(1f).fillMaxHeight()) {
              CompositionLocalProvider(LocalDialogPrimary provides true) { confirmButton() }
            }
          }
        }
      }
    }
  }
}

/** Footer slots carry the button role; an auxiliary Paste action remains a text button. */
@Composable
internal fun DialogButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  content: @Composable RowScope.() -> Unit,
) {
  val primary = LocalDialogPrimary.current
  if (primary == null) TextButton(onClick, modifier, enabled = enabled, content = content)
  else
    OutlinedButton(
      onClick,
      modifier.fillMaxWidth().heightIn(min = 48.dp).fillMaxHeight(),
      enabled = enabled,
      shape = RoundedCornerShape(50),
      border =
        BorderStroke(
          1.dp,
          MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (enabled) .6f else .2f),
        ),
      colors =
        ButtonDefaults.outlinedButtonColors(
          containerColor =
            if (primary) MaterialTheme.colorScheme.primary
            else
              lerp(
                MaterialTheme.colorScheme.surfaceContainer,
                MaterialTheme.colorScheme.onSurface,
                .045f,
              ),
          contentColor =
            if (primary) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface,
        ),
      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
      content = content,
    )
}

@Composable
internal fun RoundedTextField(
  value: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  readOnly: Boolean = false,
  textStyle: TextStyle = LocalTextStyle.current,
  label: (@Composable () -> Unit)? = null,
  placeholder: (@Composable () -> Unit)? = null,
  leadingIcon: (@Composable () -> Unit)? = null,
  trailingIcon: (@Composable () -> Unit)? = null,
  supportingText: (@Composable () -> Unit)? = null,
  isError: Boolean = false,
  visualTransformation: VisualTransformation = VisualTransformation.None,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
  keyboardActions: KeyboardActions = KeyboardActions.Default,
  singleLine: Boolean = false,
  maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
  minLines: Int = 1,
  shape: Shape = RoundedCornerShape(16.dp),
) {
  OutlinedTextField(
    value,
    onValueChange,
    modifier.fillMaxWidth(),
    enabled,
    readOnly,
    textStyle,
    label,
    placeholder,
    leadingIcon,
    trailingIcon,
    supportingText = supportingText,
    isError = isError,
    visualTransformation = visualTransformation,
    keyboardOptions = keyboardOptions,
    keyboardActions = keyboardActions,
    singleLine = singleLine,
    maxLines = maxLines,
    minLines = minLines,
    shape = shape,
    colors =
      OutlinedTextFieldDefaults.colors(
        unfocusedContainerColor =
          lerp(
            MaterialTheme.colorScheme.surfaceContainer,
            MaterialTheme.colorScheme.onSurface,
            .025f,
          ),
        focusedContainerColor =
          lerp(
            MaterialTheme.colorScheme.surfaceContainer,
            MaterialTheme.colorScheme.onSurface,
            .025f,
          ),
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f),
        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = .7f),
      ),
  )
}
