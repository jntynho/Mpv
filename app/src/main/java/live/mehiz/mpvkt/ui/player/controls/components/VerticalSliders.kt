package live.mehiz.mpvkt.ui.player.controls.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import live.mehiz.mpvkt.ui.icons.Icons
import live.mehiz.mpvkt.ui.icons.automirrored.filled.VolumeDown
import live.mehiz.mpvkt.ui.icons.automirrored.filled.VolumeMute
import live.mehiz.mpvkt.ui.icons.automirrored.filled.VolumeOff
import live.mehiz.mpvkt.ui.icons.automirrored.filled.VolumeUp
import live.mehiz.mpvkt.ui.icons.filled.BrightnessHigh
import live.mehiz.mpvkt.ui.icons.filled.BrightnessLow
import live.mehiz.mpvkt.ui.icons.filled.BrightnessMedium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.ui.theme.spacing
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun VerticalSlider(
  value: Float,
  range: ClosedFloatingPointRange<Float>,
  modifier: Modifier = Modifier,
  overflowValue: Float? = null,
  overflowRange: ClosedFloatingPointRange<Float>? = null,
) {
  Box(
    modifier = modifier
      .height(112.dp)
      .width(6.dp)
      .clip(RoundedCornerShape(16.dp))
      .background(Color.White.copy(alpha = .22f)),
    contentAlignment = Alignment.BottomCenter,
  ) {
    val targetHeight by animateFloatAsState(percentage(value, range), animationSpec = tween(90), label = "vsliderheight")
    Box(
      Modifier
        .fillMaxWidth()
        .fillMaxHeight(targetHeight)
        .background(Color.White.copy(alpha = .95f)),
    )
    if (overflowRange != null && overflowValue != null) {
      val overflowHeight by animateFloatAsState(
        percentage(overflowValue, overflowRange),
        label = "vslideroverflowheight",
      )
      Box(
        Modifier
          .fillMaxWidth()
          .fillMaxHeight(overflowHeight)
          .background(MaterialTheme.colorScheme.errorContainer),
      )
    }
  }
}

@Composable
fun VerticalSlider(
  value: Int,
  range: ClosedRange<Int>,
  modifier: Modifier = Modifier,
  overflowValue: Int? = null,
  overflowRange: ClosedRange<Int>? = null,
) {
  Box(
    modifier = modifier
      .height(112.dp)
      .width(6.dp)
      .clip(RoundedCornerShape(16.dp))
      .background(Color.White.copy(alpha = .22f)),
    contentAlignment = Alignment.BottomCenter,
  ) {
    val targetHeight by animateFloatAsState(percentage(value, range), animationSpec = tween(90), label = "vsliderheight")
    Box(
      Modifier
        .fillMaxWidth()
        .fillMaxHeight(targetHeight)
        .background(Color.White.copy(alpha = .95f)),
    )
    if (overflowRange != null && overflowValue != null) {
      val overflowHeight by animateFloatAsState(
        percentage(overflowValue, overflowRange),
        label = "vslideroverflowheight",
      )
      Box(
        Modifier
          .fillMaxWidth()
          .fillMaxHeight(overflowHeight)
          .background(MaterialTheme.colorScheme.errorContainer),
      )
    }
  }
}

@Composable
fun BrightnessSlider(
  brightness: Float,
  range: ClosedFloatingPointRange<Float>,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
  ) {
    Text(
      (brightness * 100).toInt().toString(),
      style = MaterialTheme.typography.bodySmall,
    )
    VerticalSlider(
      brightness,
      range,
    )
    Icon(
      when (percentage(brightness, range)) {
        in 0f..0.3f -> Icons.Default.BrightnessLow
        in 0.3f..0.6f -> Icons.Default.BrightnessMedium
        in 0.6f..1f -> Icons.Default.BrightnessHigh
        else -> Icons.Default.BrightnessMedium
      },
      null,
    )
  }
}

@Composable
fun VolumeSlider(
  volume: Int,
  mpvVolume: Int,
  range: ClosedRange<Int>,
  boostRange: ClosedRange<Int>?,
  modifier: Modifier = Modifier,
  displayAsPercentage: Boolean = false,
) {
  val percentage = (percentage(volume, range) * 100).roundToInt()
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
  ) {
    val boostVolume = mpvVolume - 100
    Text(
      getVolumeSliderText(volume, mpvVolume, boostVolume, percentage, displayAsPercentage),
      style = MaterialTheme.typography.bodySmall,
    )
    VerticalSlider(
      if (displayAsPercentage) percentage else volume,
      if (displayAsPercentage) 0..100 else range,
      overflowValue = boostVolume,
      overflowRange = boostRange,
    )
    Icon(
      when (percentage) {
        0 -> Icons.AutoMirrored.Default.VolumeOff
        in 0..30 -> Icons.AutoMirrored.Default.VolumeMute
        in 30..60 -> Icons.AutoMirrored.Default.VolumeDown
        in 60..100 -> Icons.AutoMirrored.Default.VolumeUp
        else -> Icons.AutoMirrored.Default.VolumeOff
      },
      null,
    )
  }
}

val getVolumeSliderText: @Composable (Int, Int, Int, Int, Boolean) -> String =
  { volume, mpvVolume, boostVolume, percentage, displayAsPercentage ->
    when (mpvVolume - 100) {
      0 -> if (displayAsPercentage) {
        stringResource(R.string.value_percentage_int, percentage)
      } else {
        "$volume"
      }

      in 0..1000 -> {
        if (displayAsPercentage) {
          stringResource(R.string.volume_slider_percentage_positive_boost, percentage, boostVolume)
        } else {
          stringResource(R.string.volume_slider_steps_positive_boost, volume, boostVolume)
        }
      }

      in -100..-1 -> {
        if (displayAsPercentage) {
          stringResource(R.string.volume_slider_percentage_negative_boost, percentage, abs(boostVolume))
        } else {
          stringResource(R.string.volume_slider_steps_negative_boost, volume, abs(boostVolume))
        }
      }

      else -> {
        if (displayAsPercentage) {
          stringResource(R.string.volume_slider_percentage_other, percentage, boostVolume)
        } else {
          stringResource(R.string.volume_slider_steps_other, volume, boostVolume)
        }
      }
    }
  }
