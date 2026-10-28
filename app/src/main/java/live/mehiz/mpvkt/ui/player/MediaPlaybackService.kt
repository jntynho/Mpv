// Modified for Luma: document the media-session notification exemption.
package live.mehiz.mpvkt.ui.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVNode
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.preferences.GesturePreferences
import live.mehiz.mpvkt.preferences.AppearancePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

@Suppress("TooManyFunctions")
class MediaPlaybackService : MediaBrowserServiceCompat(), MPVLib.EventObserver {
  companion object {
    private const val NOTIFICATION_ID = 69420
    private const val NOTIFICATION_CHANNEL_ID = "mpvkt_playback_channel"

    const val ACTION_PLAY = "live.mehiz.mpvkt.action.PLAY"
    const val ACTION_PAUSE = "live.mehiz.mpvkt.action.PAUSE"
    const val ACTION_STOP = "live.mehiz.mpvkt.action.STOP"
    const val ACTION_SKIP_FORWARD = "live.mehiz.mpvkt.action.SKIP_FORWARD"
    const val ACTION_SKIP_BACKWARD = "live.mehiz.mpvkt.action.SKIP_BACKWARD"
  }

  private val gesturePreferences by inject<GesturePreferences>()
  private val appearancePreferences by inject<AppearancePreferences>()
  private val appearanceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var notificationActive = false

  private val binder = MediaPlaybackBinder()
  private var mediaTitle = ""
  private var mediaArtist = ""
  private var positionMs: Long?
    get() = MPVLib.getPropertyDouble("time-pos")?.times(1000L)?.toLong()
    set(value) { value?.let { PlaybackControl.enqueue("seek") { MPVLib.command("seek", (it.coerceAtLeast(0) / 1000.0).toString(), "absolute") } } }
  private val durationMs: Long?
    get() = (MPVLib.getPropertyDouble("duration")?.times(1000L))?.toLong()
  private val paused: Boolean?
    get() = PlaybackControl.state.value.displayPaused

  private lateinit var mediaSession: MediaSessionCompat

  private lateinit var audioManager: AudioManager
  @Volatile private var focusReleased = false
  private var audioFocusRequest: AudioFocusRequest? = null
  private var audioFocusCallback: AudioManager.OnAudioFocusChangeListener? = null

  @Suppress("EmptyFunctionBlock")
  override fun eventProperty(property: String) {
  }

  override fun eventProperty(property: String, value: Long) {
    if (focusReleased) return
    when (property) {
      "duration", "time-pos" -> updatePlaybackState()
    }
  }

  override fun eventProperty(property: String, value: Boolean) {
    if (focusReleased) return
    when (property) {
      "pause" -> Unit // The coordinated playback flow updates the notification.
    }
  }

  override fun eventProperty(property: String, value: String) {
    if (focusReleased) return
    when (property) {
      "metadata/artist" -> {
        mediaArtist = value
        updateMediaSessionMetadata()
        updateNotification()
      }

      "media-title" -> {
        mediaTitle = value
        updateMediaSessionMetadata()
        updateNotification()
      }
    }
  }

  @Suppress("EmptyFunctionBlock")
  override fun eventProperty(property: String, value: Double) {
    if (focusReleased) return
    when (property) {
      "duration", "time-pos", "speed" -> updatePlaybackState()
    }
  }

  @Suppress("EmptyFunctionBlock")
  override fun eventProperty(property: String, value: MPVNode) {
  }

  @Suppress("EmptyFunctionBlock")
  override fun event(eventId: Int, data: MPVNode) {
  }

  private var mediaThumbnail: Bitmap? = null

  inner class MediaPlaybackBinder : Binder() {
    fun getService(): MediaPlaybackService = this@MediaPlaybackService
  }

  override fun onCreate() {
    super.onCreate()

    setupMediaSession()

    audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
    if (PlaybackControl.state.value.attached) {
    MPVLib.addObserver(this)
    mapOf(
      "pause" to MPVLib.MpvFormat.MPV_FORMAT_FLAG,
      "duration" to MPVLib.MpvFormat.MPV_FORMAT_DOUBLE,
      "time-pos" to MPVLib.MpvFormat.MPV_FORMAT_DOUBLE,
      "speed" to MPVLib.MpvFormat.MPV_FORMAT_DOUBLE,
      "media-title" to MPVLib.MpvFormat.MPV_FORMAT_STRING,
      "metadata/artist" to MPVLib.MpvFormat.MPV_FORMAT_STRING,
    ).onEach {
      MPVLib.observeProperty(it.key, it.value)
    }
    }

    setupAudioFocus()
    createNotificationChannel()
    PlaybackControl.focusRequester(this) { requestAudioFocus() }
    appearanceScope.launch {
      PlaybackControl.state.collect {
        if (notificationActive && !focusReleased) { updatePlaybackState(); updateNotification() }
      }
    }
    appearanceScope.launch {
      appearancePreferences.iconStyle.changes().collect {
        if (notificationActive && !focusReleased) updateNotification()
      }
    }
  }

