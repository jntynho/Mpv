package live.mehiz.mpvkt.ui.player

import java.io.File
import java.util.concurrent.atomic.AtomicLong
import live.mehiz.mpvkt.subtitles.*
import live.mehiz.mpvkt.preferences.SubtitlesPreferences
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.net.toUri
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import `is`.xyz.mpv.MPVLib
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.database.MpvKtDatabase
import live.mehiz.mpvkt.database.entities.CustomButtonEntity
import live.mehiz.mpvkt.preferences.AudioPreferences
import live.mehiz.mpvkt.preferences.GesturePreferences
import live.mehiz.mpvkt.preferences.PlayerPreferences
import live.mehiz.mpvkt.ui.custombuttons.CustomButtonsUiState
import live.mehiz.mpvkt.ui.custombuttons.getButtons
import org.koin.java.KoinJavaComponent.inject
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class PlayerViewModelProviderFactory(
  private val activity: PlayerActivity,
) : ViewModelProvider.Factory {
  override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
    return PlayerViewModel(activity) as T
  }
}

@Suppress("TooManyFunctions")
class PlayerViewModel(
  private var activity: PlayerActivity,
) : ViewModel() {
  fun attachActivity(current: PlayerActivity) {
    if (activity === current) return
    activity = current
    changeBrightnessTo(currentBrightness.value)
    viewModelScope.launch(Dispatchers.IO) {
      val buttons = _customButtons.value.getButtons()
      if (!current.isDestroyed && buttons.isNotEmpty()) current.setupCustomButtons(buttons)
    }
  }

  private val playerPreferences: PlayerPreferences by inject(PlayerPreferences::class.java)
  private val gesturePreferences: GesturePreferences by inject(GesturePreferences::class.java)
  private val subtitlesPreferences: SubtitlesPreferences by inject(SubtitlesPreferences::class.java)
  private val subtitleService: SubtitleService by inject(SubtitleService::class.java)
  private val audioPreferences: AudioPreferences by inject(AudioPreferences::class.java)
  private val mpvKtDatabase: MpvKtDatabase by inject(MpvKtDatabase::class.java)
  private val json: Json by inject(Json::class.java)


  private val _customButtons = MutableStateFlow<CustomButtonsUiState>(CustomButtonsUiState.Loading)
  val customButtons = _customButtons.asStateFlow()

  private val _primaryButton = MutableStateFlow<CustomButtonEntity?>(null)
  val primaryButton = _primaryButton.asStateFlow()

  private val _primaryButtonTitle = MutableStateFlow("")
  val primaryButtonTitle = _primaryButtonTitle.asStateFlow()

  internal val playback = PlaybackControl.state
  val paused get() = playback.value.displayPaused
  val pos by MPVLib.propDouble["time-pos"].map { it?.toInt() }.collectAsState(viewModelScope)
  val duration by MPVLib.propDouble["duration"].map { it?.toInt() }.collectAsState(viewModelScope)
  private val currentMPVVolume by MPVLib.propInt["volume"].collectAsState(viewModelScope)

  val currentVolume = MutableStateFlow(activity.audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
  private val volumeBoostCap by MPVLib.propInt["volume-max"].collectAsState(viewModelScope)

  val subtitleTracks = MPVLib.propNode["track-list"]
    .map { (it?.toObject<List<TrackNode>>(json)?.filter { it.isSubtitle } ?: persistentListOf()).toImmutableList() }

  val audioTracks = MPVLib.propNode["track-list"]
    .map { (it?.toObject<List<TrackNode>>(json)?.filter { it.isAudio } ?: persistentListOf()).toImmutableList() }

  val chapters = MPVLib.propNode["chapter-list"]
    .map { (it?.toObject<List<ChapterNode>>(json) ?: persistentListOf()).map { it.toSegment() }.toImmutableList() }

  private val _controlsShown = MutableStateFlow(true)
  val controlsShown = _controlsShown.asStateFlow()
  private val _seekBarShown = MutableStateFlow(true)
  val seekBarShown = _seekBarShown.asStateFlow()
  private val _areControlsLocked = MutableStateFlow(false)
  val areControlsLocked = _areControlsLocked.asStateFlow()

  val playerUpdate = MutableStateFlow<PlayerUpdates>(PlayerUpdates.None)
  val isBrightnessSliderShown = MutableStateFlow(false)
  val isVolumeSliderShown = MutableStateFlow(false)
  val currentBrightness = MutableStateFlow(
    runCatching {
      Settings.System.getFloat(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        .normalize(0f, 255f, 0f, 1f)
    }.getOrElse { .5f }.coerceIn(0f, 1f),
  )

  internal val panelNavigator = PlayerPanelNavigator()
  internal val subtitleSearch by lazy { SubtitleSearchController(viewModelScope,subtitleService,subtitlesPreferences::searchOptions) }
  private val subtitleVideoGeneration = AtomicLong()
  private val subtitleNavigationGeneration = AtomicLong()
  private var subtitleOwnerPath: String? = null
  private var subtitleOwnerGeneration = -1L
  internal fun subtitleMediaStarted() {
    subtitleVideoGeneration.incrementAndGet()
    subtitleSearch.stop()
    if(panelNavigator.state.value.lastOrNull()?.panel?.name?.startsWith("SubtitleSearch")==true) panelNavigator.openSheet(Sheets.SubtitleTracks)
  }
  internal fun closeSubtitleSearch() { subtitleNavigationGeneration.incrementAndGet();subtitleSearch.stop() }
  internal fun openSubtitleSearch(title: String, slot: Int) {
    val navigation=subtitleNavigationGeneration.get()
    val generation=subtitleVideoGeneration.get()
    PlaybackControl.enqueue("subtitle-search-context") {
      val path=MPVLib.getPropertyString("path") ?: return@enqueue
      viewModelScope.launch {
        if(navigation==subtitleNavigationGeneration.get() && generation==subtitleVideoGeneration.get() && panelNavigator.state.value.lastOrNull()?.sheet==Sheets.SubtitleTracks) {
          subtitleOwnerGeneration=generation;subtitleOwnerPath=path
          subtitleSearch.begin(title,slot,path);panelNavigator.openPanel(Panels.SubtitleSearch)
        }
      }
    }
  }
  internal fun backFromPlayerPanel() {
    val page=panelNavigator.state.value.lastOrNull()?.panel
    if(page==Panels.SubtitleSearch) subtitleSearch.stop()
    if(page==Panels.SubtitleSearchFiles) subtitleSearch.clearFiles()
    panelNavigator.back()
    if(page==Panels.SubtitleSearchOptions || page==Panels.SubtitleSearchLanguages) subtitleSearch.search()
  }
  internal fun applyOnlineSubtitle(file: File, slot: Int) {
    val generation=subtitleOwnerGeneration;val path=subtitleOwnerPath
    val folder=File(activity.filesDir,"subtitle-downloads")
    if(file.parentFile?.canonicalFile!=folder.canonicalFile || !file.isFile) return
    PlaybackControl.enqueue("online-subtitle-add") {
      var success=false
      try {
        if(generation!=subtitleVideoGeneration.get() || MPVLib.getPropertyString("path")!=path || !file.isFile) return@enqueue
        val oldPrimary=MPVLib.getPropertyInt("sid")
        MPVLib.command("sub-add",file.absolutePath,"cached")
        val tracks=MPVLib.getPropertyNode("track-list")?.toObject<List<TrackNode>>(json).orEmpty()
        val added=tracks.lastOrNull { it.isSubtitle && it.externalFilename?.removePrefix("file://")==file.absolutePath } ?: return@enqueue
        if(slot==1) {
          if(oldPrimary!=null && oldPrimary!=added.id) MPVLib.setPropertyInt("sid",oldPrimary) else MPVLib.setPropertyBoolean("sid",false)
          MPVLib.setPropertyInt("secondary-sid",added.id)
        } else {
          if(MPVLib.getPropertyInt("secondary-sid")==added.id) MPVLib.setPropertyBoolean("secondary-sid",false)
          MPVLib.setPropertyInt("sid",added.id)
        }
        success=MPVLib.getPropertyInt(if(slot==1) "secondary-sid" else "sid")==added.id
      } finally {
        viewModelScope.launch { if(generation==subtitleVideoGeneration.get()) subtitleSearch.reportApplied(success) }
      }
    }
  }

  internal fun probeSubtitleFeatures() {
    PlaybackControl.enqueue("subtitle-features") {
      SubtitleConfig.active=runCatching { File(activity.filesDir,"mpv.conf").inputStream().use { SubtitleConfig.options(SubtitleConfig.read(it)) } }.getOrDefault(emptySet())
      val supported=MPVLib.getPropertyDouble("secondary-sub-scale")!=null
      subtitlesPreferences.secondaryScaleSupported.set(supported)
      if(supported && "secondary-sub-scale" !in SubtitleConfig.active) MPVLib.setPropertyDouble("secondary-sub-scale",subtitlesPreferences.secondaryScale.get().toDouble())
    }
  }

  // Pair(startingPosition, seekAmount)
  val gestureSeekAmount = MutableStateFlow<Pair<Int, Int>?>(null)

  private val _seekText = MutableStateFlow<String?>(null)
  val seekText = _seekText.asStateFlow()
  private val _doubleTapSeekAmount = MutableStateFlow(0)
  val doubleTapSeekAmount = _doubleTapSeekAmount.asStateFlow()
  private val _isSeekingForwards = MutableStateFlow(false)
  val isSeekingForwards = _isSeekingForwards.asStateFlow()

  private var timerJob: Job? = null
  private val _remainingTime = MutableStateFlow(0)
  val remainingTime = _remainingTime.asStateFlow()

  fun startTimer(seconds: Int) {
    timerJob?.cancel()
    _remainingTime.value = seconds
    if (seconds < 1) return
    timerJob = viewModelScope.launch {
      for (time in seconds downTo 1) {
        _remainingTime.value = time
        delay(1000)
      }
      _remainingTime.value = 0
      pause()
      Toast.makeText(activity, activity.getString(R.string.toast_sleep_timer_ended), Toast.LENGTH_SHORT).show()
    }
  }

  fun cycleDecoders(current: Decoder) = setDecoder(when(current) {
    Decoder.HWPlus -> Decoder.HW
    Decoder.HW -> Decoder.SW
    Decoder.SW -> Decoder.HWPlus
    Decoder.AutoCopy, Decoder.Auto -> Decoder.SW
  })

  fun setDecoder(decoder: Decoder) {
    PlaybackControl.enqueue("decoder") { MPVLib.setPropertyString("hwdec", decoder.value) }
  }

  fun addAudio(uri: Uri) {
    val url = uri.toString()
    val path = if (url.startsWith("content://")) url.toUri().openContentFd(activity) else url
    MPVLib.command("audio-add", path ?: return, "cached")
  }

  fun addSubtitle(uri: Uri) {
    val url = uri.toString()
    val path = if (url.startsWith("content://")) url.toUri().openContentFd(activity) else url
    MPVLib.command("sub-add", path ?: return, "cached")
  }

  fun selectSub(id: Int, slot: Int) {
    PlaybackControl.enqueue("subtitle-$slot") {
      val property = if (slot == 1) "secondary-sid" else "sid"
      val other = if (slot == 1) "sid" else "secondary-sid"
      if (id >= 0 && MPVLib.getPropertyInt(other) == id) MPVLib.setPropertyBoolean(other, false)
      if (id < 0) MPVLib.setPropertyBoolean(property, false) else MPVLib.setPropertyInt(property, id)
    }
  }

  fun pauseUnpause() = PlaybackControl.toggle()
  fun pause() = PlaybackControl.pause()
  fun unpause() = PlaybackControl.play()
  fun beginSeek() = PlaybackControl.beginSeek()
  fun endSeek() = PlaybackControl.endSeek()

  private val showStatusBar = playerPreferences.showSystemStatusBar.get()
  fun showControls() {
    if (panelNavigator.isOpen) return
    if (showStatusBar) activity.windowInsetsController.show(WindowInsetsCompat.Type.statusBars())
    _controlsShown.update { true }
  }

  fun hideControls() {
    activity.windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())
    _controlsShown.update { false }
  }

  fun hideSeekBar() {
    _seekBarShown.update { false }
  }

  fun showSeekBar() {
    if (panelNavigator.isOpen) return
    _seekBarShown.update { true }
  }

  fun lockControls() {
    _areControlsLocked.update { true }
  }

  fun unlockControls() {
    _areControlsLocked.update { false }
  }

  fun seekBy(offset: Int, precise: Boolean = false) {
    PlaybackControl.enqueue { MPVLib.command("seek", offset.toString(), if (precise) "relative+exact" else "relative") }
  }

  fun seekTo(position: Int, precise: Boolean = true) {
    if (position < 0 || (duration != null && position > duration!!)) return
    PlaybackControl.enqueue("seek") { MPVLib.command("seek", position.toString(), if (precise) "absolute" else "absolute+keyframes") }
  }

  fun changeBrightnessBy(change: Float) {
    changeBrightnessTo(currentBrightness.value + change)
  }

  /** Read an explicit per-window override before starting a new swipe. */
  fun gestureBrightness(): Float = activity.window.attributes.screenBrightness
    .takeIf { it.isFinite() && it in 0f..1f } ?: currentBrightness.value

  fun changeBrightnessTo(
    brightness: Float,
  ) {
    if (!brightness.isFinite()) return
    val value = brightness.coerceIn(0f, 1f)
    val attributes = activity.window.attributes
    currentBrightness.value = value
    if (attributes.screenBrightness != value) {
      attributes.screenBrightness = value
      activity.window.attributes = attributes
    }
  }

  fun displayBrightnessSlider() {
    isBrightnessSliderShown.update { true }
  }

  val maxVolume = activity.audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
  fun changeVolumeBy(change: Int) {
    val mpvVolume = MPVLib.getPropertyInt("volume")
    if ((volumeBoostCap ?: audioPreferences.volumeBoostCap.get()) > 0 && currentVolume.value == maxVolume) {
      if (mpvVolume == 100 && change < 0) changeVolumeTo(currentVolume.value + change)
      val finalMPVVolume = (mpvVolume?.plus(change))?.coerceAtLeast(100) ?: 100
      if (finalMPVVolume in 100..(volumeBoostCap ?: audioPreferences.volumeBoostCap.get()) + 100) {
        changeMPVVolumeTo(finalMPVVolume)
        return
      }
    }
    changeVolumeTo(currentVolume.value + change)
  }

  fun changeVolumeTo(volume: Int) {
    val newVolume = volume.coerceIn(0..maxVolume)
    activity.audioManager.setStreamVolume(
      AudioManager.STREAM_MUSIC,
      newVolume,
      0,
    )
    currentVolume.update { newVolume }
  }

  fun changeMPVVolumeTo(volume: Int) {
    PlaybackControl.enqueue("volume") { MPVLib.setPropertyInt("volume", volume) }
  }

  fun setMPVVolume(volume: Int) {
    if (volume != currentMPVVolume) displayVolumeSlider()
  }

  fun displayVolumeSlider() {
    isVolumeSliderShown.update { true }
  }

  fun changeVideoAspect(aspect: VideoAspect, notify: Boolean = true) {
    var ratio = -1.0
    var pan = 1.0
    when (aspect) {
      VideoAspect.Crop -> {
        pan = 1.0
      }

      VideoAspect.Fit -> {
        pan = 0.0
        MPVLib.setPropertyDouble("panscan", 0.0)
      }

      VideoAspect.Stretch -> {
        val dm = DisplayMetrics()
        activity.windowManager.defaultDisplay.getRealMetrics(dm)
        val width = activity.player.width.takeIf { it > 0 } ?: dm.widthPixels
        val height = activity.player.height.takeIf { it > 0 } ?: dm.heightPixels
        ratio = if (height > 0) width / height.toDouble() else -1.0
        pan = 0.0
      }
    }
    MPVLib.setPropertyDouble("panscan", pan)
    MPVLib.setPropertyDouble("video-aspect-override", ratio)
    playerPreferences.videoAspect.set(aspect)
    if (notify) playerUpdate.update { PlayerUpdates.AspectRatio }
  }

  fun cycleScreenRotations() {
    val next = nextManualRotation(activity.resources.configuration.orientation)
    playerPreferences.orientation.set(next)
    orientationRequest(next, activity.player.getVideoOutAspect())?.let { activity.requestedOrientation = it }
  }

  @Suppress("CyclomaticComplexMethod", "LongMethod")
  fun handleLuaInvocation(property: String, value: String) {
    val data = value
      .removePrefix("\"")
      .removeSuffix("\"")
      .ifEmpty { return }

    when (property.substringAfterLast("/")) {
      "show_text" -> playerUpdate.update { PlayerUpdates.ShowText(data) }
      "toggle_ui" -> {
        when (data) {
          "show" -> showControls()
          "toggle" -> {
            if (controlsShown.value) hideControls() else showControls()
          }

          "hide" -> {
            panelNavigator.dismiss()
            hideControls()
          }
        }
      }

      "show_panel" -> {
        when (data) {
          "subtitle_settings" -> panelNavigator.openPanel(Panels.SubtitleSettings)
          "subtitle_delay" -> panelNavigator.openPanel(Panels.SubtitleDelay)
          "audio_delay" -> panelNavigator.openPanel(Panels.AudioDelay)
          "video_filters" -> panelNavigator.openPanel(Panels.VideoFilters)
        }
      }

      "set_button_title" -> {
        _primaryButtonTitle.update { _ -> data }
      }

      "reset_button_title" -> {
        _customButtons.value.getButtons().firstOrNull { it.id == playerPreferences.primaryCustomButtonId.get() }?.let {
          setPrimaryCustomButtonTitle(it)
        }
      }

      "seek_to_with_text" -> {
        val (seekValue, text) = data.split("|", limit = 2)
        seekToWithText(seekValue.toInt(), text)
      }

      "seek_by_with_text" -> {
        val (seekValue, text) = data.split("|", limit = 2)
        seekByWithText(seekValue.toInt(), text)
      }

      "seek_by" -> seekByWithText(data.toInt(), null)
      "seek_to" -> seekToWithText(data.toInt(), null)
      "toggle_button" -> {
        fun showButton() {
          if (_primaryButton.value == null) {
            _primaryButton.update {
              customButtons.value.getButtons().firstOrNull { it.id == playerPreferences.primaryCustomButtonId.get() }
            }
          }
        }
        when (data) {
          "show" -> showButton()
          "toggle" if _primaryButton.value != null -> showButton()
          else -> _primaryButton.update { null }
        }
      }

      "software_keyboard" -> when (data) {
        "show" -> forceShowSoftwareKeyboard()
        "hide" -> forceHideSoftwareKeyboard()
        "toggle" if !inputMethodManager.isActive -> forceShowSoftwareKeyboard()
        else -> forceHideSoftwareKeyboard()
      }
    }

    MPVLib.setPropertyString(property, "")
  }

  private val inputMethodManager get() = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
  private fun forceShowSoftwareKeyboard() {
    inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
  }

  private fun forceHideSoftwareKeyboard() {
    inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0)
  }

  private fun seekToWithText(seekValue: Int, text: String?) {
    _isSeekingForwards.value = seekValue > 0
    _doubleTapSeekAmount.value = seekValue - (pos ?: return)
    _seekText.update { text }
    seekTo(seekValue, playerPreferences.preciseSeeking.get())
    if (playerPreferences.showSeekBarWhenSeeking.get()) showSeekBar()
  }

  private fun seekByWithText(value: Int, text: String?) {
    _doubleTapSeekAmount.update {
      if (value < 0 && it < 0 || (pos ?: return) + value > (duration ?: return)) 0 else it + value
    }
    _seekText.update { text }
    _isSeekingForwards.value = value > 0
    seekBy(value, playerPreferences.preciseSeeking.get())
    if (playerPreferences.showSeekBarWhenSeeking.get()) showSeekBar()
  }

  private val doubleTapToSeekDuration = gesturePreferences.doubleTapToSeekDuration.get()

  fun updateSeekAmount(amount: Int) {
    _doubleTapSeekAmount.update { amount }
  }

  fun updateSeekText(text: String?) {
    _seekText.update { text }
  }

  fun leftSeek() {
    if ((pos ?: return) > 0) _doubleTapSeekAmount.value -= doubleTapToSeekDuration
    _isSeekingForwards.value = false
    seekBy(-doubleTapToSeekDuration, playerPreferences.preciseSeeking.get())
    if (playerPreferences.showSeekBarWhenSeeking.get()) showSeekBar()
  }

  fun rightSeek() {
    if ((pos ?: return) < (duration ?: return)) {
      _doubleTapSeekAmount.value += doubleTapToSeekDuration
    }
    _isSeekingForwards.value = true
    seekBy(doubleTapToSeekDuration, playerPreferences.preciseSeeking.get())
    if (playerPreferences.showSeekBarWhenSeeking.get()) showSeekBar()
  }

  fun handleLeftDoubleTap() {
    when (gesturePreferences.leftSingleActionGesture.get()) {
      SingleActionGesture.Seek -> {
        leftSeek()
      }

      SingleActionGesture.PlayPause -> {
        pauseUnpause()
      }

      SingleActionGesture.Custom -> {
        MPVLib.command("keypress", CustomKeyCodes.DoubleTapLeft.keyCode)
      }

      SingleActionGesture.None -> {}
    }
  }

  fun handleCenterDoubleTap() {
    when (gesturePreferences.centerSingleActionGesture.get()) {
      SingleActionGesture.PlayPause -> {
        pauseUnpause()
      }

      SingleActionGesture.Custom -> {
        MPVLib.command("keypress", CustomKeyCodes.DoubleTapCenter.keyCode)
      }

      SingleActionGesture.Seek -> {}
      SingleActionGesture.None -> {}
    }
  }

  fun handleRightDoubleTap() {
    when (gesturePreferences.rightSingleActionGesture.get()) {
      SingleActionGesture.Seek -> {
        rightSeek()
      }

      SingleActionGesture.PlayPause -> {
        pauseUnpause()
      }

      SingleActionGesture.Custom -> {
        MPVLib.command("keypress", CustomKeyCodes.DoubleTapRight.keyCode)
      }

      SingleActionGesture.None -> {}
    }
  }

  fun setPrimaryCustomButtonTitle(button: CustomButtonEntity) {
    _primaryButtonTitle.update { _ -> button.title }
  }
  init {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val buttons = mpvKtDatabase.customButtonDao().getCustomButtons().first()
        buttons.firstOrNull { it.id == playerPreferences.primaryCustomButtonId.get() }?.let {
          _primaryButton.update { _ -> it }
          // If the button text is not empty, it has been set buy a lua script in which
          // case we don't want to override it
          if (_primaryButtonTitle.value.isEmpty()) setPrimaryCustomButtonTitle(it)
        }
        activity.setupCustomButtons(buttons)
        _customButtons.update { _ -> CustomButtonsUiState.Success(buttons) }
      } catch (e: Exception) {
        Log.e(TAG, e.message ?: "Unable to fetch buttons")
        _customButtons.update { _ -> CustomButtonsUiState.Error(e.message ?: "Unable to fetch buttons") }
      }
    }
  }


}

fun Float.normalize(inMin: Float, inMax: Float, outMin: Float, outMax: Float): Float {
  return (this - inMin) * (outMax - outMin) / (inMax - inMin) + outMin
}

fun CustomButtonEntity.execute() {
  MPVLib.command("script-message", "call_button_$id")
}

fun CustomButtonEntity.executeLongClick() {
  MPVLib.command("script-message", "call_button_${id}_long")
}

fun <T> Flow<T>.collectAsState(scope: CoroutineScope, initialValue: T? = null) =
  object : ReadOnlyProperty<Any?, T?> {
    private var value: T? = initialValue
    init { scope.launch { collect { value = it } } }
    override fun getValue(thisRef: Any?, property: KProperty<*>) = value
  }
