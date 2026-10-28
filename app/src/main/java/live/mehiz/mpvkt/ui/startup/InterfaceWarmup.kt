package live.mehiz.mpvkt.ui.startup

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import live.mehiz.mpvkt.presentation.components.RoundedTextField
import live.mehiz.mpvkt.presentation.components.SlimPlayerSlider
import live.mehiz.mpvkt.ui.home.LibraryHeader
import live.mehiz.mpvkt.ui.home.LibrarySort
import live.mehiz.mpvkt.ui.icons.*
import live.mehiz.mpvkt.ui.player.controls.components.AnimatedPlayPauseButton
import live.mehiz.mpvkt.ui.player.controls.components.PlayerPanelHeader
import live.mehiz.mpvkt.ui.preferences.SettingsCard
import live.mehiz.mpvkt.ui.preferences.SettingsRow

/**
 * Precompose/measure common real components, never place them or expose hidden controls to
 * input/a11y. Separate frames bound the main-thread work; no network, player, decoder or private
 * keys are touched.
 */
@Composable
internal fun InterfaceWarmup(onReady: () -> Unit) {
  var stage by remember { mutableIntStateOf(0) }
  var measured by remember { mutableIntStateOf(-1) }
  val latestReady by rememberUpdatedState(onReady)
  LaunchedEffect(measured) {
    if (measured < 0) return@LaunchedEffect
    withFrameNanos {}
    if (measured < 2) stage = measured + 1 else latestReady()
  }
  SubcomposeLayout { constraints ->
    val width = constraints.maxWidth.coerceAtMost(420.dp.roundToPx())
    subcompose(stage) {
        when (stage) {
          0 ->
            Column {
              LibraryHeader("MPV", false, false, "", LibrarySort(), true, {}, {}, {}, {}, {})
              SettingsCard {
                SettingsRow(
                  { Text("MPV") },
                  { Text("Subtitles") },
                  { Icon(Icons.Subtitles, null) },
                ) {}
              }
            }
          1 ->
            Column {
              PlayerPanelHeader("Subtitles", Icons.Subtitles, {}, animateTitle = false)
              AnimatedPlayPauseButton(true, false, false, false, true, {})
              SlimPlayerSlider(.5f, {}, Modifier.fillMaxWidth(), enabled = false)
            }
          else ->
            Column(Modifier.padding(20.dp)) {
              RoundedTextField("", {}, label = { Text("Search") }, singleLine = true)
              OutlinedButton({}, shape = androidx.compose.foundation.shape.CircleShape) {
                Text("Cancel")
              }
              Button({}, shape = androidx.compose.foundation.shape.CircleShape) { Text("Save") }
            }
        }
      }
      .forEach { it.measure(Constraints.fixedWidth(width)) }
    // A SideEffect is tied to successful composition/measurement, not to an arbitrary timer.
    subcompose("completion-$stage") { SideEffect { if (measured != stage) measured = stage } }
    layout(0, 0) {}
  }
}