  override fun onBind(intent: Intent): IBinder {
    return binder
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (!PlaybackControl.state.value.attached) { stopSelf(); return START_NOT_STICKY }
    androidx.core.app.ServiceCompat.startForeground(this, NOTIFICATION_ID, createNotification(),
      if (Build.VERSION.SDK_INT >= 29) android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0)
    notificationActive = true
    if (PlaybackControl.state.value.wantsPlay) {
      if (requestAudioFocus()) PlaybackControl.focusGained() else PlaybackControl.focusLost(transient = true)
    }
    MediaButtonReceiver.handleIntent(mediaSession, intent)
    handleIntent(intent)
    return START_NOT_STICKY
  }

  override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: android.os.Bundle?) =
    BrowserRoot("root_id", null)

  override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
    result.sendResult(mutableListOf())
  }

  private fun handleIntent(intent: Intent?) {
    if (intent == null) return

    when (intent.action) {
      ACTION_PLAY -> playMedia()
      ACTION_PAUSE -> pauseMedia()
      ACTION_STOP -> stopMedia()
      ACTION_SKIP_FORWARD -> seekForward()
      ACTION_SKIP_BACKWARD -> seekBackward()
    }
  }

  fun setMediaInfo(title: String, artist: String, thumbnail: Bitmap? = null) {
    mediaThumbnail = thumbnail
    mediaTitle = title
    mediaArtist = artist

    updateMediaSessionMetadata()
    updateNotification()
  }

  private fun setupMediaSession() {
    mediaSession = MediaSessionCompat(this, "MediaPlaybackService").apply {
      setCallback(
        object : MediaSessionCompat.Callback() {
          override fun onPlay() {
            playMedia()
          }

          override fun onPause() {
            pauseMedia()
          }

          override fun onStop() {
            stopMedia()
          }

          override fun onSkipToNext() {
            seekForward()
          }

          override fun onSkipToPrevious() {
            seekBackward()
          }

          override fun onSeekTo(pos: Long) {
            positionMs = pos
          }
        },
      )

      isActive = true
      setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
      setSessionToken(sessionToken)
      setPlaybackState(
        PlaybackStateCompat.Builder()
          .setActions(getAvailableActions())
          .setState(PlaybackStateCompat.STATE_PAUSED, 0, 1.0f)
          .build(),
      )
    }
  }

  private fun getAvailableActions(): Long {
    return PlaybackStateCompat.ACTION_PLAY or
      PlaybackStateCompat.ACTION_PAUSE or
      PlaybackStateCompat.ACTION_PLAY_PAUSE or
      PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
      PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
      PlaybackStateCompat.ACTION_STOP or
      PlaybackStateCompat.ACTION_SEEK_TO
  }

  private fun setupAudioFocus() {
    audioFocusCallback = AudioManager.OnAudioFocusChangeListener { focusChange ->
      if (focusReleased) return@OnAudioFocusChangeListener
      when (focusChange) {
        AudioManager.AUDIOFOCUS_LOSS -> PlaybackControl.focusLost(transient = false)
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
          PlaybackControl.focusLost(transient = true)
        }
        AudioManager.AUDIOFOCUS_GAIN -> {
          PlaybackControl.focusGained()
        }
      }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
        .build()

      audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(audioAttributes)
        .setAcceptsDelayedFocusGain(false)
        .setOnAudioFocusChangeListener(audioFocusCallback!!)
        .build()
    }
  }

  private fun requestAudioFocus(): Boolean {
    focusReleased = false
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      audioFocusRequest?.let {
        val result = audioManager.requestAudioFocus(it)
        result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
      } ?: false
    } else {
      val result = audioManager.requestAudioFocus(
        audioFocusCallback,
        AudioManager.STREAM_MUSIC,
        AudioManager.AUDIOFOCUS_GAIN,
      )
      result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }
  }

  fun releasePlaybackFocus() {
    focusReleased = true
    PlaybackControl.clearFocusRequester(this)
    MPVLib.removeObserver(this)
    abandonAudioFocus()
  }

  private fun abandonAudioFocus() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
    } else {
      audioManager.abandonAudioFocus(audioFocusCallback)
    }
  }

  fun playMedia() = PlaybackControl.play()

  fun pauseMedia() = PlaybackControl.pause()

  private fun stopMedia() {
    notificationActive = false
    try {
      pauseMedia()
      abandonAudioFocus()
      mediaSession.isActive = false
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        stopForeground(STOP_FOREGROUND_REMOVE)
      } else {
        stopForeground(true)
      }
      stopSelf()
    } catch (e: Exception) {
      Log.e(TAG, "Error stopping media", e)
    }
  }

  private fun seekForward() {
    positionMs = positionMs?.plus(gesturePreferences.doubleTapToSeekDuration.get() * 1000L)
  }

  private fun seekBackward() {
    positionMs = positionMs?.minus(gesturePreferences.doubleTapToSeekDuration.get() * 1000L)
  }

  private fun updateMediaSessionMetadata() {
    try {
      val metadataBuilder = MediaMetadataCompat.Builder()
        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, mediaTitle)
        .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, mediaArtist)
        .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs ?: 0L)
        .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, mediaTitle)
        .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, mediaTitle.hashCode().toString())

      mediaThumbnail?.let {
        metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it)
        metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, it)
      }

      mediaSession.setMetadata(metadataBuilder.build())
    } catch (e: Exception) {
      Log.e(TAG, "Error updating metadata", e)
    }
  }

  private fun updatePlaybackState() {
    try {
      val stateBuilder = PlaybackStateCompat.Builder()
        .setActions(getAvailableActions())
        .setState(
          when {
            PlaybackControl.state.value.displayPaused -> PlaybackStateCompat.STATE_PAUSED
            PlaybackControl.state.value.isBuffering || PlaybackControl.state.value.pending -> PlaybackStateCompat.STATE_BUFFERING
            else -> PlaybackStateCompat.STATE_PLAYING
          },
          positionMs ?: 0,
          MPVLib.getPropertyDouble("speed")?.toFloat() ?: 1.0f,
        )

      mediaSession.setPlaybackState(stateBuilder.build())
    } catch (e: Exception) {
      Log.e(TAG, "Error updating playback state", e)
    }
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        NOTIFICATION_CHANNEL_ID,
        getString(R.string.notification_channel_name),
        NotificationManager.IMPORTANCE_LOW,
      ).apply {
        description = getString(R.string.notification_channel_description)
        setShowBadge(false)
        enableLights(false)
        enableVibration(false)
      }

      val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      notificationManager.createNotificationChannel(channel)
    }
  }

  private fun createNotification(): Notification {
    val icons = mediaControlIcons(appearancePreferences.iconStyle.get())
    val openAppIntent = Intent(this, PlayerActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP }
    val pendingOpenAppIntent = PendingIntent.getActivity(
      this,
      0,
      openAppIntent,
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    val playIntent = Intent(this, MediaPlaybackService::class.java).apply { action = ACTION_PLAY }
    val pendingPlayIntent = PendingIntent.getService(this, 1, playIntent, PendingIntent.FLAG_IMMUTABLE)

    val pauseIntent = Intent(this, MediaPlaybackService::class.java).apply { action = ACTION_PAUSE }
    val pendingPauseIntent = PendingIntent.getService(this, 2, pauseIntent, PendingIntent.FLAG_IMMUTABLE)

    val skipForwardIntent = Intent(this, MediaPlaybackService::class.java).apply { action = ACTION_SKIP_FORWARD }
    val pendingSkipForwardIntent = PendingIntent.getService(this, 3, skipForwardIntent, PendingIntent.FLAG_IMMUTABLE)

    val skipBackwardIntent = Intent(this, MediaPlaybackService::class.java).apply { action = ACTION_SKIP_BACKWARD }
    val pendingSkipBackwardIntent = PendingIntent.getService(this, 4, skipBackwardIntent, PendingIntent.FLAG_IMMUTABLE)

    val stopIntent = Intent(this, MediaPlaybackService::class.java).apply { action = ACTION_STOP }
    val pendingStopIntent = PendingIntent.getService(this, 5, stopIntent, PendingIntent.FLAG_IMMUTABLE)

    return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
      .setContentTitle(mediaTitle)
      .setContentText(mediaArtist.ifBlank { getString(R.string.notification_playing) })
      .setSmallIcon(icons.play)
      .setLargeIcon(mediaThumbnail)
      .setContentIntent(pendingOpenAppIntent)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setOnlyAlertOnce(true)
      .setOngoing(paused == false)
      .addAction(icons.rewind, getString(R.string.notification_rewind), pendingSkipBackwardIntent)
      .addAction(
        if (paused == false) icons.pause else icons.play,
        if (paused == false) getString(R.string.notification_pause) else getString(R.string.notification_play),
        if (paused == false) pendingPauseIntent else pendingPlayIntent,
      )
      .addAction(
        icons.forward,
        getString(R.string.notification_forward),
        pendingSkipForwardIntent,
      )
      .addAction(
        icons.stop,
        getString(R.string.notification_stop),
        pendingStopIntent,
      )
      .setStyle(
        androidx.media.app.NotificationCompat.MediaStyle()
          .setMediaSession(mediaSession.sessionToken)
          .setShowActionsInCompactView(0, 1, 2),
      )
      .setColorized(true)
      .setColor(Color.BLUE)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .build()
  }

  // Media-session notifications are exempt from Android 13 notification permission.
  @android.annotation.SuppressLint("NotificationPermission")
  private fun updateNotification() {
    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.notify(NOTIFICATION_ID, createNotification())
  }

  override fun onDestroy() {
    notificationActive = false
    appearanceScope.cancel()
    try {
      releasePlaybackFocus()
      mediaSession.release()
      super.onDestroy()
    } catch (e: Exception) {
      Log.e(TAG, "Error in onDestroy", e)
    }
  }
}
