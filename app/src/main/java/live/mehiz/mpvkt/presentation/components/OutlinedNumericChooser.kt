package live.mehiz.mpvkt.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.ui.icons.*
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
) {
  require(max > min)
  var draft by remember { mutableStateOf(value.toString()) }
  var focused by remember { mutableStateOf(false) }
  // Engine acknowledgements may arrive late; they must not overwrite an active edit.
  LaunchedEffect(value, focused) { if (!focused) draft = value.toString() }
  val parsed = draft.toIntOrNull()
  val tooLarge = parsed?.let { it > max } ?: false
  val tooSmall = parsed?.let { it < min } ?: false
  val changeBy: (Int) -> Unit = { direction ->
    val next = ((parsed ?: value).toLong() + step.toLong() * direction)
      .coerceIn(min.toLong(), max.toLong()).toInt()
    draft = next.toString()
    onChange(next)
  }
  NumericChooserRow(modifier, { changeBy(-1) }, { changeBy(1) }) {
    AppTextField(
      value = draft,
      onValueChange = { raw ->
        if (raw.isEmpty() || raw == "-" || raw.toIntOrNull() != null) {
          draft = raw
          raw.toIntOrNull()?.takeIf { it in min..max }?.let(onChange)
        }
      },
      modifier = Modifier.weight(1f).onFocusChanged { focused = it.isFocused },
      label = label,
      suffix = suffix,
      isError = tooLarge || tooSmall,
      supportingText = numericError(tooLarge, tooSmall),
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
  }
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
) {
  require(min.isFinite() && max.isFinite() && max > min)
  fun format(number: Float) = java.math.BigDecimal(number.toString()).stripTrailingZeros().toPlainString()
  val safeValue = value.takeIf { it.isFinite() }?.coerceIn(min, max) ?: min
  var draft by remember { mutableStateOf(format(safeValue)) }
  var focused by remember { mutableStateOf(false) }
  LaunchedEffect(safeValue, focused) { if (!focused) draft = format(safeValue) }
  val parsed = draft.toFloatOrNull()?.takeIf { it.isFinite() }
  val tooLarge = parsed?.let { it > max } ?: false
  val tooSmall = parsed?.let { it < min } ?: false
  val changeBy: (Int) -> Unit = { direction ->
    val next = ((parsed ?: safeValue) + step * direction).coerceIn(min, max)
    draft = format(next)
    onChange(next)
  }
  NumericChooserRow(modifier, { changeBy(-1) }, { changeBy(1) }) {
    AppTextField(
      value = draft,
      onValueChange = { raw ->
        val number = raw.toFloatOrNull()?.takeIf { it.isFinite() }
        if (raw in listOf("", "-", ".", "-.") || number != null) {
          draft = raw
          number?.takeIf { it in min..max }?.let(onChange)
        }
      },
      modifier = Modifier.weight(1f).onFocusChanged { focused = it.isFocused },
      label = label,
      suffix = suffix,
      isError = tooLarge || tooSmall,
      supportingText = numericError(tooLarge, tooSmall),
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
  }
}

private fun numericError(tooLarge: Boolean, tooSmall: Boolean): (@Composable () -> Unit)? =
  if (!tooLarge && !tooSmall) null
  else {
    { Text(stringResource(if (tooLarge) R.string.numeric_chooser_value_too_big else R.string.numeric_chooser_value_too_small)) }
  }

@Composable
private fun NumericChooserRow(
  modifier: Modifier,
  decrease: () -> Unit,
  increase: () -> Unit,
  content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
  Row(
    modifier,
    verticalAlignment = Alignment.Top,
    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
  ) {
    RepeatingIconButton(onClick = decrease) { Icon(Icons.RemoveCircle, null) }
    content()
    RepeatingIconButton(onClick = increase) { Icon(Icons.AddCircle, null) }
  }
}
