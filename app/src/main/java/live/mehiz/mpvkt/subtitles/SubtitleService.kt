package live.mehiz.mpvkt.subtitles

import java.io.File
import java.io.IOException
import java.net.URI
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.*

internal fun JsonElement?.obj() = this as? JsonObject

internal fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

internal fun JsonObject.number(key: String) = (this[key] as? JsonPrimitive)?.intOrNull

internal fun JsonObject.flag(key: String) = (this[key] as? JsonPrimitive)?.booleanOrNull

internal fun JsonObject.list(key: String) = (this[key] as? JsonArray).orEmpty()

internal fun JsonElement.rows(): List<JsonObject> =
  when (this) {
    is JsonArray -> mapNotNull { it.obj() }
    is JsonObject ->
      (this["results"]
          ?: this["data"]
          ?: this["subtitles"]
          ?: this["items"]
          ?: JsonArray(emptyList()))
        .rows()
    else -> emptyList()
  }

internal class SubtitleService(
  private val http: SubtitleTransport,
  private val apiKey: (String) -> String,
  private val directory: File,
) {
  private val fileLock = Mutex()
  private val downloadGeneration = AtomicLong()
  private val json = Json { ignoreUnknownKeys = true }
  private val wyzie = "https://sub.wyzie.io"

  internal suspend fun request(
    url: String,
    headers: Map<String, String> = emptyMap(),
  ): JsonElement =
    withTimeoutOrNull(25_000) {
      json.parseToJsonElement(String(http.get(url, headers), Charsets.UTF_8))
    } ?: throw IOException("Source timed out")

  private fun key(provider: String): String = apiKey(provider).trim()

  private fun required(provider: String) =
    key(provider).ifBlank { throw IOException("API key required") }

  suspend fun media(query: String): List<MediaMatch> =
    request(endpoint("$wyzie/api/tmdb/search", mapOf("q" to query)))
      .rows()
      .mapNotNull {
        val id = it.number("id")?.takeIf { value -> value > 0 } ?: return@mapNotNull null
        val title = it.text("title").ifBlank { it.text("name") }
        if (title.isBlank()) return@mapNotNull null
        val type = it.text("mediaType").ifBlank { it.text("media_type") }
        if (type !in setOf("movie", "tv")) return@mapNotNull null
        MediaMatch(
          id,
          title.take(200),
          type == "tv",
          it.text("releaseYear").ifBlank { it.text("release_date").take(4) },
          it
            .text("imdbId")
            .ifBlank { it.text("imdb_id") }
            .takeIf { value -> value.matches(Regex("tt\\d+")) },
          subtitlePosterUrl(it.text("poster").ifBlank { it.text("poster_path") }.ifBlank { it.text("posterUrl") }),
        )
      }
      .distinctBy { it.id to it.television }
      .take(30)

  suspend fun seasons(media: MediaMatch): List<Season> =
    request(endpoint("$wyzie/api/tmdb/tv/${media.id}", mapOf("key" to key("wyzie"))))
      .obj()
      ?.list("seasons")
      .orEmpty()
      .mapNotNull { it.obj() }
      .mapNotNull { row ->
        row.number("season_number")?.let {
          Season(it, row.text("name").take(200), row.number("episode_count") ?: 0)
        }
      }
      .filter { it.number in 0..999 }
      .distinctBy { it.number }
      .sortedBy { it.number }

  suspend fun episodes(media: MediaMatch, season: Int): List<Episode> =
    request(endpoint("$wyzie/api/tmdb/tv/${media.id}/$season", mapOf("key" to key("wyzie"))))
      .obj()
      ?.list("episodes")
      .orEmpty()
      .mapNotNull { it.obj() }
      .mapNotNull { row ->
        row.number("episode_number")?.let { Episode(it, row.text("name").take(200)) }
      }
      .filter { it.number in 0..999 }
      .distinctBy { it.number }
      .sortedBy { it.number }

  suspend fun sources(): Map<String, String> {
    val root =
      request(endpoint("$wyzie/sources", mapOf("key" to key("wyzie")))).obj()
        ?: return mapOf("all" to "All sources")
    val out = linkedMapOf("all" to "All sources")
    for (field in listOf("sources", "free", "paid", "available")) root.list(field).forEach { item ->
      (item as? JsonPrimitive)
        ?.contentOrNull
        ?.takeIf { it.matches(Regex("[a-zA-Z0-9_-]{1,80}")) }
        ?.let { out[it] = it.replaceFirstChar(Char::uppercaseChar) }
    }
    root
      .list("tiered")
      .mapNotNull { it.obj() }
      .forEach { row ->
        val id = row.text("key")
        if (id.matches(Regex("[a-zA-Z0-9_-]{1,80}")) && row.flag("available") != false)
          out[id] = row.text("name").ifBlank { id }.take(100)
        else if (row.flag("available") == false) out.remove(id)
      }
    return out
  }

  suspend fun search(
    request: SubtitleRequest,
    options: SubtitleSearchOptions,
    update: (SearchOutcome) -> Unit = {},
  ): SearchOutcome = supervisorScope {
    require(request.title.isNotBlank()) { "Enter a movie or series title" }
    if (request.media?.television == true && (request.season == null || request.episode == null))
      throw IOException("Choose a season and episode")
    val names = linkedSetOf<String>()
    if (options.mode != "subhub") names += "wyzie"
    if (options.mode != "wyzie") {
      names += options.hubSources - "all"
      if ("all" in options.hubSources) names += SubtitleCatalog.keyless
    }
    if (names.isEmpty()) throw IOException("Select at least one subtitle source")
    val semaphore = Semaphore(3)
    val collected = linkedMapOf<String, OnlineSubtitle>()
    val errors = mutableListOf<String>()
    val lock = Any()
    val html = SubtitleSites(http)
    names
      .map { source ->
        async(Dispatchers.IO) {
          try {
            val rows =
              semaphore.withPermit {
                withTimeout(45_000) {
                  when (source) {
                    "wyzie" -> searchWyzie(request, options)
                    in SubtitleCatalog.keyless -> html.search(source, request, options)
                    else -> searchApi(source, request, options)
                  }
                }
              }
            synchronized(lock) {
              rows
                .filter {
                  it.url.length <= 8192 &&
                    runCatching { validatedSubtitleUri(it.url) }.isSuccess &&
                    SubtitleCatalog.accepts(it, options) &&
                    episodeMatches(it.name, request)
                }
                .take(250)
                .forEach {
                  if (!collected.containsKey(it.url))
                    collected[it.url] =
                      it.copy(name = it.name.take(500), source = it.source.take(80))
                }
              update(SearchOutcome(collected.values.toList(), errors.toList()))
            }
          } catch (timeout: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            synchronized(lock) {
              errors += "${SubtitleCatalog.keys[source] ?: SubtitleCatalog.hub[source]}: timed out"
              update(SearchOutcome(collected.values.toList(), errors.toList()))
            }
          } catch (cancelled: CancellationException) {
            throw cancelled
          } catch (error: Exception) {
            val description =
              if (
                error.message in
                  setOf(
                    "API key required",
                    "IMDb ID required",
                    "Choose a movie or series from title results",
                  )
              )
                error.message!!
              else if (error.message?.startsWith("HTTP ") == true)
                error.message!!.substringBefore(" from")
              else "Search unavailable"
            synchronized(lock) {
              errors +=
                "${SubtitleCatalog.keys[source] ?: SubtitleCatalog.hub[source] ?: source}: $description"
              update(SearchOutcome(collected.values.toList(), errors.toList()))
            }
          }
        }
      }
      .awaitAll()
    SearchOutcome(collected.values.toList(), errors.toList())
  }

  private suspend fun searchWyzie(
    r: SubtitleRequest,
    o: SubtitleSearchOptions,
  ): List<OnlineSubtitle> {
    val media = r.media ?: throw IOException("Choose a movie or series from title results")
    fun values(v: Set<String>) = v.takeUnless { it.isEmpty() || "all" in it }?.joinToString(",")
    val url =
      endpoint(
        "$wyzie/search",
        mapOf(
          "id" to media.id,
          "season" to r.season,
          "episode" to r.episode,
          "language" to values(o.languages),
          "format" to values(o.formats),
          "encoding" to values(o.encodings),
          "source" to values(o.wyzieSources),
          "hi" to true.takeIf { o.hearingImpaired },
          "unzip" to true,
          "key" to key("wyzie"),
        ),
      )
    return request(url).rows().mapNotNull { row ->
      val target = row.text("url")
      if (target.isBlank()) return@mapNotNull null
      OnlineSubtitle(
        target,
        row.text("fileName").ifBlank {
          row.text("release").ifBlank { row.text("media").ifBlank { r.title } }
        },
        SubtitleCatalog.language(row.text("language").ifBlank { row.text("display") }),
        row.text("source").ifBlank { "Wyzie" },
        row.text("format"),
        row.text("encoding"),
        row.flag("isHearingImpaired") == true,
        row.flag("ai") ?: false,
      )
    }
  }

  private suspend fun searchApi(
    source: String,
    r: SubtitleRequest,
    o: SubtitleSearchOptions,
  ): List<OnlineSubtitle> {
    val secret = required(source)
    fun result(
      row: JsonObject,
      url: String,
      name: String,
      language: String,
      format: String = "zip",
      hi: Boolean = false,
    ) =
      OnlineSubtitle(
        url,
        name,
        SubtitleCatalog.language(language),
        SubtitleCatalog.keys.getValue(source),
        format,
        hearingImpaired = hi,
        authenticated = source,
      )
    return when (source) {
      "subdl" -> {
        val params =
          linkedMapOf<String, Any?>(
            "api_key" to secret,
            "type" to if (r.season != null) "tv" else "movie",
            "tmdb_id" to r.media?.id,
            "film_name" to r.title.takeIf { r.media == null },
            "season_number" to r.season,
            "episode_number" to r.episode,
            "languages" to
              o.languages
                .takeUnless { "all" in it }
                ?.joinToString(",") { SubtitleCatalog.languages[it].orEmpty().uppercase() },
            "subs_per_page" to 50,
            "releases" to 1,
            "unpack" to 1,
          )
        val root =
          request(endpoint("https://api.subdl.com/api/v1/subtitles", params)).obj()
            ?: return emptyList()
        if (root.flag("status") == false) throw IOException("Provider rejected search")
        root
          .list("subtitles")
          .mapNotNull { it.obj() }
          .flatMap { parent ->
            val unpack = parent.list("unpack_files").mapNotNull { it.obj() }
            (unpack.ifEmpty { listOf(parent) }).mapNotNull { row ->
              val ep = row.number("episode")
              if (r.episode != null && ep != null && r.episode != ep) return@mapNotNull null
              val path = row.text("url").ifBlank { parent.text("url") }
              if (path.isBlank()) return@mapNotNull null
              result(
                row,
                absolute("https://dl.subdl.com/", path),
                row
                  .text("name")
                  .ifBlank { parent.text("name") }
                  .ifBlank { r.episodeQuery + ".zip" },
                row.text("language").ifBlank { parent.text("language") },
              )
            }
          }
      }
      "jimaku" -> {
        val headers = mapOf("Authorization" to secret)
        val entries =
          request(
              endpoint(
                "https://jimaku.cc/api/entries/search",
                mapOf("query" to r.title, "anime" to true),
              ),
              headers,
            )
            .rows()
            .ifEmpty {
              request(
                  endpoint(
                    "https://jimaku.cc/api/entries/search",
                    mapOf("query" to r.title, "anime" to false),
                  ),
                  headers,
                )
                .rows()
            }
        val entry =
          entries
            .filter { matchScore(r.title, it.text("name") + " " + it.text("english_name")) >= 60 }
            .maxByOrNull { matchScore(r.title, it.text("name") + " " + it.text("english_name")) }
            ?: return emptyList()
        val id = entry.number("id") ?: return emptyList()
        request(
            endpoint("https://jimaku.cc/api/entries/$id/files", mapOf("episode" to r.episode)),
            headers,
          )
          .rows()
          .mapNotNull { row ->
            val url = row.text("url")
            if (url.isBlank()) null
            else
              result(
                row,
                absolute("https://jimaku.cc/", url),
                row.text("name"),
                "ja",
                row.text("name").substringAfterLast('.'),
              )
          }
      }
      "subsource" -> {
        val headers = mapOf("X-API-Key" to secret)
        val entries =
          request(
              endpoint(
                "https://api.subsource.net/api/v1/movies/search",
                mapOf(
                  "searchType" to "text",
                  "q" to r.title,
                  "type" to if (r.season == null) "all" else "series",
                  "season" to r.season,
                ),
              ),
              headers,
            )
            .rows()
        val movie =
          entries
            .filter {
              r.media?.id != null && it.number("tmdbId") == r.media.id ||
                matchScore(r.title, it.text("title")) >= 60
            }
            .maxByOrNull { matchScore(r.title, it.text("title")) } ?: return emptyList()
        val id = movie.number("movieId") ?: return emptyList()
        request(
            endpoint(
              "https://api.subsource.net/api/v1/subtitles",
              mapOf("movieId" to id, "limit" to 100, "sort" to "popular"),
            ),
            headers,
          )
          .rows()
          .mapNotNull { row ->
            val sid = row.number("subtitleId") ?: return@mapNotNull null
            val name =
              row
                .list("releaseInfo")
                .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                .joinToString(" · ")
                .ifBlank { r.episodeQuery + ".zip" }
            if (!episodeMatches(name, r)) return@mapNotNull null
            result(
              row,
              "https://api.subsource.net/api/v1/subtitles/$sid/download",
              name,
              row.text("language"),
              hi = row.flag("hearingImpaired") == true,
            )
          }
      }
      "subsro" -> {
        val imdb =
          r.media?.imdb
            ?: Regex("tt\\d{7,10}").find(r.title)?.value
            ?: throw IOException("IMDb ID required")
        val headers = mapOf("X-Subs-Api-Key" to secret)
        listOf("en", "ro")
          .filter { "all" in o.languages || it in o.languages }
          .flatMap { language ->
            request(
                endpoint(
                  "https://api.subs.ro/v1.0/search/imdbid/$imdb",
                  mapOf("language" to language),
                ),
                headers,
              )
              .rows()
              .mapNotNull { row ->
                val id = row.text("id")
                if (id.isBlank()) return@mapNotNull null
                val name = row.text("title")
                if (!episodeMatches(name + row.text("description"), r)) return@mapNotNull null
                result(
                  row,
                  absolute(
                    "https://api.subs.ro/v1.0/",
                    row.text("downloadLink").ifBlank { "subtitle/$id/download" },
                  ),
                  name,
                  language,
                )
              }
          }
      }
      "subx" ->
        request(
            endpoint(
              "https://subx-api.duckdns.org/api/subtitles/search",
              mapOf(
                "title" to r.episodeQuery,
                "limit" to 50,
                "video_type" to if (r.season == null) "movie" else "episode",
              ),
            ),
            mapOf("Authorization" to "Bearer $secret"),
          )
          .rows()
          .mapNotNull { row ->
            if (
              r.season != null && row.number("season") != r.season ||
                r.episode != null &&
                  row.number("episode") != null &&
                  row.number("episode") != r.episode
            )
              return@mapNotNull null
            val id = row.text("id")
            if (id.isBlank()) return@mapNotNull null
            result(
              row,
              absolute(
                "https://subx-api.duckdns.org/",
                row.text("download_url").ifBlank { "api/subtitles/$id/download" },
              ),
              row.text("title").ifBlank { r.episodeQuery + ".zip" },
              "es",
            )
          }
      "betaseries" -> {
        if (r.season == null || r.episode == null) return emptyList()
        val shows =
          request(
              endpoint(
                "https://api.betaseries.com/shows/search",
                mapOf("key" to secret, "v" to "3.0", "title" to r.title),
              )
            )
            .obj()
            ?.list("shows")
            .orEmpty()
            .mapNotNull { it.obj() }
        val show =
          shows
            .filter { matchScore(r.title, it.text("title")) >= 60 }
            .maxByOrNull { matchScore(r.title, it.text("title")) } ?: return emptyList()
        val root =
          request(
              endpoint(
                "https://api.betaseries.com/shows/episodes",
                mapOf(
                  "key" to secret,
                  "v" to "3.0",
                  "id" to show.number("id"),
                  "season" to r.season,
                  "episode" to r.episode,
                  "subtitles" to 1,
                ),
              )
            )
            .obj()
        val episode = root?.list("episodes")?.firstOrNull()?.obj() ?: root?.get("episode").obj()
        episode
          ?.list("subtitles")
          .orEmpty()
          .mapNotNull { it.obj() }
          .mapNotNull { row ->
            val url = row.text("url")
            val language =
              when (row.text("language")) {
                "vo" -> "en"
                "vf" -> "fr"
                else -> return@mapNotNull null
              }
            if (url.isBlank() || row.text("source") == "seriessub") null
            else result(row, url, row.text("file").ifBlank { r.episodeQuery + ".zip" }, language)
          }
      }
      else -> emptyList()
    }
  }

  suspend fun download(
    subtitle: OnlineSubtitle,
    request: SubtitleRequest,
  ): List<Pair<String, File>> {
    val generation = downloadGeneration.get()
    val url = if (subtitle.resolvePage) SubtitleSites(http).resolve(subtitle) else subtitle.url
    val headers =
      when (subtitle.authenticated) {
        "jimaku" -> authenticated(url, "jimaku.cc", "Authorization", required("jimaku"))
        "subsource" -> authenticated(url, "api.subsource.net", "X-API-Key", required("subsource"))
        "subsro" -> authenticated(url, "api.subs.ro", "X-Subs-Api-Key", required("subsro"))
        "subx" ->
          authenticated(url, "subx-api.duckdns.org", "Authorization", "Bearer " + required("subx"))
        else -> emptyMap()
      }
    val bytes = http.get(url, headers, limit = 20 * 1024 * 1024)
    val entries = SubtitleFiles.unpack(bytes, subtitle.name, request)
    return fileLock.withLock {
      check(generation == downloadGeneration.get()) { "Downloads were cleared" }
      entries.map { entry -> entry.name to SubtitleFiles.save(directory, url, entry) }
    }
  }

  private fun authenticated(
    url: String,
    host: String,
    name: String,
    secret: String,
  ): Map<String, String> {
    check(validatedSubtitleUri(url).host.equals(host, true)) { "Invalid provider download host" }
    return mapOf(name to secret)
  }

  suspend fun clearDownloads() {
    downloadGeneration.incrementAndGet()
    fileLock.withLock {
      directory
        .listFiles()
        ?.filter { it.isFile }
        ?.forEach { check(it.delete()) { "Could not clear a subtitle file" } }
    }
  }
}

internal fun absolute(base: String, path: String) =
  URI(base).resolve(path.replace(" ", "%20")).toString()

internal fun matchScore(query: String, title: String): Int {
  val parts =
    query.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").split(' ').filter(String::isNotBlank)
  val other = title.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ")
  return parts.count { Regex("\\b${Regex.escape(it)}\\b").containsMatchIn(other) } * 100 /
    parts.size.coerceAtLeast(1)
}

internal fun episodeMatches(name: String, request: SubtitleRequest): Boolean {
  val parsed = MediaQuery.parse(name)
  return request.episode == null ||
    parsed.episode == null ||
    parsed.episode == request.episode && (parsed.season == null || parsed.season == request.season)
}
