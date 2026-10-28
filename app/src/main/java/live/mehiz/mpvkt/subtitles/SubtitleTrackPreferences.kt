package live.mehiz.mpvkt.subtitles

import live.mehiz.mpvkt.ui.player.TrackNode

internal fun subtitleLanguageCodes(preferences: String): String =
  preferences
    .split(',')
    .map(String::trim)
    .map(SubtitleCatalog::language)
    .filter { it in SubtitleCatalog.languages && it != "all" }
    .distinct()
    .joinToString(",")

internal fun preferredSubtitleTrack(tracks: List<TrackNode>, preferences: String): Int? {
  val tokens = preferences.split(',').map(String::trim).filter(String::isNotBlank)
  return tracks
    .filter { it.isSubtitle }
    .mapNotNull { track ->
      val position =
        tokens.indexOfFirst { token ->
          val code = SubtitleCatalog.language(token)
          (code in SubtitleCatalog.languages &&
            code == SubtitleCatalog.language(track.lang.orEmpty())) ||
            track.title?.contains(
              Regex("(?i)(?<![\\p{L}\\p{N}])${Regex.escape(token)}(?![\\p{L}\\p{N}])")
            ) == true
        }
      if (position < 0) null else track.id to position
    }
    .minByOrNull { it.second }
    ?.first
}
