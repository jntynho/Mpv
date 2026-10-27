package live.mehiz.mpvkt.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun SliderItem(label: String, value: Int, valueText: String, onChange: (Int) -> Unit, max: Int,
  modifier: Modifier = Modifier, min: Int = 0, icon: @Composable () -> Unit = {}) {
  PlayerSliderLayout(label, valueText, modifier, icon) {
    SlimPlayerSlider(value.toFloat(), { onChange(it.roundToInt()) }, Modifier.fillMaxWidth(),
      valueRange = min.toFloat()..max.toFloat(), steps = (max - min - 1).coerceAtLeast(0))
  }
}

@Composable
fun SliderItem(label: String, value: Float, valueText: String, onChange: (Float) -> Unit, max: Float,
  modifier: Modifier = Modifier, steps: Int = 0, min: Float = 0f, icon: @Composable () -> Unit = {}) {
  PlayerSliderLayout(label, valueText, modifier, icon) {
    SlimPlayerSlider(value, onChange, Modifier.fillMaxWidth(), valueRange = min..max, steps = steps)
  }
}

/** Titles and values keep their own row; narrow panels don't squeeze labels into single letters. */
@Composable
internal fun PlayerSliderLayout(label: String, valueText: String, modifier: Modifier,
  icon: @Composable () -> Unit, slider: @Composable () -> Unit) {
  Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      icon()
      Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
      Text(valueText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        maxLines = 1)
    }
    slider()
  }
}
