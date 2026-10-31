package live.mehiz.mpvkt.ui.player.controls

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
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

/**
 * Both root menus and settings live in this one surface, including when navigation is interrupted.
 */
@Composable
internal fun PlayerPane(
  pages: List<PlayerPanelRoute>,
  onDismiss: () -> Unit,
  onBack: () -> Unit,
  onCloseStart: () -> Unit = {},
  mediaKey: Long = 0L,
  content: @Composable (PlayerPanelRoute) -> Unit,
) {
  val page = pages.lastOrNull() ?: return
  val prefs = koinInject<PlayerPreferences>()
  val reduceMotion by prefs.reduceMotion.collectAsState()
  val opacity by prefs.panelTransparency.collectAsState()
  val directionSign = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1
  val focus = androidx.compose.ui.platform.LocalFocusManager.current
  LaunchedEffect(page.key) { focus.clearFocus() }
  val savedPages = key(mediaKey) { rememberSaveableStateHolder() }
  PlayerSheet(
    onDismissRequest = onDismiss,
    onNavigateBack = onBack.takeIf { pages.size > 1 },
    reduceMotion = reduceMotion,
    onCloseStart = onCloseStart,
    containerColor =
      androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer.copy(
        alpha = opacity.coerceIn(0f, 1f)
      ),
  ) { close, headerDrag ->
    CompositionLocalProvider(
      live.mehiz.mpvkt.ui.player.controls.components.LocalPanelMediaKey provides mediaKey,
      live.mehiz.mpvkt.ui.player.controls.components.LocalPanelOpacity provides
        opacity.coerceIn(0f, 1f),
      LocalOverscrollFactory provides
        if (reduceMotion) null else live.mehiz.mpvkt.ui.motion.LibraryOverscrollFactory,
    ) {
      Column(Modifier.fillMaxSize()) {
        PlayerPanelHeader(
          title = page.title(),
          icon = page.icon(),
          onClose = close,
          onBack = onBack.takeIf { pages.size > 1 },
          animateTitle = !reduceMotion,
          modifier = headerDrag,
        )
        val rejected by
          live.mehiz.mpvkt.ui.player.PanelProperties.rejectedProperties.collectAsState()
        if (rejected.isNotEmpty())
          androidx.compose.material3.Text(
            stringResource(R.string.panel_write_failed),
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = androidx.compose.material3.MaterialTheme.colorScheme.error,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
          )
        AnimatedContent(
          targetState = pages,
          contentKey = { it.last().key },
          modifier = Modifier.weight(1f).fillMaxSize().clipToBounds(),
          transitionSpec = {
            val direction = (if (targetState.size < initialState.size) -1 else 1) * directionSign
            val duration = if (reduceMotion) 0 else 240
            val enter =
              fadeIn(tween(duration)) +
                slideInHorizontally(tween(duration, easing = FastOutSlowInEasing)) {
                  direction * it / 10
                }
            val exit =
              fadeOut(tween(if (reduceMotion) 0 else 160)) +
                slideOutHorizontally(tween(duration, easing = FastOutSlowInEasing)) {
                  -direction * it / 10
                }
            (enter togetherWith exit).using(
              SizeTransform(clip = true, sizeAnimationSpec = { _, _ -> tween(0) })
            )
          },
          label = "player_pane_page",
        ) { history ->
          val destination = history.last()
          key(mediaKey) {
            savedPages.SaveableStateProvider(destination.key) {
              val inactive = destination.key != page.key
              androidx.compose.foundation.layout.Box(
                Modifier.fillMaxSize()
                  .then(if (inactive) Modifier.clearAndSetSemantics {} else Modifier)
                  .pointerInput(inactive) {
                    if (inactive)
                      awaitPointerEventScope {
                        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach {
                          it.consume()
                        }
                      }
                  }
              ) {
                content(destination)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PlayerPanelRoute.title(): String =
  stringResource(
    when (panel) {
      Panels.SubtitleSearch -> R.string.subtitle_search
      Panels.SubtitleSearchOptions -> R.string.subtitle_filters
      Panels.SubtitleSearchLanguages -> R.string.subtitle_languages
      Panels.SubtitleSearchSeason -> R.string.subtitle_season
      Panels.SubtitleSearchEpisode -> R.string.subtitle_episode
      Panels.SubtitleSearchFiles -> R.string.subtitle_choose_file
      Panels.VideoFilters -> R.string.player_sheets_filters_title
      Panels.SubtitleSettings -> R.string.player_sheets_subtitles_settings_title
      Panels.SubtitleDelay -> R.string.player_sheets_sub_delay_card_title
      Panels.AudioDelay -> R.string.player_sheets_audio_delay_card_title
      Panels.SleepTimer -> R.string.timer_title
      Panels.None ->
        when (sheet) {
          Sheets.More -> R.string.player_sheets_more_title
          Sheets.SubtitleTracks -> R.string.mpv_panel_subtitles
          Sheets.AudioTracks -> R.string.mpv_panel_audio
          Sheets.Chapters -> R.string.mpv_panel_chapters
          Sheets.Decoders -> R.string.pref_decoder
          Sheets.PlaybackSpeed -> R.string.player_sheets_speed_slider_label
          Sheets.None -> error("An empty route cannot be displayed")
        }
    }
  )

@Composable
private fun PlayerPanelRoute.icon(): ImageVector =
  when (sheet) {
    Sheets.More -> Icons.MoreVert
    Sheets.SubtitleTracks -> Icons.Subtitles
    Sheets.AudioTracks -> Icons.Audiotrack
    Sheets.Chapters -> Icons.Bookmarks
    Sheets.Decoders -> Icons.Memory
    Sheets.PlaybackSpeed -> Icons.Schedule
    Sheets.None -> Icons.Tune
  }
