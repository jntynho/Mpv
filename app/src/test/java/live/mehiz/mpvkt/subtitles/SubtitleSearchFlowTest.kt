package live.mehiz.mpvkt.subtitles

import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SubtitleSearchFlowTest {
  @get:Rule val temporary = TemporaryFolder()
  private val titles =
    """{"results":[{"id":7,"title":"Show","mediaType":"tv"},{"id":8,"title":"Film","mediaType":"movie"}]}"""
  private val subtitles =
    """[{"url":"https://example.com/film.srt","fileName":"film.srt","language":"en","format":"srt","source":"Example"}]"""

  @Test
  fun openingNeverSearchesAndFilmSelectionLoadsOnlySubtitlesWithTheSelectedTitle() = runBlocking {
    val requests = CopyOnWriteArrayList<String>()
    withController({ url ->
      requests += url
      if ("/api/tmdb/search" in url) titles else subtitles
    }) { c ->
      c.begin("File.Name.2024.mkv", 1)
      assertEquals(SubtitleSearchPage.Query, c.state.value.page)
      assertTrue(requests.isEmpty())
      c.findMedia()
      c.await { it.matches.size == 2 }
      assertEquals(1, requests.size)
      c.selectMedia(c.state.value.matches.last())
      c.await { it.subtitles.isNotEmpty() && !it.loading }
      assertEquals(SubtitleSearchPage.Subtitles, c.state.value.page)
      assertFalse(c.state.value.series)
      assertEquals(1, c.state.value.slot)
      assertEquals(2, requests.size)
      assertTrue(requests.last().contains("id=8"))
      assertEquals("File Name", c.state.value.query)
      assertTrue(c.back())
      assertEquals(SubtitleSearchPage.Titles, c.state.value.page)
      assertEquals(2, c.state.value.matches.size)
      assertTrue(c.state.value.subtitles.isEmpty())
      assertEquals(2, requests.size)
      assertTrue(c.back())
      assertEquals(SubtitleSearchPage.Query, c.state.value.page)
      assertFalse(c.back())
    }
  }

  @Test
  fun seriesWaitsForExplicitSubmitAndSeasonChangesCannotKeepPreviousResults() = runBlocking {
    val requests = CopyOnWriteArrayList<String>()
    withController({ url ->
      requests += url
      when {
        "/api/tmdb/search" in url -> titles
        "/api/tmdb/tv/7/" in url -> """{"episodes":[{"episode_number":2,"name":"Episode Two"}]}"""
        "/api/tmdb/tv/7" in url -> """{"seasons":[{"season_number":1},{"season_number":2}]}"""
        else -> subtitles
      }
    }) { c ->
      c.begin("Show.S01E99.mkv", 0)
      c.findMedia()
      c.await { it.matches.isNotEmpty() }
      c.selectMedia(c.state.value.matches.first())
      c.await { !it.loadingEpisodes && it.episodes.isNotEmpty() }
      assertEquals(2, c.state.value.episode)
      assertTrue(c.state.value.subtitles.isEmpty())
      assertFalse(requests.any { it.startsWith("https://sub.wyzie.io/search?") })
      c.search()
      c.await { !it.loading && it.subtitles.isNotEmpty() }
      assertTrue(requests.last().contains("season=1") && requests.last().contains("episode=2"))
      c.setSeason(2)
      c.await { !it.loadingEpisodes }
      assertTrue(c.state.value.subtitles.isEmpty())
      val before = requests.count { it.startsWith("https://sub.wyzie.io/search?") }
      c.setEpisode(3)
      assertEquals(before, requests.count { it.startsWith("https://sub.wyzie.io/search?") })
      c.search()
      c.await { !it.loading }
      assertTrue(requests.last().contains("season=2") && requests.last().contains("episode=3"))
    }
  }

  @Test
  fun backCancelsSlowLookupAndLateResultsCannotReplaceTheQueryPage() = runBlocking {
    val entered = CompletableDeferred<Unit>()
    val cancelled = CompletableDeferred<Unit>()
    withController({ _ ->
      entered.complete(Unit)
      try {
        awaitCancellation()
      } finally {
        cancelled.complete(Unit)
      }
    }) { c ->
      c.begin("Film", 0)
      c.findMedia()
      withTimeout(3000) { entered.await() }
      assertTrue(c.back())
      withTimeout(3000) { cancelled.await() }
      assertEquals(SubtitleSearchPage.Query, c.state.value.page)
      assertFalse(c.state.value.loading)
      assertTrue(c.state.value.matches.isEmpty())
    }
  }

  @Test
  fun reopeningStartsAtTheFormAndRetainsTheRequestedTargetSlot() = runBlocking {
    withController({ url -> if ("/api/tmdb/search" in url) titles else subtitles }) { c ->
      c.begin("Film", 0, "one.mp4")
      c.findMedia()
      c.await { it.matches.isNotEmpty() }
      c.begin("Film", 1, "one.mp4")
      assertEquals(SubtitleSearchPage.Query, c.state.value.page)
      assertEquals(1, c.state.value.slot)
      assertTrue(c.state.value.matches.isEmpty())
      c.begin("New.S02E03.mkv", 0, "two.mkv")
      assertEquals("New", c.state.value.query)
      assertEquals(2, c.state.value.season)
      assertEquals(3, c.state.value.episode)
      assertEquals(SubtitleSearchPage.Query, c.state.value.page)
    }
  }

  @Test
  fun unavailableMetadataAllowsManualSeasonAndEpisodeWithoutRequestsPerDigit() = runBlocking {
    val requests = CopyOnWriteArrayList<String>()
    withController({ url ->
      requests += url
      when {
        "/api/tmdb/search" in url -> titles
        "/api/tmdb/tv/" in url -> throw java.io.IOException("Metadata unavailable")
        else -> subtitles
      }
    }) { c ->
      c.begin("Show", 0)
      c.findMedia()
      c.await { it.matches.isNotEmpty() }
      c.selectMedia(c.state.value.matches.first())
      c.await { !it.loadingEpisodes }
      assertEquals(SubtitleNotice.EpisodesUnavailable, c.state.value.message)
      val count = requests.size
      c.setSeason(1)
      c.setSeason(12)
      c.setEpisode(3)
      assertFalse(c.state.value.loadingEpisodes)
      assertEquals(count, requests.size)
      c.search()
      c.await { !it.loading }
      assertTrue(requests.last().contains("season=12") && requests.last().contains("episode=3"))
    }
  }

  private suspend fun SubtitleSearchController.await(predicate: (SubtitleSearchState) -> Boolean) =
    withTimeout(3000) { state.first(predicate) }

  private suspend fun withController(
    reply: suspend (String) -> String,
    test: suspend (SubtitleSearchController) -> Unit,
  ) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    try {
      val service =
        SubtitleService(
          FakeSubtitleTransport { url, _ -> reply(url).toByteArray() },
          { "" },
          temporary.newFolder(),
        )
      test(SubtitleSearchController(scope, service, { SubtitleSearchOptions(mode = "wyzie") }))
    } finally {
      scope.cancel()
    }
  }
}
