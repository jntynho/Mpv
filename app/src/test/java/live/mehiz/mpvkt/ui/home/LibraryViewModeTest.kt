package live.mehiz.mpvkt.ui.home

import org.junit.Assert.*
import org.junit.Test

class LibraryViewModeTest {
  private fun video(id: String, path: String) =
    LibraryVideo(
      "content://$id",
      "$id.mp4",
      path,
      path.substringAfterLast('/'),
      1000,
      "$path/$id.mp4",
      path,
      1,
      sizeBytes = 20,
    )

  @Test
  fun treeShowsImmediateChildrenAndDirectFilesWithoutDuplicatesOrSiblingLeakage() {
    val source =
      listOf(
        video("direct", "/storage/emulated/0/Movies"),
        video("ep1", "/storage/emulated/0/Movies/Series/S1"),
        video("ep2", "/storage/emulated/0/Movies/Series/S2"),
        video("other", "/storage/emulated/0/Movies2"),
      )
    fun at(path: String?) = buildViewListing(source, path, "", LibrarySort(), LibraryViewMode.Tree)
    assertEquals(listOf("Movies", "Movies2"), at(null).folders.map { it.name })
    assertEquals(listOf("Series"), at("/storage/emulated/0/Movies").folders.map { it.name })
    assertEquals(listOf("direct.mp4"), at("/storage/emulated/0/Movies").videos.map { it.name })
    assertEquals(
      listOf("S1", "S2"),
      at("/storage/emulated/0/Movies/Series").folders.map { it.name },
    )
    assertEquals(2, at("/storage/emulated/0/Movies").folders.single().count)
    assertEquals(
      "/storage/emulated/0/Movies",
      parentTreeFolder("/storage/emulated/0/Movies/Series"),
    )
    assertNull(parentTreeFolder("/storage/emulated/0/Movies"))
  }

  @Test
  fun videosModeSortsAllVideosAndRetainsOriginalUrisAndFolderMetadata() {
    val source =
      listOf(video("z", "/storage/emulated/0/Movies"), video("a", "/storage/ABCD/Camera"))
    val result =
      buildViewListing(
        source,
        null,
        "",
        LibrarySort(LibrarySortKey.Title, false),
        LibraryViewMode.Videos,
      )
    assertTrue(result.folders.isEmpty())
    assertEquals(listOf("content://z", "content://a"), result.videos.map { it.uri })
    assertEquals(source[0].folderPath, result.videos[0].folderPath)
    assertEquals(2, source.size)
  }

  @Test
  fun treeSupportsRelativeLegacyAndSecondaryStorageAndSkipsMalformedPaths() {
    val source =
      listOf(
        video("a", "Movies/Series"),
        video("b", "/storage/ABCD/Movies/Series"),
        video("c", "/sdcard/Camera"),
        video("d", "/legacy/Films"),
        video("bad", "/storage/emulated/0/../Secret"),
      )
    val root = buildViewListing(source, null, "", LibrarySort(), LibraryViewMode.Tree)
    assertEquals(4, root.folders.size)
    assertTrue(root.folders.any { it.id == "/legacy" })
    assertTrue(root.folders.none { it.path.contains("..") })
    assertNull(parentTreeFolder("Movies"))
    assertNull(parentTreeFolder("/legacy"))
    assertNull(parentTreeFolder("/storage/ABCD/Movies"))
    assertEquals(LibraryViewMode.Folder, LibraryViewMode.restore("invalid"))
  }

  @Test
  fun searchKeepsAncestorFoldersReachableAndCountsWholeSubtree() {
    val source =
      listOf(
        video("needle", "/storage/emulated/0/Movies/Series"),
        video("other", "/storage/emulated/0/Movies/Series"),
      )
    val result = buildViewListing(source, null, "needle", LibrarySort(), LibraryViewMode.Tree)
    assertEquals("Movies", result.folders.single().name)
    assertEquals(2, result.folders.single().count)
    assertEquals(
      1,
      buildViewListing(
          source,
          "/storage/emulated/0/Movies/Series",
          "needle",
          LibrarySort(),
          LibraryViewMode.Tree,
        )
        .videos
        .size,
    )
  }
}
