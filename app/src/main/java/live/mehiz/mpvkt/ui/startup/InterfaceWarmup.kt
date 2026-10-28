package live.mehiz.mpvkt.ui.startup

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.overscroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.presentation.components.RoundedTextField
import live.mehiz.mpvkt.presentation.components.SlimPlayerSlider
import live.mehiz.mpvkt.ui.home.*
import live.mehiz.mpvkt.ui.icons.*
import live.mehiz.mpvkt.ui.motion.LocalReduceMotion
import live.mehiz.mpvkt.ui.player.controls.components.*
import live.mehiz.mpvkt.ui.preferences.*
import live.mehiz.mpvkt.ui.utils.navigationMotion

internal val LocalInterfacePreparation = staticCompositionLocalOf { false }

internal data class WarmupReport(
  val scenes: Int,
  val captures: Int,
  val rendered: Boolean,
  val failure: String? = null,
)

/**
 * Placed/display-list recorded, then explicitly rasterized: never a measure-only or alpha=0 warmup.
 */
@Composable
internal fun InterfaceWarmup(
  onReady: (WarmupReport) -> Unit,
  onProgress: (Float) -> Unit = {},
  firstRun: Boolean = false,
  thumbnails: List<android.graphics.Bitmap> = emptyList(),
  captureFrame: suspend (GraphicsLayer) -> Unit = ::rasterize,
) {
  var scene by remember { mutableIntStateOf(0) }
  var phase by remember { mutableIntStateOf(0) }
  var drawRevision by remember { mutableIntStateOf(0) }
  val frames = remember { Channel<Int>(Channel.CONFLATED) }
  DisposableEffect(frames) { onDispose { frames.close() } }
  val layer = rememberGraphicsLayer()
  val ready by rememberUpdatedState(onReady)
  val progress by rememberUpdatedState(onProgress)
  val reduceMotion = LocalReduceMotion.current
  val density = LocalDensity.current
  val width = with(density) { 720.toDp() }.coerceAtMost(420.dp)
  val height = with(density) { 1080.toDp() }.coerceAtMost(700.dp)
  LaunchedEffect(layer) {
    val startedAt = android.os.SystemClock.elapsedRealtime()
    var captures = 0
    var completeScenes = 0
    var healthy = true
    var failure: String? = null
    // The timeout prevents a lost window/surface from leaving the application behind the loader.
    val finished =
      try {
        withTimeoutOrNull(9_000) {
          repeat(6) { index ->
            scene = index
            phase = 0
            repeat(3) { step ->
              phase = step
              // Wait for a new draw of this exact content revision, not a frame merely scheduled by
              // Compose.
              val revision = ++drawRevision
              withTimeoutOrNull(1_200) {
                do {
                  val drawn = frames.receive()
                } while (drawn != revision)
              } ?: error("Warmup view did not draw")
              withFrameNanos {}
              // Exercise forward and reverse frames. Animation time is real work, not an empty
              // hold.
              val until =
                withFrameNanos { it } +
                  (if (reduceMotion) 32 else if (firstRun) 280 else 140) * 1_000_000L
              do {
                val now = withFrameNanos { it }
              } while (now < until)
              try {
                captureFrame(layer)
                captures++
              } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
              } catch (error: Exception) {
                healthy = false
                failure = error.javaClass.simpleName
              } catch (_: OutOfMemoryError) {
                healthy = false
                failure = "OutOfMemoryError"
              }
            }
            completeScenes++
            progress(completeScenes / 6f)
          }
          true
        }
      } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
      } catch (error: Exception) {
        failure = error.javaClass.simpleName
        false
      } catch (_: OutOfMemoryError) {
        failure = "OutOfMemoryError"
        false
      }
    if (finished != true) healthy = false
    android.util.Log.i(
      "MPV-startup",
      "Preparation scenes=$completeScenes captures=$captures elapsed=${android.os.SystemClock.elapsedRealtime() - startedAt}ms failure=$failure",
    )
    ready(WarmupReport(completeScenes, captures, healthy && captures == 18, failure))
  }
  // Full opacity inside a bounded texture. The startup page covers this sibling; explicit capture
  // forces rasterization even when window occlusion would otherwise omit the covered draw.
  Box(
    Modifier.size(width, height)
      .clearAndSetSemantics {}
      .focusProperties {
        canFocus = false
        onEnter = { cancelFocusChange() }
      }
      .focusGroup()
      .pointerInput(Unit) {
        awaitPointerEventScope {
          while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
      }
      .drawWithContent {
        layer.record { this@drawWithContent.drawContent() }
        drawLayer(layer)
        frames.trySend(drawRevision)
      }
  ) {
    CompositionLocalProvider(LocalInterfacePreparation provides true) {
      WarmupScene(scene, phase, reduceMotion, thumbnails)
    }
  }
}

