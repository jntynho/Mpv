package live.mehiz.mpvkt.subtitles

import java.util.Locale

internal data class MediaQuery(
  val title: String,
  val season: Int? = null,
  val episode: Int? = null,
  val year: Int? = null,
) {
  companion object {
    private val episodePattern =
      Regex("(?i)(?:s(\\d{1,2})[ ._-]*e(\\d{1,3})|(\\d{1,2})x(\\d{1,3}))")

    fun parse(file: String): MediaQuery {
      val raw = file.substringAfterLast('/')
      val name =
        if (
          raw.substringAfterLast('.').lowercase() in
            setOf(
              "mkv",
              "mp4",
              "avi",
              "webm",
              "mov",
              "m4v",
              "srt",
              "ass",
              "ssa",
              "vtt",
              "sub",
              "zip",
              "txt",
            )
        )
          raw.substringBeforeLast('.')
        else raw
      val match = episodePattern.find(name)
      val year = Regex("\\b(19\\d{2}|20\\d{2})\\b").find(name.replace('.', ' ').replace('_', ' '))
      val end =
        listOfNotNull(
            match?.range?.first,
            year?.range?.first,
            Regex("(?i)\\b(?:480p|720p|1080p|2160p|web[ .-]?dl|bluray|hdtv)\\b")
              .find(name)
              ?.range
              ?.first,
          )
          .minOrNull() ?: name.length
      val title =
        name.take(end).replace(Regex("[._]+"), " ").trim(' ', '-', '(', '[').ifBlank { name }
      return MediaQuery(
        title,
        match?.let { (it.groupValues[1].ifEmpty { it.groupValues[3] }).toIntOrNull() },
        match?.let { (it.groupValues[2].ifEmpty { it.groupValues[4] }).toIntOrNull() },
        year?.value?.toIntOrNull(),
      )
    }
  }
}

internal data class MediaMatch(
  val id: Int,
  val title: String,
  val television: Boolean,
  val year: String = "",
  val imdb: String? = null,
  val posterUrl: String? = null,
)

internal data class Season(val number: Int, val title: String = "", val episodes: Int = 0)

internal data class Episode(val number: Int, val title: String = "")

internal data class SubtitleRequest(
  val title: String,
  val media: MediaMatch? = null,
  val season: Int? = null,
  val episode: Int? = null,
) {
  val episodeQuery
    get() =
      title +
        if (season != null && episode != null) " S%02dE%02d".format(Locale.ROOT, season, episode)
        else ""
}

internal data class OnlineSubtitle(
  val url: String,
  val name: String,
  val language: String,
  val source: String,
  val format: String = "",
  val encoding: String = "",
  val hearingImpaired: Boolean = false,
  val ai: Boolean? = null,
  val resolvePage: Boolean = false,
  val authenticated: String? = null,
)

internal data class SearchOutcome(val subtitles: List<OnlineSubtitle>, val failures: List<String>)

internal data class SubtitleSearchOptions(
  val mode: String = "hybrid",
  val hubSources: Set<String> = setOf("all"),
  val wyzieSources: Set<String> = setOf("all"),
  val languages: Set<String> = setOf("en"),
  val formats: Set<String> = setOf("srt", "ass"),
  val encodings: Set<String> = setOf("utf-8"),
  val hearingImpaired: Boolean = false,
  val ai: String = "all",
)

internal object SubtitleCatalog {
  val modes = linkedMapOf("wyzie" to "Wyzie", "subhub" to "SubHub", "hybrid" to "Hybrid")
  val hub =
    linkedMapOf(
      "all" to "All keyless sources",
      "subtitlecat" to "SubtitleCat",
      "moviesubtitles" to "MovieSubtitles.org",
      "moviesubtitlesrt" to "MovieSubtitlesRT",
      "mysubs" to "My Subs",
      "tvsubtitles" to "TVSubtitles",
      "betaseries" to "BetaSeries",
      "jimaku" to "Jimaku",
      "subdl" to "SubDL",
      "subsource" to "SubSource",
      "subsro" to "Subs.ro",
      "subx" to "SubX",
    )
  val keyless = setOf("subtitlecat", "moviesubtitles", "moviesubtitlesrt", "mysubs", "tvsubtitles")
  val keys =
    linkedMapOf(
      "wyzie" to "Wyzie",
      "betaseries" to "BetaSeries",
      "jimaku" to "Jimaku",
      "subdl" to "SubDL",
      "subsource" to "SubSource",
      "subsro" to "Subs.ro",
      "subx" to "SubX",
    )
  val languages: Map<String, String> =
    linkedMapOf("all" to "All languages") +
      Locale.getISOLanguages()
        .mapNotNull { code ->
          Locale(code)
            .getDisplayLanguage(Locale.ENGLISH)
            .takeIf { it.isNotBlank() }
            ?.let { code to it }
        }
        .sortedBy { it.second }
        .toMap() +
      linkedMapOf(
        "pt-br" to "Portuguese (Brazil)",
        "zh-cn" to "Chinese (Simplified)",
        "zh-tw" to "Chinese (Traditional)",
      )
  val formats =
    linkedMapOf(
      "all" to "All formats",
      "srt" to "SRT",
      "ass" to "ASS",
      "ssa" to "SSA",
      "vtt" to "WebVTT",
      "sub" to "SUB",
    )
  val encodings =
    linkedMapOf(
      "all" to "All encodings",
      "utf-8" to "UTF-8",
      "windows-1256" to "Arabic (Windows-1256)",
      "windows-1252" to "Western (Windows-1252)",
      "utf-16" to "UTF-16",
      "iso-8859-1" to "ISO-8859-1",
    )

  fun language(value: String): String {
    val raw = value.trim().lowercase(Locale.ROOT).replace('_', '-')
    if (raw in languages) return raw
    return mapOf(
      "ara" to "ar",
      "arabic" to "ar",
      "eng" to "en",
      "english" to "en",
      "jpn" to "ja",
      "japanese" to "ja",
      "fre" to "fr",
      "fra" to "fr",
      "french" to "fr",
      "ger" to "de",
      "deu" to "de",
      "gre" to "el",
      "gr" to "el",
      "cz" to "cs",
      "br" to "pt-br",
    )[raw]
      ?: languages.entries.firstOrNull { it.value.equals(value, true) }?.key
      ?: raw.substringBefore('-')
  }

  fun accepts(subtitle: OnlineSubtitle, options: SubtitleSearchOptions): Boolean =
    (options.languages.isEmpty() ||
      "all" in options.languages ||
      options.languages.any {
        it == language(subtitle.language) || it == language(subtitle.language).substringBefore('-')
      }) &&
      (subtitle.format.isBlank() ||
        options.formats.isEmpty() ||
        "all" in options.formats ||
        subtitle.format.lowercase() in options.formats ||
        subtitle.format in setOf("zip", "gz")) &&
      (subtitle.encoding.isBlank() ||
        options.encodings.isEmpty() ||
        "all" in options.encodings ||
        options.encodings.any { it.equals(subtitle.encoding, true) }) &&
      (!options.hearingImpaired || subtitle.hearingImpaired) &&
      (options.ai == "all" ||
        (options.ai == "human" && subtitle.ai == false) ||
        (options.ai == "ai" && subtitle.ai == true))
}
