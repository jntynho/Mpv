package live.mehiz.mpvkt.ui.home

import org.junit.Assert.*
import org.junit.Test

class LibrarySortingTest {
  private fun video(id: String, name: String, folder: String = "A", size: Long = 0, added: Long = 0) =
    LibraryVideo(id, name, folder, folder, 999, "/$folder/$name", "/$folder", 123456,
      dateAdded = added, sizeBytes = size)

  @Test fun titleSortingIsCaseInsensitiveAndDeterministicForTies() {
    val source = listOf(video("3", "beta"), video("2", "Alpha"), video("1", "alpha"))
    fun sorted(input: List<LibraryVideo>, asc: Boolean) = buildLibraryListing(input, "A", "", LibrarySort(LibrarySortKey.Title, asc)).videos.map { it.name }
    assertEquals(listOf("Alpha", "alpha", "beta"), sorted(source, true))
    assertEquals(sorted(source, true), sorted(source.reversed(), true))
    assertEquals(listOf("beta", "Alpha", "alpha"), sorted(source, false))
  }

  @Test fun fileSizeUsesLongBytesRatherThanDurationOrTruncatedIntegers() {
    val source = listOf(video("1", "small", size = 12), video("2", "large", size = 5_000_000_000L))
    val ascending = buildLibraryListing(source, "A", "", LibrarySort(LibrarySortKey.Size, true))
    val descending = buildLibraryListing(source, "A", "", LibrarySort(LibrarySortKey.Size, false))
    assertEquals(listOf("small", "large"), ascending.videos.map { it.name })
    assertEquals(listOf("large", "small"), descending.videos.map { it.name })
    assertTrue(ascending.ready)
  }

  @Test fun folderDateUsesLatestAddedMemberInsteadOfModificationTime() {
    val source = listOf(video("1", "first", "A", added = 10), video("2", "second", "A", added = 40), video("3", "third", "B", added = 30))
    val result = buildLibraryListing(source, null, "", LibrarySort(LibrarySortKey.Date, false))
    assertEquals(listOf("A", "B"), result.folders.map { it.name })
    assertEquals(40L, result.folders.first().dateAdded)
    assertEquals(listOf("first", "second"), buildLibraryListing(source, "A", "", LibrarySort(LibrarySortKey.Date, true)).videos.map { it.name })
  }

  @Test fun searchDoesNotChangeFolderCountsSizesOrLatestDate() {
    val source = listOf(
      video("1", "needle", "A", 10, 1), video("2", "other", "A", 20, 10), video("3", "third", "A", 30, 20),
      video("4", "needle", "B", 5, 3), video("5", "other", "B", 5, 4),
    )
    val result = buildLibraryListing(source, null, "needle", LibrarySort(LibrarySortKey.Count, false))
    assertEquals(listOf("A", "B"), result.folders.map { it.name })
    assertEquals(3, result.folders.first().count)
    assertEquals(60L, result.folders.first().sizeBytes)
    assertEquals(20L, result.folders.first().dateAdded)
    assertEquals(1, buildLibraryListing(source, "A", "needle", LibrarySort()).videos.size)
  }

  @Test fun folderSizeCannotOverflowIntoANegativeSortValue() {
    val source = listOf(video("1", "a", "A", Long.MAX_VALUE), video("2", "b", "A", 500),
      video("3", "c", "B", 100), video("4", "d", "B", -10))
    val result = buildLibraryListing(source, null, "", LibrarySort(LibrarySortKey.Size, false))
    assertEquals(listOf("A", "B"), result.folders.map { it.name })
    assertEquals(Long.MAX_VALUE, result.folders[0].sizeBytes)
    assertEquals(100L, result.folders[1].sizeBytes)
  }

  @Test fun countInsideAFolderFallsBackToTitleWithoutModifyingTheSavedSetting() {
    val saved = LibrarySort(LibrarySortKey.Count, false)
    val result = buildLibraryListing(listOf(video("1", "Alpha"), video("2", "Zulu")), "A", "", saved)
    assertEquals(listOf("Zulu", "Alpha"), result.videos.map { it.name })
    assertEquals(LibrarySortKey.Title, result.sort.key)
    assertEquals(LibrarySortKey.Count, saved.key)
  }

  @Test fun directionWorksIndependentlyForAllFourFolderCriteria() {
    val source = listOf(video("1", "a", "Alpha", 10, 10), video("2", "b", "Zulu", 100, 20), video("3", "c", "Zulu", 50, 30))
    for (key in LibrarySortKey.entries) {
      assertEquals(listOf("Alpha", "Zulu"), buildLibraryListing(source, null, "", LibrarySort(key, true)).folders.map { it.name })
      assertEquals(listOf("Zulu", "Alpha"), buildLibraryListing(source, null, "", LibrarySort(key, false)).folders.map { it.name })
    }
  }

  @Test fun restoreKeepsPreviousNameAndDateChoicesAndHandlesRemovedDuration() {
    assertEquals(LibrarySort(LibrarySortKey.Title, false), LibrarySort.restore(null, true, 2))
    assertEquals(LibrarySort(LibrarySortKey.Title, true), LibrarySort.restore(null, false, 1))
    assertEquals(LibrarySort(LibrarySortKey.Date, false), LibrarySort.restore(null, true, 0))
    assertEquals(LibrarySort(LibrarySortKey.Date, false), LibrarySort.restore(null, true, 3))
    assertEquals(LibrarySort(LibrarySortKey.Size, false), LibrarySort.restore("size", false, 1))
    assertEquals(LibrarySort(), LibrarySort.restore("unrecognized", false, null))
  }
}
