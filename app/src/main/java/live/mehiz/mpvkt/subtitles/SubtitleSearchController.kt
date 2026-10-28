package live.mehiz.mpvkt.subtitles

import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import live.mehiz.mpvkt.preferences.SubtitlesPreferences

internal enum class SubtitleNotice {
  EpisodesUnavailable,
  NoTitle,
  TitleLookupUnavailable,
  ChooseEpisode,
  NoResults,
  SearchUnavailable,
  NoFormat,
  DownloadUnavailable,
  Added,
  AddUnavailable,
}

internal data class SubtitleSearchState(
  val query: String = "",
  val media: MediaMatch? = null,
  val matches: List<MediaMatch> = emptyList(),
  val seasons: List<Season> = emptyList(),
  val episodes: List<Episode> = emptyList(),
  val series: Boolean = false,
  val season: Int? = null,
  val episode: Int? = null,
  val slot: Int = 0,
  val subtitles: List<OnlineSubtitle> = emptyList(),
  val failures: List<String> = emptyList(),
  val loading: Boolean = false,
  val loadingEpisodes: Boolean = false,
  val downloading: String? = null,
  val files: List<Pair<String, File>> = emptyList(),
  val message: SubtitleNotice? = null,
)

internal fun SubtitlesPreferences.searchOptions() =
  SubtitleSearchOptions(
    searchMode.get(),
    hubSources.get(),
    wyzieSources.get(),
    searchLanguages.get(),
    searchFormats.get(),
    searchEncodings.get(),
    hearingImpaired.get(),
    aiResults.get(),
  )

/**
 * One generation per user request. Stale title/episode/download responses cannot affect the next
 * video.
 */
