package live.mehiz.mpvkt.ui.home

internal enum class LibraryViewMode(val storedName: String) {
  Folder("folder"),
  Tree("tree"),
  Videos("videos");

  companion object {
    fun restore(value: String?) = entries.firstOrNull { it.storedName == value } ?: Folder
  }
}

/** A hierarchy of MediaStore-visible videos only. No raw storage scan or broader permission. */
internal fun buildViewListing(
  videos: List<LibraryVideo>,
  folder: String?,
  query: String,
  sort: LibrarySort,
  mode: LibraryViewMode,
): LibraryListing =
  when (mode) {
    LibraryViewMode.Folder -> buildLibraryListing(videos, folder, query, sort)
    LibraryViewMode.Videos ->
      buildLibraryListing(videos.map { it.copy(folderId = "all") }, "all", query, sort)
    LibraryViewMode.Tree -> {
      val groups = linkedMapOf<String, MutableList<LibraryVideo>>()
      val direct = mutableListOf<LibraryVideo>()
      videos.forEach { video ->
        val path = video.folderPath.trimEnd('/')
        if (path.split('/').any { it == ".." || it == "." }) return@forEach
        val parent = folder ?: storageRoot(path)
        if (path == parent) direct += video
        else if (
          path.startsWith(if (parent.isEmpty()) "" else if (parent == "/") "/" else "$parent/")
        ) {
          val suffix =
            path.removePrefix(if (parent.isEmpty()) "" else if (parent == "/") "/" else "$parent/")
          val child =
            (if (parent == "/") "/" else parent.takeIf(String::isNotEmpty)?.plus("/").orEmpty()) +
              suffix.substringBefore('/')
          groups.getOrPut(child) { mutableListOf() } += video
        }
      }
      val groupedVideos =
        groups.flatMap { (path, members) ->
          members.map {
            it.copy(folderId = path, folder = path.substringAfterLast('/'), folderPath = path)
          }
        }
      val folders = buildLibraryListing(groupedVideos, null, query, sort).folders
      val files =
        buildLibraryListing(direct.map { it.copy(folderId = "direct") }, "direct", query, sort)
          .videos
      LibraryListing(folders, files, sort, ready = true)
    }
  }

private fun storageRoot(path: String): String =
  when {
    path.startsWith("/storage/emulated/") -> path.split('/').take(4).joinToString("/")
    path.startsWith("/storage/") -> path.split('/').take(3).joinToString("/")
    path.startsWith("/sdcard/") || path == "/sdcard" -> "/sdcard"
    path.startsWith('/') -> "/"
    else -> ""
  }

internal fun parentTreeFolder(path: String): String? {
  val parent = path.substringBeforeLast('/', "")
  return parent.takeIf { it.isNotEmpty() && it != "/" && it != storageRoot(path) }
}
