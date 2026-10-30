package live.mehiz.mpvkt.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.ui.icons.*
import live.mehiz.mpvkt.ui.player.controls.components.PanelTextField
import live.mehiz.mpvkt.ui.theme.spacing

@Composable
fun OutlinedNumericChooser(
  value: Int,
  onChange: (Int) -> Unit,
  max: Int,
  step: Int,
  modifier: Modifier = Modifier,
  min: Int = 0,
  suffix: (@Composable () -> Unit)? = null,
  label: (@Composable () -> Unit)? = null,
  enabled: Boolean = true,
) {
  require(max > min && step > 0)
  NumericChooser(
    value.toString(),
    { validInteger(it, min, max)?.let(onChange) },
    { validInteger(it, min, max) != null },
    { onChange(steppedInteger(value, -step.toLong(), min, max)) },
    { onChange(steppedInteger(value, step.toLong(), min, max)) },
    enabled && value > min,
    enabled && value < max,
    min < 0,
    modifier,
    label,
    suffix,
    enabled,
  )
}

@Composable
fun OutlinedNumericChooser(
  value: Float,
  onChange: (Float) -> Unit,
  max: Float,
  step: Float,
  modifier: Modifier = Modifier,
  min: Float = 0f,
  suffix: (@Composable () -> Unit)? = null,
  label: (@Composable () -> Unit)? = null,
  enabled: Boolean = true,
) {
  require(min.isFinite() && max.isFinite() && max > min && step.isFinite() && step > 0)
  val safe = value.takeIf { it.isFinite() }?.coerceIn(min, max) ?: min
  NumericChooser(
    safe.toString(),
    { validDecimal(it, min, max)?.let(onChange) },
    { validDecimal(it, min, max) != null },
    { onChange((safe.toDouble() - step).coerceIn(min.toDouble(), max.toDouble()).toFloat()) },
    { onChange((safe.toDouble() + step).coerceIn(min.toDouble(), max.toDouble()).toFloat()) },
    enabled && safe > min,
    enabled && safe < max,
    min < 0,
    modifier,
    label,
    suffix,
    enabled,
  )
}

@Composable
private fun NumericChooser(
  value: String,
  change: (String) -> Unit,
  valid: (String) -> Boolean,
  decrease: () -> Unit,
  increase: () -> Unit,
  canDecrease: Boolean,
  canIncrease: Boolean,
  signed: Boolean,
  modifier: Modifier,
  label: (@Composable () -> Unit)?,
  suffix: (@Composable () -> Unit)?,
  enabled: Boolean,
) {
  var draft by remember { mutableStateOf(value) }
  val focus = androidx.compose.ui.platform.LocalFocusManager.current
  val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
  // Preserve in-progress signs/decimal points; only valid values are sent to the caller.
  LaunchedEffect(value) {
    if (valid(draft) && normalizedNumber(draft).toDoubleOrNull() == value.toDoubleOrNull())
      return@LaunchedEffect
    draft = value
  }
  Column(modifier) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
    ) {
      RepeatingIconButton(decrease, enabled = canDecrease) {
        Icon(Icons.RemoveCircle, stringResource(R.string.panel_decrease))
      }
      PanelTextField(
        draft,
        {
          draft = it.take(32)
          if (valid(draft)) change(draft)
        },
        Modifier.weight(1f),
        enabled = enabled,
        label = label,
        isError = draft.isNotBlank() && draft !in listOf("-", ".", "-.") && !valid(draft),
        leadingIcon =
          if (signed)
            ({
              TextButton(
                onClick = {
                  draft = if (draft.startsWith('-')) draft.drop(1) else "-$draft"
                  if (valid(draft)) change(draft)
                },
                enabled = enabled,
                contentPadding = PaddingValues(),
              ) {
                Text("±")
              }
            })
          else null,
        trailingIcon = suffix,
        keyboardOptions =
          KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions =
          KeyboardActions(
            onDone = {
              if (valid(draft)) change(draft) else draft = value
              focus.clearFocus()
              keyboard?.hide()
            }
          ),
      )
      RepeatingIconButton(increase, enabled = canIncrease) {
        Icon(Icons.AddCircle, stringResource(R.string.panel_increase))
      }
    }
    if (draft.isNotBlank() && draft !in listOf("-", ".", "-.") && !valid(draft))
      Text(
        stringResource(R.string.panel_invalid_number),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.labelSmall,
      )
  }
}
