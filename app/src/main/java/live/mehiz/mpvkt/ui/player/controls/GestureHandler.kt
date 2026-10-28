package live.mehiz.mpvkt.ui.player.controls

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeGestures
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.flow.map
import live.mehiz.mpvkt.preferences.SubtitlesPreferences
import live.mehiz.mpvkt.ui.player.PlaybackControl
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.preferences.AudioPreferences
import live.mehiz.mpvkt.preferences.PlayerPreferences
import live.mehiz.mpvkt.preferences.preference.collectAsState
import live.mehiz.mpvkt.presentation.components.LeftSideOvalShape
import live.mehiz.mpvkt.presentation.components.RightSideOvalShape
import live.mehiz.mpvkt.ui.player.PlayerUpdates
import live.mehiz.mpvkt.ui.player.PlayerViewModel
import live.mehiz.mpvkt.ui.player.controls.components.DoubleTapSeekTriangles
import live.mehiz.mpvkt.ui.theme.playerRippleConfiguration
import org.koin.compose.koinInject

@Suppress("CyclomaticComplexMethod", "MultipleEmitters")
@Composable
fun GestureHandler(
  viewModel: PlayerViewModel,
  interactionSource: MutableInteractionSource,
  modifier: Modifier = Modifier
) {
  val playerPreferences = koinInject<PlayerPreferences>()
  val subtitlePreferences = koinInject<SubtitlesPreferences>()
  val linkedSecondary by subtitlePreferences.secondaryPinchZoom.collectAsState()
  val secondaryScaleSupported by subtitlePreferences.secondaryScaleSupported.collectAsState()
  val primaryText by MPVLib.propString["sub-text"].collectAsState()
  val secondaryText by MPVLib.propString["secondary-sub-text"].collectAsState()
  val audioPreferences = koinInject<AudioPreferences>()
  val panelPages by viewModel.panelNavigator.state.collectAsState()
  val allowGesturesInPanels by playerPreferences.allowGesturesInPanels.collectAsState()
  val playback by viewModel.playback.collectAsState()
  val paused = playback.displayPaused
  val duration by remember { MPVLib.propDouble["duration"].map { it?.toInt() } }.collectAsState(initial = null)
  val position by remember { MPVLib.propDouble["time-pos"].map { it?.toInt() } }.collectAsState(initial = null)
  val playbackSpeed by MPVLib.propFloat["speed"].collectAsState()
  val controlsShown by viewModel.controlsShown.collectAsState()
  val areControlsLocked by viewModel.areControlsLocked.collectAsState()
  val seekAmount by viewModel.doubleTapSeekAmount.collectAsState()
  val isSeekingForwards by viewModel.isSeekingForwards.collectAsState()
  var isDoubleTapSeeking by remember { mutableStateOf(false) }
  LaunchedEffect(seekAmount) {
    delay(800)
    isDoubleTapSeeking = false
    viewModel.updateSeekAmount(0)
    viewModel.updateSeekText(null)
    delay(100)
    viewModel.hideSeekBar()
  }
  val multipleSpeedGesture by playerPreferences.holdForMultipleSpeed.collectAsState()
  val brightnessGesture by playerPreferences.brightnessGesture.collectAsState()
  val volumeGesture by playerPreferences.volumeGesture.collectAsState()
  val swapVolumeAndBrightness by playerPreferences.swapVolumeAndBrightness.collectAsState()
  val pinchZoomGesture by playerPreferences.pinchZoomGesture.collectAsState()
  val seekGesture by playerPreferences.horizontalSeekGesture.collectAsState()
  val preciseSeeking by playerPreferences.preciseSeeking.collectAsState()
  val showSeekbarWhenSeeking by playerPreferences.showSeekBarWhenSeeking.collectAsState()
  var isLongPressing by remember { mutableStateOf(false) }
  val currentVolume by viewModel.currentVolume.collectAsState()
  val currentMPVVolume by MPVLib.propInt["volume"].collectAsState()
  val currentBrightness by viewModel.currentBrightness.collectAsState()
  val volumeBoostingCap = audioPreferences.volumeBoostCap.get()
  val haptics = LocalHapticFeedback.current
  Box(
    modifier = modifier
      .fillMaxSize()
      .windowInsetsPadding(WindowInsets.safeGestures)
      .pointerInput(areControlsLocked, panelPages, allowGesturesInPanels, pinchZoomGesture, linkedSecondary, secondaryScaleSupported) {
        if (!pinchZoomGesture || areControlsLocked || (panelPages.isNotEmpty() && !allowGesturesInPanels)) return@pointerInput
        var target = 0
        var primaryScale = subtitlePreferences.subScale.get()
        var secondaryScale = subtitlePreferences.secondaryScale.get()
        var changedPrimary = false
        var changedSecondary = false
        try {
          awaitPointerEventScope {
            var pinching = false
            do {
              val event = awaitPointerEvent(PointerEventPass.Initial)
              val fingers = event.changes.filter { it.pressed }
              val count = fingers.size
              if(count >= 2) {
                if(!pinching) {
                  val centerY = fingers.map { it.position.y }.average().toFloat() / size.height.coerceAtLeast(1)
                  val centerX = fingers.map { it.position.x }.average().toFloat() / size.width.coerceAtLeast(1)
                  target = subtitlePinchTarget(centerX,centerY,primaryText.orEmpty(),secondaryText.orEmpty(),subtitlePreferences.subPos.get(),secondaryScaleSupported)
                  primaryScale=subtitlePreferences.subScale.get();secondaryScale=subtitlePreferences.secondaryScale.get()
                }
                pinching=true
                val factor=event.calculateZoom()
                if(factor.isFinite() && factor>0f && factor!=1f) {
                  if(target!=0) {
                    if(target==1 && "sub-scale" !in live.mehiz.mpvkt.subtitles.SubtitleConfig.active) {
                      primaryScale=(primaryScale*factor).coerceIn(.1f,5f);changedPrimary=true
                      val value=primaryScale
                      PlaybackControl.enqueue("property:sub-scale") { MPVLib.setPropertyFloat("sub-scale",value) }
                    }
                    if((target==2 || linkedSecondary && secondaryScaleSupported && secondaryText?.isNotBlank()==true) && "secondary-sub-scale" !in live.mehiz.mpvkt.subtitles.SubtitleConfig.active) {
                      secondaryScale=(secondaryScale*factor).coerceIn(.1f,5f);changedSecondary=true
                      val value=secondaryScale
                      PlaybackControl.enqueue("property:secondary-sub-scale") { MPVLib.setPropertyFloat("secondary-sub-scale",value) }
                    }
                  } else {
                    val current=MPVLib.getPropertyFloat("video-zoom") ?: 0f
                    MPVLib.setPropertyFloat("video-zoom",pinchZoom(current,factor))
                  }
                }
              }
              if(pinching) event.changes.forEach { it.consume() }
              if(count==0) {
                if(changedPrimary) subtitlePreferences.subScale.set(primaryScale)
                if(changedSecondary) subtitlePreferences.secondaryScale.set(secondaryScale)
                changedPrimary=false;changedSecondary=false;pinching=false
              }
            } while(true)
          }
        } finally {
          if(changedPrimary) subtitlePreferences.subScale.set(primaryScale)
          if(changedSecondary) subtitlePreferences.secondaryScale.set(secondaryScale)
        }
      }
      .pointerInput(Unit) {
        var originalSpeed = MPVLib.getPropertyFloat("speed") ?: 1f
        detectTapGestures(
          onTap = {
            if (controlsShown) viewModel.hideControls() else viewModel.showControls()
          },
          onDoubleTap = {
            if (areControlsLocked || isDoubleTapSeeking) return@detectTapGestures
            if (it.x > size.width * 3 / 5) {
              if (!isSeekingForwards) viewModel.updateSeekAmount(0)
              viewModel.handleRightDoubleTap()
              isDoubleTapSeeking = true
            } else if (it.x < size.width * 2 / 5) {
              if (isSeekingForwards) viewModel.updateSeekAmount(0)
              viewModel.handleLeftDoubleTap()
              isDoubleTapSeeking = true
            } else {
              viewModel.handleCenterDoubleTap()
            }
          },
          onPress = {
            if (panelPages.isNotEmpty() && !allowGesturesInPanels) {
              viewModel.panelNavigator.dismiss()
            }
            if (!areControlsLocked && isDoubleTapSeeking && seekAmount != 0) {
              if (it.x > size.width * 3 / 5) {
                if (!isSeekingForwards) viewModel.updateSeekAmount(0)
                viewModel.handleRightDoubleTap()
              } else if (it.x < size.width * 2 / 5) {
                if (isSeekingForwards) viewModel.updateSeekAmount(0)
                viewModel.handleLeftDoubleTap()
              } else {
                viewModel.handleCenterDoubleTap()
              }
            } else {
              isDoubleTapSeeking = false
            }
            val press = PressInteraction.Press(
              it.copy(x = if (it.x > size.width * 3 / 5) it.x - size.width * 0.6f else it.x),
            )
            interactionSource.emit(press)
            tryAwaitRelease()
            if (isLongPressing) {
              isLongPressing = false
              MPVLib.setPropertyFloat("speed", originalSpeed)
              viewModel.playerUpdate.update { PlayerUpdates.None }
            }
            interactionSource.emit(PressInteraction.Release(press))
          },
          onLongPress = {
            if (multipleSpeedGesture == 0f || areControlsLocked) return@detectTapGestures
            if (!isLongPressing && paused == false) {
              originalSpeed = playbackSpeed ?: return@detectTapGestures
              haptics.performHapticFeedback(HapticFeedbackType.LongPress)
              isLongPressing = true
              MPVLib.setPropertyFloat("speed", multipleSpeedGesture)
              viewModel.playerUpdate.update { PlayerUpdates.MultipleSpeed }
            }
          },
        )
      }
      .pointerInput(areControlsLocked, seekGesture, panelPages.isNotEmpty(), allowGesturesInPanels) {
        if (!seekGesture || areControlsLocked || (panelPages.isNotEmpty() && !allowGesturesInPanels)) return@pointerInput
        var startingPosition = position ?: 0
        var startingX = 0f
        detectHorizontalDragGestures(
          onDragStart = {
            startingPosition = position ?: 0
            startingX = it.x
            viewModel.beginSeek()
          },
          onDragCancel = {
            viewModel.gestureSeekAmount.update { null }
            viewModel.hideSeekBar()
            viewModel.endSeek()
          },
          onDragEnd = {
            viewModel.gestureSeekAmount.update { null }
            viewModel.hideSeekBar()
            viewModel.endSeek()
          },
        ) { change, dragAmount ->
          if ((position ?: 0) <= 0f && dragAmount < 0) return@detectHorizontalDragGestures
          if ((position ?: 0) >= (duration ?: 0) && dragAmount > 0) return@detectHorizontalDragGestures
          calculateNewHorizontalGestureValue(
            startingPosition,
            startingX,
            change.position.x,
            0.15f
          ).let {
            viewModel.gestureSeekAmount.update { _ ->
              Pair(
                startingPosition,
                (it - startingPosition)
                  .coerceIn(0 - startingPosition, ((duration ?: 0) - startingPosition)),
              )
            }
            viewModel.seekTo(it, preciseSeeking)
          }

          if (showSeekbarWhenSeeking) viewModel.showSeekBar()
        }
      }
      .pointerInput(areControlsLocked, brightnessGesture, volumeGesture, swapVolumeAndBrightness, panelPages.isNotEmpty(), allowGesturesInPanels) {
        if ((!brightnessGesture && !volumeGesture) || areControlsLocked || (panelPages.isNotEmpty() && !allowGesturesInPanels)) return@pointerInput
        var startingY = 0f
        var mpvVolumeStartingY = 0f
        var originalVolume = currentVolume
        var originalMPVVolume = currentMPVVolume
        var originalBrightness = currentBrightness
        var adjustingBrightness = false
        var brightnessStartY = 0f
        val volumeGestureSens = 0.03f
        val mpvVolumeGestureSens = 0.02f
        val isIncreasingVolumeBoost: (Float) -> Boolean = {
          volumeBoostingCap > 0 && currentVolume == viewModel.maxVolume &&
            (currentMPVVolume ?: 100) - 100 < volumeBoostingCap && it < 0
        }
        val isDecreasingVolumeBoost: (Float) -> Boolean = {
          volumeBoostingCap > 0 && currentVolume == viewModel.maxVolume &&
            (currentMPVVolume ?: 100) - 100 in 1..volumeBoostingCap && it > 0
        }
        detectVerticalDragGestures(
          onDragEnd = { startingY = 0f },
          onDragStart = {
            startingY = 0f
            mpvVolumeStartingY = 0f
            originalVolume = currentVolume
            originalMPVVolume = currentMPVVolume
            originalBrightness = viewModel.gestureBrightness()
            brightnessStartY = it.y
            adjustingBrightness = brightnessGesture && (!volumeGesture ||
              if (swapVolumeAndBrightness) it.x > size.width / 2f else it.x < size.width / 2f)
          },
        ) { change, amount ->
          val changeVolume: () -> Unit = {
            if (isIncreasingVolumeBoost(amount) || isDecreasingVolumeBoost(amount)) {
              if (mpvVolumeStartingY == 0f) {
                startingY = 0f
                originalVolume = currentVolume
                mpvVolumeStartingY = change.position.y
              }
              viewModel.changeMPVVolumeTo(
                calculateNewVerticalGestureValue(
                  originalMPVVolume ?: 100,
                  mpvVolumeStartingY,
                  change.position.y,
                  mpvVolumeGestureSens,
                )
                  .coerceIn(100..volumeBoostingCap + 100),
              )
            } else {
              if (startingY == 0f) {
                mpvVolumeStartingY = 0f
                originalMPVVolume = currentMPVVolume
                startingY = change.position.y
              }
              viewModel.changeVolumeTo(
                calculateNewVerticalGestureValue(originalVolume, startingY, change.position.y, volumeGestureSens),
              )
            }
            viewModel.displayVolumeSlider()
          }
          val changeBrightness: () -> Unit = {
            viewModel.changeBrightnessTo(
              brightnessAfterDrag(originalBrightness, brightnessStartY, change.position.y, size.height.toFloat()),
            )
            viewModel.displayBrightnessSlider()
          }
          if (adjustingBrightness) changeBrightness() else if (volumeGesture) changeVolume()
        }
      },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoubleTapToSeekOvals(
  amount: Int,
  text: String?,
  showOvals: Boolean,
  showSeekIcon: Boolean,
  showSeekTime: Boolean,
  interactionSource: MutableInteractionSource,
  modifier: Modifier = Modifier,
) {
  val alpha by animateFloatAsState(if (amount == 0) 0f else 0.2f, label = "double_tap_animation_alpha")
  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = if (amount > 0) Alignment.CenterEnd else Alignment.CenterStart,
  ) {
    CompositionLocalProvider(
      LocalRippleConfiguration provides playerRippleConfiguration,
    ) {
      if (amount != 0) {
        Box(
          modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(0.4f), // 2 fifths
          contentAlignment = Alignment.Center,
        ) {
          if (showOvals) {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .clip(if (amount > 0) RightSideOvalShape else LeftSideOvalShape)
                .background(Color.White.copy(alpha))
                .indication(interactionSource, ripple()),
            )
          }
          if (showSeekIcon || showSeekTime) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              DoubleTapSeekTriangles(isForward = amount > 0)
              Text(
                text = text ?: pluralStringResource(R.plurals.seconds, amount, amount),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                color = Color.White,
              )
            }
          }
        }
      }
    }
  }
}

