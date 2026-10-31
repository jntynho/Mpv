package live.mehiz.mpvkt.ui.player.controls

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import dev.vivvvek.seeker.Segment
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import live.mehiz.mpvkt.database.entities.CustomButtonEntity
import live.mehiz.mpvkt.ui.player.Decoder
import live.mehiz.mpvkt.ui.player.Panels
import live.mehiz.mpvkt.ui.player.Sheets
import live.mehiz.mpvkt.ui.player.TrackNode
import live.mehiz.mpvkt.ui.player.controls.components.sheets.AudioTracksSheet
import live.mehiz.mpvkt.ui.player.controls.components.sheets.ChaptersSheet
import live.mehiz.mpvkt.ui.player.controls.components.sheets.DecodersSheet
import live.mehiz.mpvkt.ui.player.controls.components.sheets.MoreSheet
import live.mehiz.mpvkt.ui.player.controls.components.sheets.PlaybackSpeedSheet
import live.mehiz.mpvkt.ui.player.controls.components.sheets.SubtitlesSheet

@Composable
fun PlayerSheets(
  sheetShown: Sheets,

  // subtitles sheet
  subtitles: ImmutableList<TrackNode>,
  onAddSubtitle: (Uri, Int, Long) -> Unit,
  onSelectSubtitle: (Int, Int) -> Unit,
  onSearchSubtitle: (Int) -> Unit,
  // audio sheet
  audioTracks: ImmutableList<TrackNode>,
  onAddAudio: (Uri, Long) -> Unit,
  onSelectAudio: (TrackNode?) -> Unit,
  // chapters sheet
  chapter: Segment?,
  chapters: ImmutableList<Segment>,
  onSeekToChapter: (Int) -> Unit,
  // Decoders sheet
  decoder: Decoder,
  onUpdateDecoder: (Decoder) -> Unit,
  // Speed sheet
  speed: Float,
  speedPresets: List<Float>,
  onSpeedChange: (Float) -> Unit,
  onAddSpeedPreset: (Float) -> Unit,
  onRemoveSpeedPreset: (Float) -> Unit,
  onResetSpeedPresets: () -> Unit,
  onMakeDefaultSpeed: (Float) -> Unit,
  onResetDefaultSpeed: () -> Unit,
  // More sheet
  sleepTimerTimeRemaining: Int,
  buttons: ImmutableList<CustomButtonEntity>,
  onOpenPanel: (Panels) -> Unit,
  mediaGeneration: Long = 0L,
  onOpenSubtitlePanel: (Panels, Int) -> Unit = { panel, _ -> onOpenPanel(panel) },
) {
  when (sheetShown) {
    Sheets.None -> {}
    Sheets.SubtitleTracks -> {
      var owner by rememberSaveable { mutableStateOf<Long?>(null) }
      var target by rememberSaveable { mutableIntStateOf(0) }
      val subtitlesPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
          val generation = owner
          owner = null
          if (it != null && generation != null) onAddSubtitle(it, target, generation)
        }
      SubtitlesSheet(
        tracks = subtitles.toImmutableList(),
        onSelect = onSelectSubtitle,
        onSearchOnline = onSearchSubtitle,
        onAddSubtitle = { slot ->
          owner = mediaGeneration
          target = slot
          subtitlesPicker.launch(arrayOf("*/*"))
        },
        onOpenSubtitleSettings = { onOpenSubtitlePanel(Panels.SubtitleSettings, it) },
        onOpenSubtitleDelay = { onOpenSubtitlePanel(Panels.SubtitleDelay, it) },
      )
    }

    Sheets.AudioTracks -> {
      var owner by rememberSaveable { mutableStateOf<Long?>(null) }
      val audioPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
          val generation = owner
          owner = null
          if (it != null && generation != null) onAddAudio(it, generation)
        }
      AudioTracksSheet(
        tracks = audioTracks,
        onSelect = onSelectAudio,
        onAddAudioTrack = {
          owner = mediaGeneration
          audioPicker.launch(arrayOf("audio/*", "video/*", "application/octet-stream"))
        },
        onOpenDelayPanel = { onOpenPanel(Panels.AudioDelay) },
      )
    }

    Sheets.Chapters -> {
      if (chapters.isEmpty()) {
        androidx.compose.foundation.layout.Box(
          androidx.compose.ui.Modifier.fillMaxSize(),
          contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
          androidx.compose.material3.Text(
            androidx.compose.ui.res.stringResource(live.mehiz.mpvkt.R.string.panel_no_chapters)
          )
        }
        return
      }
      ChaptersSheet(
        chapters,
        currentChapter = chapter,
        onClick = { onSeekToChapter(chapters.indexOf(it)) },
      )
    }

    Sheets.Decoders -> {
      DecodersSheet(selectedDecoder = decoder, onSelect = onUpdateDecoder)
    }

    Sheets.More -> {
      MoreSheet(
        remainingTime = sleepTimerTimeRemaining,
        onOpenTimer = { onOpenPanel(Panels.SleepTimer) },
        onEnterFiltersPanel = { onOpenPanel(Panels.VideoFilters) },
        customButtons = buttons,
      )
    }

    Sheets.PlaybackSpeed -> {
      PlaybackSpeedSheet(
        speed,
        onSpeedChange = onSpeedChange,
        speedPresets = speedPresets,
        onAddSpeedPreset = onAddSpeedPreset,
        onRemoveSpeedPreset = onRemoveSpeedPreset,
        onResetPresets = onResetSpeedPresets,
        onMakeDefault = onMakeDefaultSpeed,
        onResetDefault = onResetDefaultSpeed,
      )
    }
  }
}
