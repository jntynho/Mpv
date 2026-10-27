package live.mehiz.mpvkt.ui.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Test

class PlayerPanelNavigatorTest {
  @Test fun openingFiltersNeverEmitsAClosedPane() {
    val navigation = PlayerPanelNavigator()
    navigation.openSheet(Sheets.More)
    val original = navigation.state.value.single()
    val observed = mutableListOf<List<PlayerPanelRoute>>()
    val scope = CoroutineScope(Dispatchers.Unconfined)
    try {
      scope.launch { navigation.state.collect { observed += it } }
      navigation.openPanel(Panels.VideoFilters)
      navigation.back()
      assertTrue(observed.isNotEmpty())
      assertTrue(observed.all { it.isNotEmpty() })
      assertSame(original, navigation.state.value.single())
    } finally { scope.cancel() }
  }

  @Test fun bothSubtitlePagesReturnToTheSameTracksMenu() {
    val navigation = PlayerPanelNavigator()
    navigation.openSheet(Sheets.SubtitleTracks)
    val original = navigation.state.value.single()
    for (page in listOf(Panels.SubtitleSettings, Panels.SubtitleDelay)) {
      navigation.openPanel(page)
      assertEquals(page, navigation.state.value.last().panel)
      navigation.back()
      assertSame(original, navigation.state.value.single())
    }
  }

  @Test fun longPressAndLuaEntryProvideTheCorrectParent() {
    for ((page, parent) in listOf(
      Panels.VideoFilters to Sheets.More,
      Panels.SleepTimer to Sheets.More,
      Panels.AudioDelay to Sheets.AudioTracks,
      Panels.SubtitleSettings to Sheets.SubtitleTracks,
      Panels.SubtitleDelay to Sheets.SubtitleTracks,
    )) {
      val navigation = PlayerPanelNavigator()
      navigation.openPanel(page)
      assertEquals(page, navigation.state.value.last().panel)
      navigation.back()
      assertEquals(parent, navigation.state.value.single().sheet)
    }
  }

  @Test fun repeatedTapsDoNotDuplicatePagesAndBackRetracesTheCaller() {
    val navigation = PlayerPanelNavigator()
    navigation.openSheet(Sheets.More)
    repeat(20) { navigation.openPanel(Panels.VideoFilters) }
    assertEquals(2, navigation.state.value.size)
    navigation.openPanel(Panels.AudioDelay)
    navigation.back()
    assertEquals(Panels.VideoFilters, navigation.state.value.last().panel)
    navigation.back()
    assertEquals(Sheets.More, navigation.state.value.single().sheet)
  }

  @Test fun changingRootMenuCannotRetainAnOldSubmenu() {
    val navigation = PlayerPanelNavigator()
    navigation.openSheet(Sheets.More)
    navigation.openPanel(Panels.VideoFilters)
    navigation.openSheet(Sheets.AudioTracks)
    assertEquals(listOf(PlayerPanelRoute.sheet(Sheets.AudioTracks)), navigation.state.value)
    navigation.back()
    assertFalse(navigation.isOpen)
  }

  @Test fun dismissOrPipClearsHistoryBeforeTheNextOpen() {
    val navigation = PlayerPanelNavigator()
    navigation.openPanel(Panels.SubtitleDelay)
    navigation.dismiss()
    assertFalse(navigation.isOpen)
    navigation.back() // A repeated back after dismissal is safe.
    navigation.openSheet(Sheets.More)
    assertEquals(listOf(PlayerPanelRoute.sheet(Sheets.More)), navigation.state.value)
    navigation.openSheet(Sheets.None)
    assertFalse(navigation.isOpen)
  }
}
