package live.mehiz.mpvkt.ui.home

internal data class FolderPolicy(
  val excluded: Set<String> = emptySet(),
  val included: Set<String> = emptySet(),
  val includeOnly: Boolean = false,
  val minimumDuration: Int = 0,
  val minimumSizeMb: Int = 0,
) {
  val unrestricted
    get() = excluded.isEmpty() && !includeOnly && minimumDuration <= 0 && minimumSizeMb <= 0

  fun apply(videos: List<LibraryVideo>): List<LibraryVideo> {
    if (unrestricted) return videos
    return videos.filter { video ->
      excluded.none { withinFolder(video.folderPath, it) } &&
        (!includeOnly || included.any { withinFolder(video.folderPath, it) }) &&
        video.duration >= minimumDuration.coerceAtLeast(0).toLong() * 1000L &&
        video.sizeBytes >= minimumSizeMb.coerceAtLeast(0).toLong() * 1024L * 1024L
    }
  }
}

internal fun withinFolder(path: String, parent: String): Boolean {
  val p = parent.trimEnd('/')
  return p.isNotBlank() && (path.trimEnd('/') == p || path.startsWith("$p/"))
}
