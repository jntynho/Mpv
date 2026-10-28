package live.mehiz.mpvkt.subtitles

import java.io.IOException
import java.net.URI
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Small, bounded HTML adapters. Pages are parsed as data; scripts and WebViews are never executed.
 */
internal class SubtitleSites(private val http: SubtitleTransport) {
  private val sites =
    mapOf(
      "subtitlecat" to "https://www.subtitlecat.com/",
      "moviesubtitles" to "https://www.moviesubtitles.org/",
      "moviesubtitlesrt" to "https://moviesubtitlesrt.com/",
      "mysubs" to "https://my-subs.co/",
      "tvsubtitles" to "https://www.tvsubtitles.net/",
    )

  private suspend fun page(url: String, form: Map<String, String>? = null): Document =
    Jsoup.parse(String(http.get(url, form = form, limit = 768 * 1024), Charsets.UTF_8), url)

  private fun link(anchor: Element): String =
    anchor.absUrl("href").let { value ->
      if (value.startsWith("http://") && URI(value).host == URI(anchor.baseUri()).host)
        value.replaceFirst("http://", "https://")
      else value
    }

  private fun language(anchor: Element): String {
    val block = anchor.closest("tr, div.sub-single, div.row, p") ?: anchor.parent() ?: anchor
    val image = block.selectFirst("img[alt], img[src*=flags]")
    val raw =
      image?.attr("alt")?.takeIf { it.length in 2..3 }
        ?: image?.attr("src")?.substringAfterLast('/')?.substringBefore('.')
    return SubtitleCatalog.language(raw.orEmpty())
  }