private suspend fun rasterize(layer: GraphicsLayer) {
  val image = layer.toImageBitmap()
  check(image.width > 0 && image.height > 0)
  // Capture is owned by this warmup only. Do not keep a succession of GPU/readback bitmaps alive.
  image.asAndroidBitmap().recycle()
}

/**
 * Real home is mounted once, retained across reveal, and captured after its first library layout.
 */
@Composable
internal fun PreparedHome(
  preparing: Boolean,
  captureKey: Any,
  captureAllowed: Boolean = true,
  onReady: () -> Unit,
  content: @Composable () -> Unit,
) {
  val layer = rememberGraphicsLayer()
  val frames = remember { Channel<Unit>(Channel.CONFLATED) }
  val ready by rememberUpdatedState(onReady)
  DisposableEffect(frames) { onDispose { frames.close() } }
  LaunchedEffect(preparing, captureKey, captureAllowed) {
    if (!preparing || !captureAllowed) return@LaunchedEffect
    // A failed capture still leaves the mounted/laid-out home usable; never loop or hide the app
    // forever.
    try {
      withTimeoutOrNull(1_500) {
        frames.receive()
        withFrameNanos {}
        rasterize(layer)
        repeat(3) { withFrameNanos {} }
      }
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
      throw cancelled
    } catch (_: Exception) {} catch (_: OutOfMemoryError) {}
    ready()
  }
  Box(
    Modifier.fillMaxSize()
      .then(
        if (preparing)
          Modifier.clearAndSetSemantics {}
            .focusProperties {
              canFocus = false
              onEnter = { cancelFocusChange() }
            }
            .focusGroup()
        else Modifier
      )
      .drawWithContent {
        if (preparing) {
          layer.record { this@drawWithContent.drawContent() }
          drawLayer(layer)
          frames.trySend(Unit)
        } else drawContent()
      }
  ) {
    content()
  }
}

