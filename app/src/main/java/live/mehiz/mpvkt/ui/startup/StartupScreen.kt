package live.mehiz.mpvkt.ui.startup

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import live.mehiz.mpvkt.BuildConfig
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.preferences.PlayerPreferences
import live.mehiz.mpvkt.preferences.preference.collectAsState
import live.mehiz.mpvkt.ui.home.LocalMediaLibrary
import live.mehiz.mpvkt.ui.home.MediaLibrary
import live.mehiz.mpvkt.ui.home.Thumbnails
import live.mehiz.mpvkt.ui.icons.*
import org.koin.compose.koinInject

internal data class LibraryAccess(
  val granted: Boolean,
  val limited: Boolean,
  val request: () -> Unit,
)

internal val LocalLibraryAccess =
  staticCompositionLocalOf<LibraryAccess> { error("Library access is missing") }

/** One Activity-scoped library survives the intro, permission dialog and navigation. */
@Composable
internal fun StartupGate(content: @Composable () -> Unit) {
  val context = LocalContext.current
  val app = context.applicationContext
  val library: MediaLibrary = viewModel()
  val state by library.state.collectAsState()
  val preferences = remember { app.getSharedPreferences("startup", Context.MODE_PRIVATE) }
  var introduced by rememberSaveable { mutableStateOf(preferences.getBoolean("introduced", false)) }
  var access by remember { mutableStateOf(VideoAccess.granted(context)) }
  var limited by remember { mutableStateOf(VideoAccess.limited(context)) }
  var denied by rememberSaveable { mutableStateOf(false) }
  var requesting by remember { mutableStateOf(false) }
  val style = LocalIconStyle.current
  var prepared by remember { mutableStateOf(false) }
  var interfaceReady by remember { mutableStateOf(library.interfacePrepared) }
  var storageReady by remember { mutableStateOf(false) }
  var homeReady by remember { mutableStateOf(library.enteredHome) }
  var minimumReady by remember { mutableStateOf(library.enteredHome) }
  var thumbnails by remember { mutableStateOf<List<android.graphics.Bitmap>>(emptyList()) }
  var warmupProgress by remember { mutableFloatStateOf(if (interfaceReady) 1f else 0f) }
  var renderHealthy by remember { mutableStateOf(false) }
  val firstPreparation = remember {
    preferences.getInt("prepared_version", 0) != BuildConfig.VERSION_CODE
  }
  LaunchedEffect(library) {
    if (!library.enteredHome) delay(if (firstPreparation) 3_800 else 1_600)
    minimumReady = true
  }
  val database = koinInject<live.mehiz.mpvkt.database.MpvKtDatabase>()
  val reduceMotion by koinInject<PlayerPreferences>().reduceMotion.collectAsState()
  fun completeIntro() {
    preferences.edit { putBoolean("introduced", true) }
    introduced = true
  }
  val request =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
      requesting = false
      access = VideoAccess.granted(context)
      limited = VideoAccess.limited(context)
      denied = !access
      if (access) {
        library.reload()
        completeIntro()
      }
    }
  val owner = LocalLifecycleOwner.current
  val lifecycleState by owner.lifecycle.currentStateAsState()
  val foreground = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
  DisposableEffect(owner, library) {
    var paused = false
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_PAUSE) paused = true
      if (event == Lifecycle.Event.ON_RESUME) {
        access = VideoAccess.granted(context)
        limited = VideoAccess.limited(context)
        if (paused && access) library.reload()
        paused = false
      }
    }
    owner.lifecycle.addObserver(observer)
    onDispose { owner.lifecycle.removeObserver(observer) }
  }
  val folderPreferences =
    remember(app) {
      live.mehiz.mpvkt.preferences.FoldersPreferences(
        live.mehiz.mpvkt.preferences.preference.AndroidPreferenceStore(app)
      )
    }
  val selectedScan by folderPreferences.includeHidden.collectAsState()
  val selectedTrees by folderPreferences.hiddenTrees.collectAsState()
  val hasSelectedAccess = selectedScan && selectedTrees.isNotEmpty()
  LaunchedEffect(access, hasSelectedAccess, selectedTrees) {
    if (access || hasSelectedAccess) library.reload(force = true) else library.clearAccess()
  }
  LaunchedEffect(database) {
    try {
      withContext(Dispatchers.IO) { database.openHelper.writableDatabase }
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (_: Exception) {
      /* A database error must not trap the user behind a loading page. */
    }
    storageReady = true
  }
  LaunchedEffect(style) {
    // Prefetch runs off the UI thread. No player/decoder is created during onboarding.
    try {
      IconVectors.prepare(app.resources, style)
      IconVectors.prepare(
        app.resources,
        if (style == IconStyle.Outline) IconStyle.Broken else IconStyle.Outline,
      )
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (_: Exception) {
      /* Individual icons can still use their normal on-demand path. */
    }
    prepared = true
  }
  val thumbnailOptions = live.mehiz.mpvkt.ui.home.LocalThumbnailOptions.current
  LaunchedEffect(state.videos, thumbnailOptions) {
    // A small, bounded prefetch, not a decode of the whole library and not a startup gate.
    thumbnails = emptyList()
    if (library.enteredHome || !thumbnailOptions.enabled) return@LaunchedEffect
    state.videos.take(3).forEach { video ->
      Thumbnails.load(app, video.uri, video.revision, thumbnailOptions)?.let {
        if (!library.enteredHome) thumbnails = thumbnails + it
      }
    }
  }
  val dataReady = (!access && !hasSelectedAccess) || state.loaded
  val ready =
    introduced &&
      prepared &&
      interfaceReady &&
      storageReady &&
      dataReady &&
      homeReady &&
      minimumReady
  LaunchedEffect(ready) {
    if (ready) {
      library.enteredHome = true
      thumbnails = emptyList()
      preferences.edit {
        putInt("prepared_version", BuildConfig.VERSION_CODE)
        putBoolean("raster_warmup", renderHealthy)
      }
    }
  }
  val showHome = ready || library.enteredHome
  val requestAccess = {
    if (!requesting) {
      requesting = true
      request.launch(VideoAccess.permissions())
    }
  }
  CompositionLocalProvider(
    LocalMediaLibrary provides library,
    LocalLibraryAccess provides LibraryAccess(access, limited, requestAccess),
  ) {
    Box(Modifier.fillMaxSize()) {
      // Keep this composition alive when the cover leaves: no second cold construction of Home.
      if (introduced)
        CompositionLocalProvider(LocalInterfacePreparation provides !showHome) {
          PreparedHome(
            preparing = !showHome,
            captureAllowed = dataReady && foreground && !requesting,
            captureKey = state.videos,
            onReady = { homeReady = true },
          ) {
            content()
          }
        }
      if (!interfaceReady && prepared && foreground && !requesting)
        InterfaceWarmup(
          onReady = { report ->
            library.interfacePrepared = true
            interfaceReady = true
            renderHealthy = report.rendered
          },
          onProgress = { warmupProgress = it },
          firstRun = firstPreparation,
          thumbnails = thumbnails,
        )
      androidx.compose.animation.AnimatedVisibility(
        !showHome,
        enter = fadeIn(tween(0)),
        exit = fadeOut(tween(if (reduceMotion) 0 else 280)),
        modifier = Modifier.fillMaxSize(),
      ) {
        Box(
          Modifier.fillMaxSize().pointerInput(Unit) {
            awaitPointerEventScope {
              while (true) awaitPointerEvent(
                  androidx.compose.ui.input.pointer.PointerEventPass.Final
                )
                .changes
                .forEach { it.consume() }
            }
          }
        ) {
          StartupPage(
            showIntroduction = !introduced,
            access = access,
            preparing = !ready,
            progress =
              (warmupProgress * .75f +
                  (if (prepared && storageReady) .15f else 0f) +
                  (if (homeReady) .1f else 0f))
                .coerceIn(0f, 1f),
            requesting = requesting,
            denied = denied,
            onAllow = { if (access) completeIntro() else requestAccess() },
            onSkip = ::completeIntro,
            onSettings = {
              context.startActivity(
                Intent(
                  Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                  "package:${context.packageName}".toUri(),
                )
              )
            },
          )
        }
      }
    }
  }
}

@Composable
private fun StartupPage(
  showIntroduction: Boolean,
  access: Boolean,
  preparing: Boolean,
  progress: Float,
  requesting: Boolean,
  denied: Boolean,
  onAllow: () -> Unit,
  onSkip: () -> Unit,
  onSettings: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  Box(
    Modifier.fillMaxSize().background(colors.background).safeDrawingPadding(),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      Modifier.widthIn(max = 420.dp)
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(28.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      Box(
        Modifier.size(88.dp).background(colors.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
      ) {
        Icon(Icons.Play, null, Modifier.size(36.dp), tint = colors.primary)
      }
      Text("MPV", style = MaterialTheme.typography.displaySmall)
      Text(
        stringResource(
          if (showIntroduction) R.string.mpv_welcome else R.string.mpv_loading_interface
        ),
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
      )
      if (showIntroduction) {
        Text(
          stringResource(R.string.mpv_welcome_body),
          style = MaterialTheme.typography.bodyLarge,
          color = colors.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
        Surface(shape = RoundedCornerShape(24.dp), color = colors.surfaceContainer) {
          Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Folder, null, tint = colors.primary)
            Text(
              stringResource(
                if (denied) R.string.mpv_permission_denied else R.string.mpv_welcome_access
              ),
              style = MaterialTheme.typography.bodyMedium,
            )
          }
        }
        Button(
          onClick = onAllow,
          enabled = !requesting,
          modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) {
          Text(stringResource(if (access) R.string.mpv_get_started else R.string.mpv_allow))
        }
        if (denied)
          TextButton(onClick = onSettings) { Text(stringResource(R.string.mpv_open_app_settings)) }
        TextButton(onClick = onSkip, enabled = !requesting) {
          Text(stringResource(R.string.mpv_not_now))
        }
      }
      if (!showIntroduction)
        Text(
          stringResource(R.string.mpv_loading_detail),
          style = MaterialTheme.typography.bodyMedium,
          color = colors.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
      if (preparing) {
        val animatedProgress by
          androidx.compose.animation.core.animateFloatAsState(
            progress,
            tween(240),
            label = "startup_progress",
          )
        LinearProgressIndicator(
          progress = { animatedProgress },
          modifier = Modifier.width(180.dp).height(4.dp).clip(RoundedCornerShape(50)),
        )
        if (showIntroduction)
          Text(
            stringResource(R.string.mpv_preparing),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
          )
      }
    }
  }
}
