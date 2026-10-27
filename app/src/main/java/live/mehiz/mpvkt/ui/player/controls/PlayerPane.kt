package live.mehiz.mpvkt.ui.player.controls

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.preferences.PlayerPreferences
import live.mehiz.mpvkt.preferences.preference.collectAsState
import live.mehiz.mpvkt.presentation.components.PlayerSheet
import live.mehiz.mpvkt.ui.icons.*
import live.mehiz.mpvkt.ui.player.Panels
import live.mehiz.mpvkt.ui.player.PlayerPanelRoute
import live.mehiz.mpvkt.ui.player.Sheets
import live.mehiz.mpvkt.ui.player.controls.components.PlayerPanelHeader
import org.koin.compose.koinInject

/** Both root menus and settings live in this one surface, including when navigation is interrupted. */
@Composable
internal fun PlayerPane(
  pages: List<PlayerPanelRoute>,
  onDismiss: () -> Unit,
  onBack: () -> Unit,
  content: @Composable (PlayerPanelRoute) -> Unit,
) {
  val page = pages.lastOrNull() ?: return
  val reduceMotion by koinInject<PlayerPreferences>().reduceMotion.collectAsState()
  val savedPages = rememberSaveableStateHolder()
  PlayerSheet(onDismissRequest = onDismiss, onNavigateBack = onBack.takeIf { pages.size > 1 }) { close ->
    CompositionLocalProvider(LocalOverscrollFactory provides if (reduceMotion) null else live.mehiz.mpvkt.ui.motion.LibraryOverscrollFactory) {
    Column(Modifier.fillMaxSize()) {
      PlayerPanelHeader(title = page.title(), icon = page.icon(), onClose = close, onBack = onBack.takeIf { pages.size > 1 }, animateTitle = !reduceMotion)
      AnimatedContent(
        targetState = pages,
        contentKey = { it.last().key },
        modifier = Modifier.weight(1f).fillMaxSize().clipToBounds(),
        transitionSpec = {
          val direction = if (targetState.size < initialState.size) -1 else 1
          val duration = if (reduceMotion) 0 else 240
          val enter = fadeIn(tween(duration)) + slideInHorizontally(tween(duration, easing = FastOutSlowInEasing)) { direction * it / 10 }
          val exit = fadeOut(tween(if (reduceMotion) 0 else 160)) + slideOutHorizontally(tween(duration, easing = FastOutSlowInEasing)) { -direction * it / 10 }
          (enter togetherWith exit).using(SizeTransform(clip = true, sizeAnimationSpec = { _, _ -> tween(0) }))
        },
        label = "player_pane_page",
      ) { history ->
        val destination = history.last()
        savedPages.SaveableStateProvider(destination.key) { content(destination) }
      }
    }
    }
  }
}

@Composable
private fun PlayerPanelRoute.title(): String = stringResource(when (panel) {
  Panels.VideoFilters -> R.string.player_sheets_filters_title
  Panels.SubtitleSettings -> R.string.player_sheets_subtitles_settings_title
  Panels.SubtitleDelay -> R.string.player_sheets_sub_delay_card_title
  Panels.AudioDelay -> R.string.player_sheets_audio_delay_card_title
  Panels.SleepTimer -> R.string.timer_title
  Panels.None -> when (sheet) {
    Sheets.More -> R.string.player_sheets_more_title
    Sheets.SubtitleTracks -> R.string.mpv_panel_subtitles
    Sheets.AudioTracks -> R.string.mpv_panel_audio
    Sheets.Chapters -> R.string.mpv_panel_chapters
    Sheets.Decoders -> R.string.pref_decoder
    Sheets.PlaybackSpeed -> R.string.player_sheets_speed_slider_label
    Sheets.None -> error("An empty route cannot be displayed")
  }
})

@Composable
private fun PlayerPanelRoute.icon(): ImageVector = when (sheet) {
  Sheets.More -> Icons.MoreVert
  Sheets.SubtitleTracks -> Icons.Subtitles
  Sheets.AudioTracks -> Icons.Audiotrack
  Sheets.Chapters -> Icons.Bookmarks
  Sheets.Decoders -> Icons.Memory
  Sheets.PlaybackSpeed -> Icons.Schedule
  Sheets.None -> Icons.Tune
}