@Composable
private fun WarmupScene(
  scene: Int,
  phase: Int,
  reduceMotion: Boolean,
  thumbnails: List<android.graphics.Bitmap>,
) {
  Surface(Modifier.fillMaxSize()) {
    when (scene) {
      0 ->
        AnimatedContent(
          phase == 1,
          transitionSpec = { navigationMotion(!targetState, reduceMotion) },
          label = "warm_navigation",
        ) { detail ->
          Column(Modifier.fillMaxSize()) {
            LibraryHeader(
              if (detail) stringResource(R.string.pref_subtitles) else "MPV",
              false,
              detail,
              "",
              LibrarySort(),
              reduceMotion,
              {},
              {},
              {},
              {},
              {},
            )
            WarmingLibraryList(phase, reduceMotion)
          }
        }
      1 ->
        Column(Modifier.fillMaxSize()) {
          LibraryHeader(
            stringResource(R.string.pref_subtitles),
            phase == 1,
            true,
            "",
            LibrarySort(),
            reduceMotion,
            {},
            {},
            {},
            {},
            {},
          )
          SettingsGroup {
            SettingsRow(
              { Text(stringResource(R.string.subtitle_filters)) },
              icon = { Icon(Icons.Tune, null) },
              expanded = phase == 1,
            ) {}
            ExpandableSettingsContent(phase == 1) {
              SettingsRow({ Text(stringResource(R.string.pref_subtitles)) }, toggle = true) {}
            }
          }
        }
      2 ->
        key(phase) {
          live.mehiz.mpvkt.presentation.components.PlayerSheet(
            onDismissRequest = {},
            reduceMotion = reduceMotion,
            interactive = false,
          ) { _, _ ->
            Column(
              Modifier.fillMaxSize()
                .background(
                  Brush.verticalGradient(
                    0f to Color.Black,
                    .2f to Color.Transparent,
                    .7f to Color.Transparent,
                    1f to Color.Black,
                  )
                )
            ) {
              PlayerPanelHeader(stringResource(R.string.pref_subtitles), Icons.Subtitles, {})
              AnimatedPlayPauseButton(phase != 1, false, false, false, reduceMotion, {})
              PlayerActionRow(stringResource(R.string.pref_audio), Icons.VolumeUp, {})
              PlayerActionRow(stringResource(R.string.pref_subtitles), Icons.Subtitles, {})
              dev.vivvvek.seeker.Seeker(
                value = if (phase == 1) 80f else 20f,
                range = 0f..120f,
                onValueChange = {},
                readAheadValue = 100f,
                segments =
                  listOf(dev.vivvvek.seeker.Segment("", 0f), dev.vivvvek.seeker.Segment("", 60f)),
                modifier = Modifier.fillMaxWidth(),
              )
              SlimPlayerSlider(if (phase == 1) .8f else .2f, {}, Modifier.fillMaxWidth())
            }
          }
        }
      3 ->
        Surface(
          Modifier.fillMaxWidth().graphicsLayer {
            translationY = if (phase == 1) 0f else 8.dp.toPx()
          }
        ) {
          LibrarySortMenuContent(
            LibrarySort(if (phase == 1) LibrarySortKey.Date else LibrarySortKey.Title, phase != 1),
            countEnabled = true,
            reduceMotion = reduceMotion,
            onSelect = {},
          )
        }
      4 ->
        Column(Modifier.padding(20.dp)) {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            thumbnails.take(3).forEach { bitmap ->
              androidx.compose.foundation.Image(
                bitmap.asImageBitmap(),
                null,
                Modifier.weight(1f).height(64.dp),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
              )
            }
          }
          SettingsCard {
            Column(Modifier.padding(20.dp)) {
              Text(
                stringResource(R.string.pref_custom_button_add_button),
                style = MaterialTheme.typography.titleLarge,
              )
              RoundedTextField(
                "MPV",
                {},
                label = { Text(stringResource(R.string.pref_custom_button_action_add_title_text)) },
              )
              RoundedTextField("", {}, label = { Text(stringResource(R.string.subtitle_key_hint)) })
              Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({}, Modifier.weight(1f)) {
                  Text(stringResource(R.string.generic_cancel))
                }
                Button({}, Modifier.weight(1f)) { Text(stringResource(R.string.subtitle_save)) }
              }
            }
          }
        }
      else ->
        Column(Modifier.padding(20.dp)) {
          // Exercise the original vectors in both styles as painters, rather than only parsing
          // their XML.
          androidx.compose.runtime.CompositionLocalProvider(
            LocalIconStyle provides if (phase == 1) IconStyle.Broken else IconStyle.Outline
          ) {
            val resources = androidx.compose.ui.platform.LocalContext.current.resources
            val style = LocalIconStyle.current
            val icons =
              IconKey.entries
                .flatMap { listOf(it.drawable(style), it.drawable(style, true)) }
                .distinct()
                .map { IconVectors.get(resources, it) }
            BoxWithConstraints {
              val cell = (maxWidth / 8).coerceAtMost(with(LocalDensity.current) { 48.toDp() })
              Column {
                icons.chunked(8).forEach { row ->
                  Row { row.forEach { Icon(it, null, Modifier.size(cell).padding(cell / 8)) } }
                }
              }
            }
          }
          SlimPlayerSlider(if (phase == 1) .9f else .1f, {}, Modifier.fillMaxWidth())
          AnimatedPlayPauseButton(phase != 1, false, false, false, reduceMotion, {})
        }
    }
  }
}

@Composable
private fun WarmingLibraryList(phase: Int, reduceMotion: Boolean) {
  val effect = remember {
    live.mehiz.mpvkt.ui.motion.LibraryOverscrollFactory.createOverscrollEffect()
  }
  LaunchedEffect(phase) {
    withFrameNanos {}
    if (!reduceMotion && phase == 1) {
      effect.applyToScroll(
        androidx.compose.ui.geometry.Offset(0f, 40f),
        androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput,
      ) {
        androidx.compose.ui.geometry.Offset.Zero
      }
      effect.applyToFling(androidx.compose.ui.unit.Velocity.Zero) {
        androidx.compose.ui.unit.Velocity.Zero
      }
    }
  }
  val rows = if (phase == 1) (7 downTo 0).toList() else (0..7).toList()
  LazyColumn(Modifier.fillMaxSize().overscroll(effect), overscrollEffect = null) {
    items(rows, key = { it }) { index ->
      Row(
        Modifier.animateItem(placementSpec = tween(if (reduceMotion) 0 else 280))
          .fillMaxWidth()
          .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        androidx.compose.foundation.Image(
          androidx.compose.ui.res.painterResource(R.drawable.library_folder),
          null,
          Modifier.size(88.dp, 76.dp),
        )
        Column {
          Text("MPV · $index", style = MaterialTheme.typography.titleMedium)
          Text(
            stringResource(R.string.mpv_view_folders),
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
    }
  }
}
