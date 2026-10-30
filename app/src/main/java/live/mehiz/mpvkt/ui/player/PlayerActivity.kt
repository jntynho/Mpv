// Modified for Luma: compatible back navigation and private PiP receiver.
package live.mehiz.mpvkt.ui.player

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import android.util.Rational
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.addCallback
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAndroidRect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.core.net.toUri
import androidx.core.text.isDigitsOnly
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import com.github.k1rakishou.fsaf.FileManager
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVNode
import `is`.xyz.mpv.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import live.mehiz.mpvkt.database.entities.CustomButtonEntity
import live.mehiz.mpvkt.database.entities.PlaybackStateEntity
import live.mehiz.mpvkt.databinding.PlayerLayoutBinding
import live.mehiz.mpvkt.domain.playbackstate.repository.PlaybackStateRepository
import live.mehiz.mpvkt.preferences.AdvancedPreferences
import live.mehiz.mpvkt.preferences.AudioPreferences
import live.mehiz.mpvkt.preferences.GesturePreferences
import live.mehiz.mpvkt.preferences.AppearancePreferences
import live.mehiz.mpvkt.preferences.PlayerPreferences
import live.mehiz.mpvkt.preferences.SubtitlesPreferences
import live.mehiz.mpvkt.ui.player.controls.PlayerControls
import live.mehiz.mpvkt.ui.theme.MpvKtTheme
import live.mehiz.mpvkt.ui.icons.IconVectors
import org.koin.android.ext.android.inject
import java.io.File

@Suppress("TooManyFunctions", "LargeClass")
class PlayerActivity : AppCompatActivity() {