  suspend fun search(
    source: String,
    r: SubtitleRequest,
    options: SubtitleSearchOptions,
  ): List<OnlineSubtitle> {
    val base = sites.getValue(source)
    val title = SubtitleCatalog.hub.getValue(source)
    val first =
      when (source) {
        "subtitlecat" -> page(endpoint(base + "index.php", mapOf("search" to r.episodeQuery)))
        "moviesubtitlesrt" -> page(endpoint(base, mapOf("s" to r.episodeQuery)))
        "moviesubtitles" -> page(base + "search.php", mapOf("q" to r.title))
        "mysubs" -> page(endpoint(base + "search.php", mapOf("key" to r.title)))
        else -> page(base + "search1.php", mapOf("qs" to r.title))
      }
    val selector =
      when (source) {
        "subtitlecat" -> "table.sub-table a[href]"
        "moviesubtitlesrt" -> "article h2 a[href], div.inside-article header h2 a[href]"
        "moviesubtitles" -> "a[href*=movie-]"
        "mysubs" -> "a[href*=showlistsubtitles-], a[href*=film-versions-]"
        else -> "a[href*=tvshow-]"
      }
    val candidates =
      first
        .select(selector)
        .filter { matchScore(r.title, it.text().ifBlank { it.attr("title") }) >= 60 }
        .distinctBy { link(it) }
        .sortedByDescending { matchScore(r.title, it.text()) }
        .take(3)
    val output = mutableListOf<OnlineSubtitle>()
    for (candidate in candidates) {
      currentCoroutineContext().ensureActive()
      var target = link(candidate)
      if (source == "mysubs" && r.season != null && r.episode != null) {
        val found = Regex("showlistsubtitles-(\\d+)-([^/?#]+)").find(target)
        if (found != null)
          target =
            base +
              "versions-${found.groupValues[1]}-${r.episode}-${r.season}-${found.groupValues[2]}-subtitles"
      }
      if (source == "tvsubtitles" && r.season != null)
        target = target.replace(Regex("(tvshow-\\d+)(?:-\\d+)?\\.html"), "$1-${r.season}.html")
      val detail = page(target)
      when (source) {
        "subtitlecat" -> {
          detail.select("a[id^=download_][href], div.sub-single a[href]").take(150).forEach { a ->
            val lang =
              a.id().removePrefix("download_").takeIf { a.id().startsWith("download_") }
                ?: language(a)
            val url = link(a)
            val name =
              url.substringBefore('?').substringAfterLast('/').ifBlank { candidate.text() + ".srt" }
            output +=
              OnlineSubtitle(
                url,
                name,
                SubtitleCatalog.language(lang),
                title,
                name.substringAfterLast('.'),
              )
          }
        }
        "moviesubtitlesrt" -> {
          val url =
            detail.selectFirst("a[href*=.zip], a[href$=.srt], a[href$=.ass]")?.let(::link)
              ?: continue
          val text = detail.text()
          val lang =
            Regex("(?i)(?:subtitle language|language)\\s*[:：]?\\s*([A-Za-z]+)")
              .find(text)
              ?.groupValues
              ?.get(1)
              .orEmpty()
          output +=
            OnlineSubtitle(
              url,
              detail.selectFirst("h1")?.text().orEmpty().ifBlank { candidate.text() } + ".zip",
              SubtitleCatalog.language(lang),
              title,
              "zip",
            )
        }
        "moviesubtitles" -> {
          detail.select("a[href*=subtitle-]").take(80).forEach { a ->
            val block = a.parent()?.parent() ?: a
            val name =
              block.selectFirst("b")?.text()?.takeIf { it.isNotBlank() } ?: candidate.text()
            output +=
              OnlineSubtitle(
                link(a).replace("subtitle-", "download-"),
                name + ".zip",
                language(a),
                title,
                "zip",
                resolvePage = true,
              )
          }
        }
        "mysubs" ->
          detail.select("a[href*=/downloads/]").take(80).forEach { a ->
            val block = a.closest("tr, div.row, p") ?: a.parent() ?: a
            val name = block.text().takeIf { it.length > 10 } ?: r.episodeQuery
            output +=
              OnlineSubtitle(link(a), name + ".zip", language(a), title, "zip", resolvePage = true)
          }
        "tvsubtitles" -> {
          val episodeLinks =
            detail
              .select("table#table5 tr")
              .filter { row ->
                val ep =
                  Regex("(\\d+)x(\\d+)").find(row.selectFirst("td")?.text().orEmpty())
                    ?: return@filter false
                (r.season == null || ep.groupValues[1].toInt() == r.season) &&
                  (r.episode == null || ep.groupValues[2].toInt() == r.episode)
              }
              .flatMap { it.select("a[href*=episode-], a[href*=subtitle-]") }
              .distinctBy { link(it) }
              .take(4)
          for (a in episodeLinks) {
            val links =
              if ("episode-" in link(a)) page(link(a)).select("a[href*=subtitle-]").toList()
              else listOf(a)
            links.take(80).forEach { item ->
              val id =
                Regex("subtitle-(\\d+)").find(link(item))?.groupValues?.get(1) ?: return@forEach
              output +=
                OnlineSubtitle(
                  base + "download-$id.html",
                  r.episodeQuery + ".$id.zip",
                  language(item),
                  title,
                  "zip",
                  resolvePage = true,
                )
            }
          }
        }
      }
    }
    // Unknown-language results are not silently passed through a language filter.
    return output
      .filter { episodeMatches(it.name, r) && SubtitleCatalog.accepts(it, options) }
      .distinctBy { it.url }
  }

  suspend fun resolve(subtitle: OnlineSubtitle): String {
    val doc = page(subtitle.url)
    doc.selectFirst("a[href*=.zip], a[href$=.srt], a[href$=.ass], a[download][href]")?.let {
      return link(it)
    }
    val script = doc.select("script").joinToString("\n") { it.data() }
    val direct = Regex("REAL_URL\\s*=\\s*['\"]([^'\"]+)['\"]").find(script)?.groupValues?.get(1)
    if (direct != null) return absolute(subtitle.url, direct.replace("\\/", "/"))
    // Only concatenate literal strings and string variables; never evaluate provider
    // JavaScript.
    val variables =
      Regex("(?:var|let|const)\\s+(\\w+)\\s*=\\s*['\"]([^'\"]*)['\"]").findAll(script).associate {
        it.groupValues[1] to it.groupValues[2]
      }
    val expression =
      Regex("(?:document|window)\\.location(?:\\.href)?\\s*=\\s*([^;]+);")
        .find(script)
        ?.groupValues
        ?.get(1)
    if (expression != null) {
      val pieces = expression.split('+').map { it.trim() }
      val resolved =
        pieces
          .map { term ->
            if (term.length >= 2 && term.first() in "'\"" && term.last() == term.first())
              term.substring(1, term.length - 1)
            else variables[term] ?: throw IOException("Unsupported provider download link")
          }
          .joinToString("")
      return absolute(subtitle.url, resolved)
    }
    throw IOException("Provider download link is unavailable")
  }
}