fun calculateNewVerticalGestureValue(originalValue: Int, startingY: Float, newY: Float, sensitivity: Float): Int {
  return originalValue + ((startingY - newY) * sensitivity).toInt()
}

fun calculateNewVerticalGestureValue(originalValue: Float, startingY: Float, newY: Float, sensitivity: Float): Float {
  return originalValue + ((startingY - newY) * sensitivity)
}

fun calculateNewHorizontalGestureValue(originalValue: Int, startingX: Float, newX: Float, sensitivity: Float): Int {
  return originalValue + ((newX - startingX) * sensitivity).toInt()
}

fun calculateNewHorizontalGestureValue(originalValue: Float, startingX: Float, newX: Float, sensitivity: Float): Float {
  return originalValue + ((newX - startingX) * sensitivity)
}

/** Full brightness range occupies one usable-height swipe, independent of density or rotation. */
internal fun brightnessAfterDrag(original: Float, startY: Float, currentY: Float, height: Float): Float {
  if (!original.isFinite() || !startY.isFinite() || !currentY.isFinite() || !height.isFinite() || height <= 0f) return .5f
  return (original + (startY - currentY) / height).coerceIn(0f, 1f)
}

/** Conservative hit region: ordinary video pinches remain video zoom outside active subtitle text. */
internal fun subtitlePinchTarget(x: Float, y: Float, primary: String, secondary: String, position: Int, secondarySupported: Boolean): Int {
  if(!x.isFinite() || !y.isFinite() || x !in .12f.. .88f) return 0
  val primaryY=.06f+.88f*(position.coerceIn(0,100)/100f)
  return when {
    secondarySupported && secondary.isNotBlank() && kotlin.math.abs(y-.1f)<.075f -> 2
    primary.isNotBlank() && kotlin.math.abs(y-primaryY)<.075f -> 1
    else -> 0
  }
}
