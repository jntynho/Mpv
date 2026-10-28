package live.mehiz.mpvkt.ui.home

import org.junit.Assert.*
import org.junit.Test

class FolderPolicyTest {
  private fun video(path: String, duration: Long = 30_000, size: Long = 20L * 1024 * 1024) =
    LibraryVideo(
      "content://$path",
      "film.mp4",
      path,
      path.substringAfterLast('/'),
      duration,
      "$path/film.mp4",
      path,
      1,
      sizeBytes = size,
    )

  @Test
  fun filtersRespectPathBoundariesAndExclusionWinsAcrossEveryView() {
    val source =
      listOf(
        video("/Movies/One"),
        video("/Movies/One/Season"),
        video("/Movies/OneMore"),
        video("/Camera"),
      )
    val policy =
      FolderPolicy(
        excluded = setOf("/Movies/One/"),
        included = setOf("/Movies"),
        includeOnly = true,
      )
    val filtered = policy.apply(source)
    assertEquals(listOf("/Movies/OneMore"), filtered.map { it.folderPath })
    LibraryViewMode.entries.forEach { mode ->
      val listing = buildViewListing(filtered, null, "", LibrarySort(), mode)
      assertFalse(listing.videos.any { it.folderPath.startsWith("/Movies/One/") })
      assertEquals(1, listing.videos.size + listing.folders.sumOf { it.count })
    }
    assertEquals(4, source.size)
  }

  @Test
  fun emptyWhitelistAndSizeDurationLimitsHaveDefinedNonDestructiveResults() {
    val source = listOf(video("/A", 5000), video("/B", size = 500_000), video("/C"))
    assertTrue(FolderPolicy(includeOnly = true).apply(source).isEmpty())
    assertEquals(
      listOf("/C"),
      FolderPolicy(minimumDuration = 10, minimumSizeMb = 1).apply(source).map { it.folderPath },
    )
    assertSame(source, FolderPolicy().apply(source))
    assertSame(source, FolderPolicy(minimumDuration = -2, minimumSizeMb = -4).apply(source))
  }
}