internal class SubtitleSearchController(
  private val scope: CoroutineScope,
  private val service: SubtitleService,
  private val options: () -> SubtitleSearchOptions,
) {
  private val mutable = MutableStateFlow(SubtitleSearchState())
  val state = mutable.asStateFlow()
  private var owner = ""
  @Volatile private var generation = 0L
  private var job: Job? = null

  private fun invalidate(): Long {
    job?.cancel()
    return ++generation
  }

  fun stop() {
    invalidate()
    mutable.update {
      it.copy(loading = false, loadingEpisodes = false, downloading = null, files = emptyList())
    }
  }

  fun begin(title: String, slot: Int, ownerKey: String = title) {
    if (owner != ownerKey) {
      invalidate()
      owner = ownerKey
      val parsed = MediaQuery.parse(title)
      mutable.value =
        SubtitleSearchState(
          query = parsed.title,
          series = parsed.season != null,
          season = parsed.season,
          episode = parsed.episode,
          slot = slot,
        )
      if (parsed.title.isNotBlank()) findMedia()
    } else mutable.update { it.copy(slot = slot) }
  }

  fun editQuery(query: String) {
    invalidate()
    mutable.update {
      it.copy(
        query = query.take(500),
        media = null,
        matches = emptyList(),
        subtitles = emptyList(),
        failures = emptyList(),
        message = null,
        seasons = emptyList(),
        episodes = emptyList(),
        loading = false,
        loadingEpisodes = false,
        downloading = null,
        files = emptyList(),
      )
    }
  }

  fun setSeries(value: Boolean) {
    invalidate()
    mutable.update {
      it.copy(
        series = value,
        media = null,
        seasons = emptyList(),
        episodes = emptyList(),
        season = if (value) it.season ?: 1 else null,
        episode = if (value) it.episode ?: 1 else null,
        subtitles = emptyList(),
        files = emptyList(),
        loading = false,
        loadingEpisodes = false,
        downloading = null,
      )
    }
  }

  fun setSlot(slot: Int) {
    val cancelDownload = state.value.downloading != null
    if (cancelDownload) invalidate()
    mutable.update {
      it.copy(
        slot = slot.coerceIn(0, 1),
        files = emptyList(),
        downloading = null,
        loading = if (cancelDownload) false else it.loading,
      )
    }
  }

  fun clearFiles() {
    mutable.update { it.copy(files = emptyList()) }
  }

  fun setSeason(number: Int?) {
    val ticket = invalidate()
    val selected = state.value.media
    mutable.update {
      it.copy(
        season = number,
        episode = null,
        episodes = emptyList(),
        subtitles = emptyList(),
        loading = false,
        loadingEpisodes = false,
        files = emptyList(),
        downloading = null,
      )
    }
    if (selected != null && number != null) {
      mutable.update { it.copy(loadingEpisodes = true) }
      job =
        scope.launch {
          try {
            val episodes = withContext(Dispatchers.IO) { service.episodes(selected, number) }
            if (ticket == generation)
              mutable.update {
                it.copy(
                  episodes = episodes,
                  episode = episodes.firstOrNull()?.number,
                  loadingEpisodes = false,
                )
              }
          } catch (c: CancellationException) {
            throw c
          } catch (_: Exception) {
            if (ticket == generation)
              mutable.update {
                it.copy(loadingEpisodes = false, message = SubtitleNotice.EpisodesUnavailable)
              }
          }
        }
    }
  }

  fun setEpisode(number: Int?) {
    invalidate()
    mutable.update {
      it.copy(
        episode = number,
        subtitles = emptyList(),
        files = emptyList(),
        loading = false,
        loadingEpisodes = false,
        downloading = null,
      )
    }
  }

  fun findMedia() {
    val snapshot = state.value
    if (snapshot.query.isBlank()) return
    if (snapshot.media != null) {
      search()
      return
    }
    val ticket = invalidate()
    mutable.update {
      it.copy(
        loading = true,
        message = null,
        matches = emptyList(),
        subtitles = emptyList(),
        failures = emptyList(),
        files = emptyList(),
        downloading = null,
      )
    }
    job =
      scope.launch {
        try {
          val matches = withContext(Dispatchers.IO) { service.media(snapshot.query.trim()) }
          if (ticket != generation) return@launch
          mutable.update {
            it.copy(
              matches = matches,
              loading = false,
              message = if (matches.isEmpty()) SubtitleNotice.NoTitle else null,
            )
          }
        } catch (c: CancellationException) {
          throw c
        } catch (_: Exception) {
          if (ticket == generation)
            mutable.update {
              it.copy(loading = false, message = SubtitleNotice.TitleLookupUnavailable)
            }
        }
      }
  }

  fun selectMedia(media: MediaMatch) {
    val old = state.value
    val ticket = invalidate()
    mutable.update {
      it.copy(
        media = media,
        query = media.title,
        series = media.television,
        matches = emptyList(),
        seasons = emptyList(),
        episodes = emptyList(),
        season = if (media.television) old.season else null,
        episode = if (media.television) old.episode else null,
        subtitles = emptyList(),
        message = null,
        loading = false,
        loadingEpisodes = media.television,
        downloading = null,
        files = emptyList(),
      )
    }
    if (!media.television) {
      search()
      return
    }
    job =
      scope.launch {
        try {
          val seasons = withContext(Dispatchers.IO) { service.seasons(media) }
          val season =
            seasons.firstOrNull { it.number == old.season }
              ?: seasons.firstOrNull { it.number > 0 }
              ?: seasons.firstOrNull()
          val episodes =
            if (season != null)
              withContext(Dispatchers.IO) { service.episodes(media, season.number) }
            else emptyList()
          if (ticket == generation)
            mutable.update {
              it.copy(
                seasons = seasons,
                episodes = episodes,
                season = season?.number ?: old.season ?: 1,
                episode =
                  episodes.firstOrNull { it.number == old.episode }?.number
                    ?: old.episode
                    ?: episodes.firstOrNull()?.number
                    ?: 1,
                loadingEpisodes = false,
              )
            }
        } catch (c: CancellationException) {
          throw c
        } catch (_: Exception) {
          if (ticket == generation)
            mutable.update {
              it.copy(
                loadingEpisodes = false,
                season = old.season ?: 1,
                episode = old.episode ?: 1,
                message = SubtitleNotice.EpisodesUnavailable,
              )
            }
        }
      }
  }

  private fun request(s: SubtitleSearchState) =
    SubtitleRequest(
      s.query.trim(),
      s.media,
      s.season.takeIf { s.series },
      s.episode.takeIf { s.series },
    )

  fun search() {
    val snapshot = state.value
    if (snapshot.query.isBlank()) return
    if (snapshot.series && (snapshot.season == null || snapshot.episode == null)) {
      mutable.update { it.copy(message = SubtitleNotice.ChooseEpisode) }
      return
    }
    val ticket = invalidate()
    mutable.update {
      it.copy(
        loading = true,
        loadingEpisodes = false,
        message = null,
        subtitles = emptyList(),
        matches = emptyList(),
        files = emptyList(),
        downloading = null,
        failures = emptyList(),
      )
    }
    job =
      scope.launch {
        try {
          withContext(Dispatchers.IO) {
            service.search(request(snapshot), options()) { outcome ->
              if (ticket == generation)
                mutable.update {
                  it.copy(subtitles = outcome.subtitles, failures = outcome.failures)
                }
            }
          }
          if (ticket == generation)
            mutable.update {
              it.copy(
                loading = false,
                message = if (it.subtitles.isEmpty()) SubtitleNotice.NoResults else null,
              )
            }
        } catch (c: CancellationException) {
          throw c
        } catch (_: Exception) {
          if (ticket == generation)
            mutable.update { it.copy(loading = false, message = SubtitleNotice.SearchUnavailable) }
        }
      }
  }

  fun download(subtitle: OnlineSubtitle, apply: (File, Int) -> Unit) {
    val snapshot = state.value
    val ticket = invalidate()
    mutable.update {
      it.copy(
        downloading = subtitle.url,
        loading = false,
        loadingEpisodes = false,
        message = null,
        files = emptyList(),
      )
    }
    job =
      scope.launch {
        try {
          val files =
            withTimeoutOrNull(60_000) {
              withContext(Dispatchers.IO) {
                val formats = options().formats
                service.download(subtitle, request(snapshot)).filter { (_, file) ->
                  formats.isEmpty() || "all" in formats || file.extension.lowercase() in formats
                }
              }
            } ?: throw java.io.IOException("Subtitle download timed out")
          if (ticket != generation) return@launch
          when (files.size) {
            0 -> mutable.update { it.copy(downloading = null, message = SubtitleNotice.NoFormat) }
            1 -> {
              val ambiguousEpisode =
                snapshot.series &&
                  MediaQuery.parse(subtitle.name).episode == null &&
                  MediaQuery.parse(files[0].first).episode == null
              if (ambiguousEpisode) mutable.update { it.copy(downloading = null, files = files) }
              else {
                mutable.update { it.copy(downloading = null) }
                apply(files[0].second, snapshot.slot)
              }
            }
            else -> mutable.update { it.copy(downloading = null, files = files) }
          }
        } catch (c: CancellationException) {
          throw c
        } catch (_: Exception) {
          if (ticket == generation)
            mutable.update {
              it.copy(downloading = null, message = SubtitleNotice.DownloadUnavailable)
            }
        }
      }
  }

  fun reportApplied(success: Boolean) {
    mutable.update {
      it.copy(message = if (success) SubtitleNotice.Added else SubtitleNotice.AddUnavailable)
    }
  }

  fun chooseFile(file: File, apply: (File, Int) -> Unit) {
    if (state.value.files.any { it.second == file }) {
      apply(file, state.value.slot)
      mutable.update { it.copy(files = emptyList()) }
    }
  }
}
