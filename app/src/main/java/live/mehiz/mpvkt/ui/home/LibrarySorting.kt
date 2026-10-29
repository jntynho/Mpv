package live.mehiz.mpvkt.ui.home

internal enum class LibrarySortKey(val storedName: String) {
  Title("title"),
  Date("date"),
  Size("size"),
  Count("count"),
  Duration("duration"),
}

internal data class LibrarySort(
  val key: LibrarySortKey = LibrarySortKey.Title,
  val ascending: Boolean = true,
) {
  fun forFiles() = if (key == LibrarySortKey.Count) copy(key = LibrarySortKey.Title) else this

  companion object {
    fun restore(key: String?, ascending: Boolean, legacy: Int?): LibrarySort {
      LibrarySortKey.entries
        .firstOrNull { it.storedName == key }
        ?.let {
          return LibrarySort(it, ascending)
        }
      return when (legacy) {
        0,
        3 -> LibrarySort(LibrarySortKey.Date, false)
        1 -> LibrarySort(LibrarySortKey.Title, true)
        2 -> LibrarySort(LibrarySortKey.Title, false)
        else -> LibrarySort()
      }
    }
  }
}

internal data class LibraryFolder(
  val id: String,
  val name: String,
  val path: String,
  val count: Int,
  val sizeBytes: Long,
  val dateAdded: Long,
  val duration: Long = 0,
)

internal data class LibraryListing(
  val folders: List<LibraryFolder> = emptyList(),
  val videos: List<LibraryVideo> = emptyList(),
  val sort: LibrarySort = LibrarySort(),
  val ready: Boolean = false,
)

/** Called on Dispatchers.Default; aggregation uses all folder members even during a search. */
internal fun buildLibraryListing(
  videos: List<LibraryVideo>,
  folderId: String?,
  query: String,
  sort: LibrarySort,
): LibraryListing {
  fun LibraryVideo.matches() =
    query.isBlank() ||
      name.contains(query, true) ||
      path.contains(query, true) ||
      folder.contains(query, true)
  if (folderId != null) {
    val effective = sort.forFiles()
    val comparator =
      Comparator<LibraryVideo> { a, b ->
        val primary =
          when (effective.key) {
            LibrarySortKey.Title,
            LibrarySortKey.Count -> String.CASE_INSENSITIVE_ORDER.compare(a.name, b.name)
            LibrarySortKey.Date -> a.dateAdded.compareTo(b.dateAdded)
            LibrarySortKey.Size -> a.sizeBytes.compareTo(b.sizeBytes)
            LibrarySortKey.Duration -> a.duration.compareTo(b.duration)
          }
        val directed = if (effective.ascending) primary else -primary
        if (directed != 0) directed
        else compareNames(a.name, b.name).takeIf { it != 0 } ?: a.uri.compareTo(b.uri)
      }
    return LibraryListing(
      videos = videos.filter { it.folderId == folderId && it.matches() }.sortedWith(comparator),
      sort = effective,
      ready = true,
    )
  }
  val folders =
    videos
      .groupBy { it.folderId }
      .mapNotNull { (id, members) ->
        if (members.none { it.matches() }) return@mapNotNull null
        val first = members.first()
        LibraryFolder(
          id,
          first.folder,
          first.folderPath,
          members.size,
          members.fold(0L) { total, video ->
            val size = video.sizeBytes.coerceAtLeast(0)
            if (Long.MAX_VALUE - total < size) Long.MAX_VALUE else total + size
          },
          members.maxOf { it.dateAdded },
          members.sumOf { it.duration.coerceAtLeast(0) },
        )
      }
  val comparator =
    Comparator<LibraryFolder> { a, b ->
      val primary =
        when (sort.key) {
          LibrarySortKey.Title -> String.CASE_INSENSITIVE_ORDER.compare(a.name, b.name)
          LibrarySortKey.Date -> a.dateAdded.compareTo(b.dateAdded)
          LibrarySortKey.Size -> a.sizeBytes.compareTo(b.sizeBytes)
          LibrarySortKey.Count -> a.count.compareTo(b.count)
          LibrarySortKey.Duration -> a.duration.compareTo(b.duration)
        }
      val directed = if (sort.ascending) primary else -primary
      if (directed != 0) directed
      else compareNames(a.name, b.name).takeIf { it != 0 } ?: a.id.compareTo(b.id)
    }
  return LibraryListing(folders = folders.sortedWith(comparator), sort = sort, ready = true)
}

private fun compareNames(a: String, b: String): Int =
  String.CASE_INSENSITIVE_ORDER.compare(a, b).takeIf { it != 0 } ?: a.compareTo(b)
