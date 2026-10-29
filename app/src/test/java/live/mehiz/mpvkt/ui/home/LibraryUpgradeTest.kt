package live.mehiz.mpvkt.ui.home

import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.ui.icons.IconKey
import live.mehiz.mpvkt.ui.preferences.optionIconKey
import org.junit.Assert.*
import org.junit.Test

class LibraryUpgradeTest {
  private fun video(name: String, duration: Long, folder: String = "folder") =
    LibraryVideo(
      "content://videos/$name",
      name,
      folder,
      folder,
      duration,
      "/$folder/$name",
      "/$folder",
      1,
    )

  @Test
  fun durationSortUsesActualDurationForFilesAndAggregatedFolderMembers() {
    val videos = listOf(video("long", 3000), video("short", 1000), video("other", 2000, "second"))
    val sort = LibrarySort(LibrarySortKey.Duration, true)
    assertEquals(
      listOf("short", "long"),
      buildLibraryListing(videos, "folder", "", sort).videos.map { it.name },
    )
    assertEquals(
      listOf("second", "folder"),
      buildLibraryListing(videos, null, "", sort).folders.map { it.name },
    )
    assertEquals(4000L, buildLibraryListing(videos, null, "", sort).folders.last().duration)
    assertEquals(
      listOf("long", "short"),
      buildLibraryListing(videos, "folder", "", sort.copy(ascending = false)).videos.map { it.name },
    )
  }

  @Test
  fun orderLabelsDescribeTheSelectedDataAndLegacySortMigrationStillWorks() {
    assertEquals(
      R.string.mpv_order_shortest,
      LibrarySort(LibrarySortKey.Duration).orderLabels().first,
    )
    assertEquals(R.string.mpv_order_newest, LibrarySort(LibrarySortKey.Date).orderLabels().second)
    assertEquals(LibrarySortKey.Duration, LibrarySort.restore("duration", false, null).key)
    assertEquals(LibrarySort(LibrarySortKey.Title, false), LibrarySort.restore(null, true, 2))
  }

  @Test
  fun historyIsUniqueBoundedAndRecencyIsUpdatedInsteadOfDuplicated() {
    var entries = emptyList<RecentMediaEntry>()
    repeat(120) {
      entries =
        RecentMedia.updated(
          entries,
          RecentMediaEntry("content://video/$it", "Video $it", it.toLong()),
        )
    }
    assertEquals(100, entries.size)
    entries = RecentMedia.updated(entries, RecentMediaEntry("content://video/50", "New title", 999))
    assertEquals("New title", entries.first().title)
    assertEquals(1, entries.count { it.uri == "content://video/50" })
    assertTrue(RecentMedia.decode("invalid json").isEmpty())
    assertEquals(
      1,
      RecentMedia.decode(
          """[{"uri":"x","title":"a","playedAt":1},{"uri":"x","title":"b","playedAt":2}]"""
        )
        .size,
    )
  }

  @Test
  fun streamValidationSupportsNetworkStreamsButRejectsInvalidAndLocalActions() {
    listOf(
        "https://example.com/video.mp4?x=1",
        "rtsp://192.168.1.2:8554/live",
        "udp://239.1.1.1:5000",
        "https://[::1]/video",
      )
      .forEach { assertTrue(it, validMediaLink(it)) }
    listOf(
        "",
        "javascript:alert(1)",
        "file:///etc/passwd",
        "intent://x",
        "https://",
        "https://example.com/a b",
      )
      .forEach { assertFalse(it, validMediaLink(it)) }
  }

  @Test
  fun optionIconsDescribeIndividualSettingsRatherThanTheWholeCategory() {
    assertEquals(IconKey.ScreenRotation, optionIconKey(R.string.pref_player_orientation))
    assertEquals(IconKey.BrightnessHigh, optionIconKey(R.string.pref_player_gestures_brightness))
    assertEquals(IconKey.VolumeUp, optionIconKey(R.string.pref_player_gestures_volume))
    assertEquals(IconKey.Language, optionIconKey(R.string.pref_preferred_languages))
    assertEquals(IconKey.Memory, optionIconKey(R.string.subtitle_ai))
  }
}