  private val viewModel: PlayerViewModel by viewModels<PlayerViewModel> { PlayerViewModelProviderFactory(this) }
  private val binding by lazy { PlayerLayoutBinding.inflate(layoutInflater) }
  private val playerObserver by lazy { PlayerObserver(this) }
  private val playbackStateRepository: PlaybackStateRepository by inject()
  val player by lazy { binding.player }
  val windowInsetsController by lazy { WindowCompat.getInsetsController(window, window.decorView) }
  val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }
  private var mediaSession: MediaSession? = null
  private val appearancePreferences: AppearancePreferences by inject()
  private val playerPreferences: PlayerPreferences by inject()
  private val audioPreferences: AudioPreferences by inject()
  private val json: kotlinx.serialization.json.Json by inject()
  private val subtitlesPreferences: SubtitlesPreferences by inject()
  private val advancedPreferences: AdvancedPreferences by inject()
  private val gesturePreferences: GesturePreferences by inject()
  private val fileManager: FileManager by inject()

  private val securePlayback: Boolean
    get() = live.mehiz.mpvkt.ui.secure.SecurePlaybackRegistry.file(intent.getStringExtra("secure_session")) != null
  private var fileName = ""
  private var mediaPlaybackService: MediaPlaybackService? = null
  private var serviceBinding = false
  private var pipTransitionExpected = false
  private var restoreJob: Job? = null
  private var requestDefaults: Map<String, String?> = emptyMap()
  private var mediaGeneration = 0L

  private var audioFocusRequest: AudioFocusRequestCompat? = null
  private var volumeBeforeDuck: Double? = null

  private var pipRect: android.graphics.Rect? = null
  val isPipSupported: Boolean get() = !securePlayback && platformPipSupported
  private val platformPipSupported by lazy {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
      false
    } else {
      packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }
  }
  private var pipReceiver: BroadcastReceiver? = null

  private val noisyReceiver = object : BroadcastReceiver() {
    var initialized = false
    override fun onReceive(context: Context?, intent: Intent?) {
      if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
        viewModel.pause()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    if (securePlayback) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
    onBackPressedDispatcher.addCallback(this) {
      if (viewModel.panelNavigator.isOpen) {
        viewModel.backFromPlayerPanel()
        if (!viewModel.panelNavigator.isOpen) viewModel.showControls()
        return@addCallback
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isPipSupported &&
        viewModel.paused == false && !securePlayback && playerPreferences.automaticallyEnterPip.get() &&
        !viewModel.panelNavigator.isOpen) {
        if (tryEnterPip()) return@addCallback
      }
      run {
        isEnabled = false
        onBackPressedDispatcher.onBackPressed()
        isEnabled = true
      }
    }
    lifecycleScope.launch {
      try { IconVectors.prepare(applicationContext.resources, appearancePreferences.iconStyle.get()) }
      catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
      catch (_: Exception) { /* On-demand resource loading remains available. */ }
    }
    setContentView(binding.root)

    setupMPV()
    PlaybackControl.attach(this)
    viewModel.probeSubtitleFeatures()
    viewModel.attachActivity(this)
    requestDefaults = listOf("force-media-title", "user-agent", "http-header-fields").associateWith { MPVLib.getPropertyString(it) }
    applyRequestOptions(intent.extras)
    setupAudio()
    setupMediaSession()
    getPlayableUri(intent)?.let(player::playFile)
    setOrientation()

    // Observe appearance without recreating the Activity or touching the video surface.
    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        launch { appearancePreferences.iconStyle.changes().collect { updatePipParams() } }
        launch { PlaybackControl.state.map { it.displayPaused }.distinctUntilChanged().collect { updatePipParams() } }
      }
    }
    binding.controls.setContent {
      MpvKtTheme {
        PlayerControls(
          viewModel = viewModel,
          onBackPress = onBackPressedDispatcher::onBackPressed,
          modifier = Modifier.onGloballyPositioned {
            pipRect = it.boundsInWindow().toAndroidRect()
          },
        )
      }
    }
  }

  private fun getPlayableUri(intent: Intent): String? = runCatching {
    val uri = parsePathFromIntent(intent)
    if (uri?.startsWith("content://") == true) uri.toUri().openContentFd(this) else uri
  }.getOrElse { Log.e(TAG, "Cannot open media URI", it); null }

  override fun onDestroy() {
    Log.d(TAG, "Exiting")
    val oldFocusRequest = audioFocusRequest
    audioFocusRequest = null
    oldFocusRequest?.let { AudioManagerCompat.abandonAudioFocusRequest(audioManager, it) }
    mediaSession?.release()
    if (noisyReceiver.initialized) {
      unregisterReceiver(noisyReceiver)
      noisyReceiver.initialized = false
    }

    if (serviceBinding) endBackgroundPlayback()
    player.isExiting = true
    if (isFinishing) {
      MPVLib.command("stop")
    }
    PlaybackControl.detach(this)
    MPVLib.removeObserver(playerObserver)
    MpvProperties.detach(this)
    playerObserver.release()
    player.destroy()

    restoreJob?.cancel()
    pipReceiver?.let { unregisterReceiver(it); pipReceiver = null }
    if (isFinishing) {
      live.mehiz.mpvkt.ui.secure.SecurePlaybackRegistry.remove(intent.getStringExtra("secure_session"))
    }
    super.onDestroy()
  }

  override fun onPause() {
    if (!pipTransitionExpected && !(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) &&
      (!playerPreferences.automaticBackgroundPlayback.get() || securePlayback)
    ) {
      viewModel.pause()
    }
    saveVideoPlaybackState(fileName)
    super.onPause()
  }

  override fun finish() {
    setReturnIntent()
    super.finish()
  }

  override fun onStop() {
    saveVideoPlaybackState(fileName)
    if (!isFinishing && !isChangingConfigurations && !securePlayback && playerPreferences.automaticBackgroundPlayback.get()) {
      if (!serviceBinding) startBackgroundPlayback()
    } else if (!(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode)) {
      viewModel.pause()
      if (serviceBinding) endBackgroundPlayback()
    }
    window.attributes.screenBrightness.let {
      if (playerPreferences.rememberBrightness.get() && it != -1f) {
        playerPreferences.defaultBrightness.set(it)
      }
    }
    super.onStop()
  }

  @SuppressLint("NewApi")
  override fun onUserLeaveHint() {
    val autoPip = isPipSupported && viewModel.paused == false && !securePlayback && playerPreferences.automaticallyEnterPip.get()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) pipTransitionExpected = autoPip
    else if (autoPip) tryEnterPip()
    super.onUserLeaveHint()
  }


  override fun onStart() {
    super.onStart()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isPipSupported) {
      setPictureInPictureParams(createPipParams())
    }
    WindowCompat.setDecorFitsSystemWindows(window, false)
    window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    binding.root.systemUiVisibility =
      View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
      View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
      View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
      View.SYSTEM_UI_FLAG_LOW_PROFILE
    windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars())
    windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      window.attributes.layoutInDisplayCutoutMode = if (playerPreferences.drawOverDisplayCutout.get()) {
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
      } else {
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
      }
    }

    if (playerPreferences.rememberBrightness.get()) {
      playerPreferences.defaultBrightness.get().let {
        if (it != -1f) viewModel.changeBrightnessTo(it)
      }
    }

    if (serviceBinding) {
      endBackgroundPlayback()
      setupAudio()
    }
    mediaSession?.isActive = !securePlayback
  }

  private fun copyMPVAssets() {
    Utils.copyAssets(this@PlayerActivity)
    copyMPVScripts()
    copyMPVConfigFiles()
    // fonts can be lazily loaded
    lifecycleScope.launch(Dispatchers.IO) {
      try { org.koin.java.KoinJavaComponent.get<live.mehiz.mpvkt.subtitles.SubtitleFonts>(live.mehiz.mpvkt.subtitles.SubtitleFonts::class.java).prepare(subtitlesPreferences.fontsFolder.get()) }
      catch(c: kotlinx.coroutines.CancellationException) { throw c }
      catch(_: Exception) { Log.w(TAG,"Subtitle font directory unavailable") }
    }
  }

  private fun setupMPV() {
    copyMPVAssets()
    player.initialize(filesDir.path, cacheDir.path)
    MpvProperties.attach(this)
    MPVLib.addObserver(playerObserver)
  }

  private fun setupAudio() {
    audioPreferences.audioChannels.get().let { MPVLib.setPropertyString(it.property, it.value) }

    val request = AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN).also {
      it.setAudioAttributes(
        AudioAttributesCompat.Builder().setUsage(AudioAttributesCompat.USAGE_MEDIA)
          .setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC).build(),
      )
      it.setOnAudioFocusChangeListener(audioFocusChangeListener)
    }.build()
    audioFocusRequest = request
    PlaybackControl.focusRequester(this) {
      AudioManagerCompat.requestAudioFocus(audioManager, request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }
    if (AudioManagerCompat.requestAudioFocus(audioManager, request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
      PlaybackControl.focusGained()
    } else PlaybackControl.focusLost(transient = true)
  }

  private fun copyMPVConfigFiles() {
    val applicationPath = filesDir.path
    try {
      val mpvConf = fileManager.fromUri(advancedPreferences.mpvConfStorageUri.get().toUri())
        ?: error("User hasn't set any mpvConfig directory")
      if (!fileManager.exists(mpvConf)) error("Couldn't access mpv configuration directory")
      fileManager.copyDirectoryWithContent(mpvConf, fileManager.fromPath(applicationPath), true)
    } catch (e: Exception) {
      File("$applicationPath/mpv.conf")
        .also { if (!it.exists()) it.createNewFile() }
        .writeText(advancedPreferences.mpvConf.get())
      File("$applicationPath/input.conf")
        .also { if (!it.exists()) it.createNewFile() }
        .writeText(advancedPreferences.inputConf.get())
      Log.e("PlayerActivity", "Couldn't copy mpv configuration files: ${e.message}")
    }
  }

  private fun copyMPVScripts() {
    val mpvktLua = assets.open("mpvkt.lua")
    val applicationPath = filesDir.path

    val scriptsDir = fileManager.createDir(fileManager.fromPath(applicationPath), "scripts")!!

    fileManager.deleteContent(scriptsDir)

    File("$scriptsDir/mpvkt.lua")
      .also { if (!it.exists()) it.createNewFile() }
      .writeText(mpvktLua.bufferedReader().readText())
  }

  fun setupCustomButtons(buttons: List<CustomButtonEntity>) {
    val applicationPath = filesDir.path

    val scriptsDir = fileManager.createDir(fileManager.fromPath(applicationPath), "scripts")!!

    val customButtonsContent = buildString {
      appendLine("local lua_modules = mp.find_config_file('scripts')")
      appendLine("if lua_modules then")
      appendLine("package.path = package.path .. ';' .. lua_modules .. '/?.lua;' .. lua_modules .. '/?/init.lua'")
      appendLine("end")
      appendLine("local mpvkt = require 'mpvkt'")
      buttons.forEach { button ->
        appendLine("function button${button.id}()")
        appendLine(button.content)
        appendLine("end")
        appendLine("mp.register_script_message('call_button_${button.id}', button${button.id})")
        appendLine("function button${button.id}long()")
        appendLine(button.longPressContent)
        appendLine("end")
        appendLine("mp.register_script_message('call_button_${button.id}_long', button${button.id}long)")
      }
    }

    val file = File("$scriptsDir/custombuttons.lua")
      .also { if (!it.exists()) it.createNewFile() }

    file.writeText(customButtonsContent)

    MPVLib.command("load-script", file.absolutePath)
  }


  private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { change ->
    if (audioFocusRequest == null) return@OnAudioFocusChangeListener
    when (change) {
      AudioManager.AUDIOFOCUS_LOSS -> PlaybackControl.focusLost(transient = false)
      AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
        PlaybackControl.focusLost(transient = true)
      }
      AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
        if (volumeBeforeDuck == null) {
          volumeBeforeDuck = MPVLib.getPropertyDouble("volume")
          volumeBeforeDuck?.let { MPVLib.setPropertyDouble("volume", it * .5) }
        }
      }
      AudioManager.AUDIOFOCUS_GAIN -> {
        volumeBeforeDuck?.let { MPVLib.setPropertyDouble("volume", it) }
        volumeBeforeDuck = null
        PlaybackControl.focusGained()
      }
    }
  }

  override fun onResume() {
    super.onResume()
    pipTransitionExpected = false

    viewModel.currentVolume.update {
      audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).also {
        if (it < viewModel.maxVolume) viewModel.changeMPVVolumeTo(100)
      }
    }
  }

  private val serviceConnection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
      if (!serviceBinding || isFinishing || isDestroyed) return
      val binder = service as MediaPlaybackService.MediaPlaybackBinder
      mediaPlaybackService = binder.getService()

      fileName.let { title ->
        val artist = MPVLib.getPropertyString("metadata/artist") ?: ""
        mediaPlaybackService?.setMediaInfo(title = title, artist = artist, thumbnail = MPVLib.grabThumbnail(1080))
      }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
      mediaPlaybackService = null
    }
  }

  private fun startBackgroundPlayback() {
    if (securePlayback) return
    val previousFocus = audioFocusRequest
    audioFocusRequest = null
    PlaybackControl.clearFocusRequester(this)
    previousFocus?.let { AudioManagerCompat.abandonAudioFocusRequest(audioManager, it) }
    val intent = Intent(this, MediaPlaybackService::class.java)
    try {
      androidx.core.content.ContextCompat.startForegroundService(this, intent)
      serviceBinding = bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
      mediaSession?.isActive = false
    } catch (error: RuntimeException) {
      endBackgroundPlayback()
      viewModel.pause()
      Log.e(TAG, "Background playback was denied by the system", error)
    }
  }

  private fun endBackgroundPlayback() {
    mediaPlaybackService?.releasePlaybackFocus()
    if (serviceBinding) unbindService(serviceConnection)
    serviceBinding = false
    stopService(Intent(this, MediaPlaybackService::class.java))
    mediaPlaybackService = null
  }

  private fun setIntentExtras(extras: Bundle?) {
    if (extras == null) return

    MPVLib.setPropertyInt("time-pos", extras.getInt("position", 0) / 1000)

    // subtitles
    if (extras.containsKey("subs")) {
      val subList = Utils.getParcelableArray<Uri>(extras, "subs")
      val subsToEnable = Utils.getParcelableArray<Uri>(extras, "subs.enable")

      for (suburi in subList) {
        val subfile = suburi.resolveUri(this) ?: continue
        val flag = if (subsToEnable.any { it == suburi }) "select" else "auto"

        Log.v(TAG, "Adding subtitles from intent extras: $subfile")
        MPVLib.command("sub-add", subfile, flag)
      }
    }


  }

  private fun applyRequestOptions(extras: Bundle?) {
    requestDefaults.forEach { (key, value) -> value?.let { MPVLib.setPropertyString(key, it) } }
    extras?.getString("title")?.let { MPVLib.setPropertyString("force-media-title", it) }
    extras?.getStringArray("headers")?.let {
      val (agent, fields) = parseHttpHeaders(it)
      agent?.let { value -> MPVLib.setPropertyString("user-agent", value) }
      MPVLib.setPropertyString("http-header-fields", fields)
    }
  }

  @Suppress("NestedBlockDepth")
  private fun parsePathFromIntent(intent: Intent): String? {
    return when (intent.action) {
      Intent.ACTION_VIEW -> intent.data?.resolveUri(this)
      Intent.ACTION_SEND -> {
        if (intent.hasExtra(Intent.EXTRA_STREAM)) {
          intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.resolveUri(this)
        } else {
          intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
            val uri = it.trim().toUri()
            if (uri.isHierarchical && !uri.isRelative) uri.resolveUri(this) else null
          }
        }
      }

      else -> intent.getStringExtra("uri")
    }
  }

  private fun getFileName(intent: Intent): String {
    if (securePlayback) return intent.getStringExtra("secure_title").orEmpty().take(300)
    val uri = if (intent.type == "text/plain") {
      intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.toUri()
    } else {
      (intent.data ?: intent.getParcelableExtra(Intent.EXTRA_STREAM))
    }
    if (uri?.scheme == "content") {
      runCatching {
        contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
          if (cursor.moveToFirst()) return cursor.getString(0).orEmpty()
        }
      }
    }
    return uri?.lastPathSegment?.substringAfterLast("/") ?: uri?.path ?: ""
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) {
      viewModel.hideControls()
    } else binding.player.post {
      if (!isDestroyed && !player.isExiting) {
        viewModel.changeVideoAspect(playerPreferences.videoAspect.get(), notify = false)
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        updatePipParams()
      }
    }
  }

  private fun updatePipParams() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isPipSupported && !isDestroyed) {
      runCatching { setPictureInPictureParams(createPipParams()) }
    }
  }

  // a bunch of observers
  @Suppress("UnusedParameter")
  internal fun onObserverEvent(property: String, value: Long) {
    if (player.isExiting) return
    if (property == "video-params/rotate") {
      if (playerPreferences.orientation.get() == PlayerOrientation.Video) setOrientation()
      updatePipParams()
    }
  }

  @Suppress("UnusedParameter")
  internal fun onObserverEvent(property: String) {
    if (player.isExiting) return
  }

  internal fun onObserverEvent(property: String, value: Boolean) {
    if (player.isExiting) return
    when (property) {
      "pause" if value -> window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      "pause" -> window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      "eof-reached" if value && playerPreferences.closeAfterReachingEndOfVideo.get() -> finishAndRemoveTask()
    }
    if (property == "pause") updatePipParams()
  }

  internal fun onObserverEvent(property: String, value: String) {
    if (player.isExiting) return
    when (property.substringBeforeLast("/")) {
      "user-data/mpvkt" -> viewModel.handleLuaInvocation(property, value)
    }
  }

  @Suppress("UnusedParameter")
  internal fun onObserverEvent(property: String, value: MPVNode) {
    if (player.isExiting) return
  }

  @SuppressLint("NewApi")
  @Suppress("UnusedParameter")
  internal fun onObserverEvent(property: String, value: Double) {
    if (player.isExiting) return
    when (property) {
      "video-params/aspect" -> {
        if (playerPreferences.orientation.get() == PlayerOrientation.Video) setOrientation()
        updatePipParams()
      }
    }
  }

  internal fun event(eventId: Int) {
    if (player.isExiting) return
    when (eventId) {
      MPVLib.mpvEventId.MPV_EVENT_START_FILE -> viewModel.subtitleMediaStarted()
      MPVLib.mpvEventId.MPV_EVENT_FILE_LOADED -> {
        fileName = getFileName(intent)
        val privateMedia = securePlayback
        val sourceHint = intent.data?.toString()
        val recentTitle = fileName
        val recentContext = applicationContext
        PlaybackControl.enqueue("recent-media") {
          val path = MPVLib.getPropertyString("path")
          val uri = if(path == null || path.startsWith("fd") || path.startsWith("/proc/self/fd")) sourceHint else path
          if(uri != null && !privateMedia) playbackWriterScope.launch { live.mehiz.mpvkt.ui.home.RecentMedia.record(recentContext,uri,recentTitle) }
        }
        setIntentExtras(intent.extras)
        val mediaTitle = MPVLib.getPropertyString("media-title")
        if (mediaTitle.isNullOrBlank() || mediaTitle.isDigitsOnly()) {
          MPVLib.setPropertyString("media-title", fileName)
        }
        restoreJob?.cancel()
        val generation = ++mediaGeneration
        val title = fileName
        restoreJob = lifecycleScope.launch { loadVideoPlaybackState(title, generation) }
        setOrientation()
        viewModel.changeVideoAspect(playerPreferences.videoAspect.get())
      }

      MPVLib.mpvEventId.MPV_EVENT_PLAYBACK_RESTART -> player.isExiting = false
    }
  }

  private fun saveVideoPlaybackState(mediaTitle: String) {
    if (mediaTitle.isBlank() || securePlayback) return
    // Read native values on the owner thread before dispatching database I/O.
    val snapshot = PlaybackStateEntity(
      mediaTitle = mediaTitle,
      lastPosition = if (playerPreferences.savePositionOnQuit.get()) {
        val pos = viewModel.pos ?: 0
        val duration = viewModel.duration ?: 0
        if (duration <= 0 || pos < duration - 1) pos else 0
      } else 0,
      playbackSpeed = MPVLib.getPropertyDouble("speed") ?: 1.0,
      sid = player.sid,
      subDelay = ((MPVLib.getPropertyDouble("sub-delay") ?: 0.0) * 1000).toInt(),
      subSpeed = MPVLib.getPropertyDouble("sub-speed") ?: 1.0,
      secondarySid = player.secondarySid,
      secondarySubDelay = ((MPVLib.getPropertyDouble("secondary-sub-delay") ?: 0.0) * 1000).toInt(),
      aid = player.aid,
      audioDelay = ((MPVLib.getPropertyDouble("audio-delay") ?: 0.0) * 1000).toInt(),
    )
    val savePosition = playerPreferences.savePositionOnQuit.get()
    val repository = playbackStateRepository
    playbackWriterScope.launch {
      playbackWriteMutex.withLock {
        val oldState = if (!savePosition) repository.getVideoDataByTitle(mediaTitle) else null
        repository.upsert(if (savePosition) snapshot else snapshot.copy(lastPosition = oldState?.lastPosition ?: 0))
      }
    }
  }

  private suspend fun loadVideoPlaybackState(mediaTitle: String, generation: Long) {
    if (mediaTitle.isBlank()) return
    val state = if (securePlayback) null else withContext(Dispatchers.IO) { playbackStateRepository.getVideoDataByTitle(mediaTitle) }
    if (generation != mediaGeneration || isDestroyed || player.isExiting) return
    if(state==null && !intent.hasExtra("subs.enable") && "slang" !in live.mehiz.mpvkt.subtitles.SubtitleConfig.active) {
      val tracks=MPVLib.getPropertyNode("track-list")?.toObject<List<TrackNode>>(json).orEmpty()
      live.mehiz.mpvkt.subtitles.preferredSubtitleTrack(tracks,subtitlesPreferences.preferredLanguages.get())?.let { player.sid=it }
    }
    state?.let {
      player.sid = it.sid
      player.secondarySid = it.secondarySid
      player.aid = it.aid
    }
    restoreProperties(state, subtitlesPreferences.defaultSubDelay.get(), subtitlesPreferences.defaultSecondarySubDelay.get(),
      audioPreferences.defaultAudioDelay.get(), subtitlesPreferences.defaultSubSpeed.get().toDouble(), playerPreferences.defaultSpeed.get().toDouble()).forEach { (key, value) ->
      MPVLib.setPropertyDouble(key, value)
    }
    if (playerPreferences.savePositionOnQuit.get() && !intent.hasExtra("position")) {
      state?.lastPosition?.let { if (it > 0) MPVLib.setPropertyInt("time-pos", it) }
    }
  }

  private fun setReturnIntent() {
    Log.d(TAG, "setting return intent")
    setResult(
      RESULT_OK,
      Intent(RESULT_INTENT).apply {
        viewModel.pos?.let { putExtra("position", it * 1000) }
        viewModel.duration?.let { putExtra("duration", it * 1000) }
      },
    )
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)

    val playable = getPlayableUri(intent) ?: return
    saveVideoPlaybackState(fileName)
    restoreJob?.cancel()
    mediaGeneration++
    val oldSession = this.intent.getStringExtra("secure_session")
    if (oldSession != intent.getStringExtra("secure_session")) {
      live.mehiz.mpvkt.ui.secure.SecurePlaybackRegistry.remove(oldSession)
    }
    setIntent(intent)
    if (securePlayback) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
    else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
    mediaSession?.isActive = !securePlayback
    applyRequestOptions(intent.extras)
    MPVLib.command("loadfile", playable)
  }

  private fun tryEnterPip(): Boolean {
    if (securePlayback || Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !isPipSupported || isFinishing) return false
    pipTransitionExpected = true
    return runCatching { enterPictureInPictureMode(createPipParams()) }.getOrDefault(false).also {
      if (!it) pipTransitionExpected = false
    }
  }

  @RequiresApi(Build.VERSION_CODES.O)
  fun createPipParams(): PictureInPictureParams {
    val builder = PictureInPictureParams.Builder()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      builder.setTitle(MPVLib.getPropertyString("media-title") ?: fileName)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val autoEnter = playerPreferences.automaticallyEnterPip.get() && !securePlayback
      builder.setAutoEnterEnabled(viewModel.paused == false && autoEnter)
      builder.setSeamlessResizeEnabled(viewModel.paused == false && autoEnter)
    }
    builder.setActions(createPipActions(this, viewModel.paused == true, appearancePreferences.iconStyle.get()))
    builder.setSourceRectHint(pipRect)
    validPipAspect(player.getVideoOutAspect())?.let { aspect ->
      builder.setAspectRatio(Rational((aspect * 10000).toInt(), 10000))
    }
    return builder.build()
  }

  override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
    if (!isInPictureInPictureMode) {
      pipReceiver?.let {
        unregisterReceiver(pipReceiver)
        pipReceiver = null
      }
      super.onPictureInPictureModeChanged(false, newConfig)
      pipTransitionExpected = false
      setOrientation()
      binding.player.post { if (!isDestroyed) viewModel.changeVideoAspect(playerPreferences.videoAspect.get(), notify = false) }
      return
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      setPictureInPictureParams(createPipParams())
    }
    pipTransitionExpected = false
    viewModel.hideControls()
    viewModel.hideSeekBar()
    viewModel.isBrightnessSliderShown.update { false }
    viewModel.isVolumeSliderShown.update { false }
    viewModel.panelNavigator.dismiss()
    pipReceiver?.let { unregisterReceiver(it) }
    pipReceiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null || intent.action != PIP_INTENTS_FILTER) return
        when (intent.getIntExtra(PIP_INTENT_ACTION, 0)) {
          PIP_PAUSE -> viewModel.pause()
          PIP_PLAY -> viewModel.unpause()
          PIP_FF -> viewModel.handleRightDoubleTap()
          PIP_FR -> viewModel.handleLeftDoubleTap()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          setPictureInPictureParams(createPipParams())
        }
      }
    }
    androidx.core.content.ContextCompat.registerReceiver(
      this, pipReceiver, IntentFilter(PIP_INTENTS_FILTER),
      androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
    )
    super.onPictureInPictureModeChanged(true, newConfig)
  }

  private fun setOrientation() {
    if (pipTransitionExpected) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) return
    orientationRequest(playerPreferences.orientation.get(), player.getVideoOutAspect())?.let {
      if (requestedOrientation != it) requestedOrientation = it
    }
  }

  override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
    when (keyCode) {
      KeyEvent.KEYCODE_VOLUME_UP -> {
        viewModel.changeVolumeBy(1)
        viewModel.displayVolumeSlider()
      }

      KeyEvent.KEYCODE_VOLUME_DOWN -> {
        viewModel.changeVolumeBy(-1)
        viewModel.displayVolumeSlider()
      }

      KeyEvent.KEYCODE_DPAD_RIGHT -> viewModel.handleRightDoubleTap()
      KeyEvent.KEYCODE_DPAD_LEFT -> viewModel.handleLeftDoubleTap()
      KeyEvent.KEYCODE_SPACE -> if (event?.repeatCount == 0) viewModel.pauseUnpause()
      KeyEvent.KEYCODE_MEDIA_STOP -> finishAndRemoveTask()

      KeyEvent.KEYCODE_MEDIA_REWIND -> viewModel.handleLeftDoubleTap()
      KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> viewModel.handleRightDoubleTap()

      // other keys should be bound by the user in input.conf ig
      else -> {
        return event?.let { player.onKey(it) } == true || super.onKeyDown(keyCode, event)
      }
    }
    return true
  }

  override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
    if (event != null && player.onKey(event)) return true
    return super.onKeyUp(keyCode, event)
  }

  private fun setupMediaSession() {
    val previousAction = gesturePreferences.mediaPreviousGesture.get()
    val playAction = gesturePreferences.mediaPlayGesture.get()
    val nextAction = gesturePreferences.mediaNextGesture.get()

    mediaSession = MediaSession(this, "PlayerActivity").apply {
      setCallback(
        object : MediaSession.Callback() {
          override fun onPlay() {
            when (playAction) {
              SingleActionGesture.None -> {}
              SingleActionGesture.Seek -> {}
              SingleActionGesture.PlayPause -> {
                super.onPlay()
                viewModel.unpause()
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
              }

              SingleActionGesture.Custom -> {
                MPVLib.command("keypress", CustomKeyCodes.MediaPlay.keyCode)
              }
            }
          }

          override fun onPause() {
            when (playAction) {
              SingleActionGesture.None -> {}
              SingleActionGesture.Seek -> {}
              SingleActionGesture.PlayPause -> {
                super.onPause()
                viewModel.pause()
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
              }

              SingleActionGesture.Custom -> {
                MPVLib.command("keypress", CustomKeyCodes.MediaPlay.keyCode)
              }
            }
          }

          override fun onSkipToPrevious() {
            when (previousAction) {
              SingleActionGesture.None -> {}
              SingleActionGesture.Seek -> {
                viewModel.leftSeek()
              }

              SingleActionGesture.PlayPause -> {
                viewModel.pauseUnpause()
              }

              SingleActionGesture.Custom -> {
                MPVLib.command("keypress", CustomKeyCodes.MediaPrevious.keyCode)
              }
            }
          }

          override fun onSkipToNext() {
            when (nextAction) {
              SingleActionGesture.None -> {}
              SingleActionGesture.Seek -> {
                viewModel.rightSeek()
              }

              SingleActionGesture.PlayPause -> {
                viewModel.pauseUnpause()
              }

              SingleActionGesture.Custom -> {
                MPVLib.command("keypress", CustomKeyCodes.MediaNext.keyCode)
              }
            }
          }

          override fun onStop() {
            super.onStop()
            isActive = false
            this@PlayerActivity.finish()
          }
        },
      )
      setPlaybackState(
        PlaybackState.Builder()
          .setActions(
            PlaybackState.ACTION_PLAY or
              PlaybackState.ACTION_PAUSE or
              PlaybackState.ACTION_STOP or
              PlaybackState.ACTION_SKIP_TO_PREVIOUS or
              PlaybackState.ACTION_SKIP_TO_NEXT,
          )
          .build(),
      )
      isActive = !securePlayback
    }

    val filter = IntentFilter().apply { addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY) }
    registerReceiver(noisyReceiver, filter)
    noisyReceiver.initialized = true
  }

  companion object {
    // action of result intent
    private val playbackWriteMutex = kotlinx.coroutines.sync.Mutex()
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val playbackWriterScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private const val RESULT_INTENT = "live.mehiz.mpvkt.ui.player.PlayerActivity.result"
  }
}

const val TAG = "mpvKt"
