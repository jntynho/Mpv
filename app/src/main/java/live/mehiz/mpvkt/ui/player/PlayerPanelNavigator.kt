package live.mehiz.mpvkt.ui.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Exactly one page inside the persistent player pane. */
internal data class PlayerPanelRoute private constructor(val sheet: Sheets, val panel: Panels) {
  val key get() = if (panel != Panels.None) "panel:${panel.name}" else "sheet:${sheet.name}"

  companion object {
    fun sheet(value: Sheets): PlayerPanelRoute {
      require(value != Sheets.None)
      return PlayerPanelRoute(value, Panels.None)
    }
    fun panel(value: Panels): PlayerPanelRoute {
      require(value != Panels.None)
      return PlayerPanelRoute(Sheets.None, value)
    }
  }
}

/** UI, long presses and Lua share the same history; changing a page never dismisses the pane. */
internal class PlayerPanelNavigator {
  private val pages = MutableStateFlow<List<PlayerPanelRoute>>(emptyList())
  val state = pages.asStateFlow()
  val isOpen get() = pages.value.isNotEmpty()

  fun openSheet(sheet: Sheets) {
    pages.value = if (sheet == Sheets.None) emptyList() else listOf(PlayerPanelRoute.sheet(sheet))
  }

  fun openPanel(panel: Panels) {
    if (panel == Panels.None) { dismiss(); return }
    val page = PlayerPanelRoute.panel(panel)
    pages.update { history ->
      val existing = history.indexOf(page)
      when {
        existing >= 0 -> history.take(existing + 1)
        history.isNotEmpty() -> history + page
        else -> listOf(PlayerPanelRoute.sheet(parentOf(panel)), page)
      }
    }
  }

  fun back() { pages.update { it.dropLast(1) } }
  fun dismiss() { pages.value = emptyList() }

  private fun parentOf(panel: Panels): Sheets = when (panel) {
    Panels.SubtitleSettings, Panels.SubtitleDelay -> Sheets.SubtitleTracks
    Panels.AudioDelay -> Sheets.AudioTracks
    Panels.VideoFilters, Panels.SleepTimer -> Sheets.More
    Panels.None -> error("None is not a panel page")
  }
}
