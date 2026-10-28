package live.mehiz.mpvkt.subtitles

import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

internal class FakeSubtitleTransport(
  val respond: suspend (String, Map<String, String>) -> ByteArray
) : SubtitleTransport {
  override suspend fun get(
    url: String,
    headers: Map<String, String>,
    form: Map<String, String>?,
    limit: Int,
  ) = respond(url, headers)
}

class SubtitleServiceTest {
  private val row =
    """[{"url":"https://example.com/Show.S01E02.srt","fileName":"Show.S01E02.srt","language":"en","format":"srt","source":"Example","ai":false}]"""

  @Test
  fun sourceDiscoveryDoesNotTurnApiKeyMetadataIntoASource() = runBlocking {
    val dir = kotlin.io.path.createTempDirectory().toFile()
    try {
      val payload =
        """{"sources":["alpha","beta"],"tiered":[{"key":"alpha","name":"Alpha source","available":true},{"key":"beta","name":"Beta","available":false}],"key":{"valid":true,"type":"paid"}}"""
      val service =
        SubtitleService(FakeSubtitleTransport { _, _ -> payload.toByteArray() }, { "" }, dir)
      val sources = service.sources()
      assertEquals(mapOf("all" to "All sources", "alpha" to "Alpha source"), sources)
    } finally {
      dir.deleteRecursively()
    }
  }

  @Test
  fun duplicateEpisodeNumbersCannotCreateDuplicateLazyRowKeys() = runBlocking {
    val dir = kotlin.io.path.createTempDirectory().toFile()
    try {
      val payload =
        """{"episodes":[{"episode_number":2,"name":"B"},{"episode_number":1,"name":"A"},{"episode_number":2,"name":"duplicate"}]}"""
      val service =
        SubtitleService(FakeSubtitleTransport { _, _ -> payload.toByteArray() }, { "" }, dir)
      assertEquals(listOf(1, 2), service.episodes(MediaMatch(1, "Show", true), 1).map { it.number })
    } finally {
      dir.deleteRecursively()
    }
  }

  @Test
  fun stoppingThePanelCancelsAnUnfinishedDownload() = runBlocking {
    val entered = CompletableDeferred<Unit>()
    val cancelled = CompletableDeferred<Unit>()
    val dir = kotlin.io.path.createTempDirectory().toFile()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    try {
      val service =
        SubtitleService(
          FakeSubtitleTransport { _, _ ->
            entered.complete(Unit)
            try {
              awaitCancellation()
            } finally {
              cancelled.complete(Unit)
            }
          },
          { "" },
          dir,
        )
      val controller = SubtitleSearchController(scope, service, { SubtitleSearchOptions() })
      var applied = false
      controller.download(OnlineSubtitle("https://example.com/a.srt", "a.srt", "en", "Example")) {
        _,
        _ ->
        applied = true
      }
      withTimeout(3000) { entered.await() }
      controller.stop()
      withTimeout(3000) { cancelled.await() }
      assertFalse(applied)
      assertNull(controller.state.value.downloading)
      assertTrue(controller.state.value.files.isEmpty())
    } finally {
      scope.cancel()
      dir.deleteRecursively()
    }
  }

  @Test
  fun hybridKeepsSuccessfulResultsAndReportsMissingProviderKey() = runBlocking {
    val dir = kotlin.io.path.createTempDirectory().toFile()
    try {
      val service =
        SubtitleService(
          FakeSubtitleTransport { url, _ ->
            check(url.startsWith("https://sub.wyzie.io/"))
            row.toByteArray()
          },
          { "" },
          dir,
        )
      val result =
        service.search(
          SubtitleRequest("Show", MediaMatch(7, "Show", true), 1, 2),
          SubtitleSearchOptions(hubSources = setOf("subdl")),
        )
      assertEquals(1, result.subtitles.size)
      assertEquals(listOf("SubDL: API key required"), result.failures)
    } finally {
      dir.deleteRecursively()
    }
  }

  @Test
  fun searchSerializesSeasonEpisodeAndLanguageFilters() = runBlocking {
    val requests = mutableListOf<String>()
    val dir = kotlin.io.path.createTempDirectory().toFile()
    try {
      val service =
        SubtitleService(
          FakeSubtitleTransport { url, _ ->
            requests += url
            row.toByteArray()
          },
          { "" },
          dir,
        )
      service.search(
        SubtitleRequest("Show", MediaMatch(7, "Show", true), 1, 2),
        SubtitleSearchOptions(mode = "wyzie"),
      )
      val url = requests.single()
      assertTrue(url.contains("season=1"))
      assertTrue(url.contains("episode=2"))
      assertTrue(url.contains("language=en"))
      assertTrue(url.contains("id=7"))
      assertFalse(url.contains("null"))
    } finally {
      dir.deleteRecursively()
    }
  }

  @Test
  fun APIKeysCannotBeAttachedToAnUntrustedDownloadHost() = runBlocking {
    var requested = false
    val dir = kotlin.io.path.createTempDirectory().toFile()
    try {
      val service =
        SubtitleService(
          FakeSubtitleTransport { _, _ ->
            requested = true
            byteArrayOf()
          },
          { "private-value" },
          dir,
        )
      val result = runCatching {
        service.download(
          OnlineSubtitle(
            "https://untrusted.example/a.zip",
            "a.zip",
            "ja",
            "Jimaku",
            authenticated = "jimaku",
          ),
          SubtitleRequest("Show"),
        )
      }
      assertTrue(result.isFailure)
      assertFalse(requested)
    } finally {
      dir.deleteRecursively()
    }
  }

  @Test
  fun clearDownloadsInvalidatesAnAlreadyRunningDownloadWithoutTouchingOtherFiles() = runBlocking {
    val dir = kotlin.io.path.createTempDirectory().toFile()
    val unrelated = File(dir.parentFile, "unrelated-${dir.name}.srt").apply { writeText("keep") }
    val entered = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    val service =
      SubtitleService(
        FakeSubtitleTransport { _, _ ->
          entered.complete(Unit)
          release.await()
          "1\n00:00:00,000 --> 00:00:01,000\nText".toByteArray()
        },
        { "" },
        dir,
      )
    try {
      val download = async {
        runCatching {
          service.download(
            OnlineSubtitle("https://example.com/a.srt", "a.srt", "en", "Example"),
            SubtitleRequest("Show"),
          )
        }
      }
      entered.await()
      service.clearDownloads()
      release.complete(Unit)
      assertTrue(download.await().isFailure)
      assertEquals(0, dir.listFiles()!!.size)
      assertEquals("keep", unrelated.readText())
    } finally {
      dir.deleteRecursively()
      unrelated.delete()
    }
  }

  @Test
  fun canceledTitleLookupCannotOverwriteANewerQuery() = runBlocking {
    val entered = CompletableDeferred<Unit>()
    val cancelled = CompletableDeferred<Unit>()
    val dir = kotlin.io.path.createTempDirectory().toFile()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val service =
      SubtitleService(
        FakeSubtitleTransport { url, _ ->
          if (url.contains("q=Old")) {
            entered.complete(Unit)
            try {
              awaitCancellation()
            } finally {
              cancelled.complete(Unit)
            }
          }
          """{"results":[{"id":9,"title":"New","mediaType":"movie","releaseYear":"2024"}]}"""
            .toByteArray()
        },
        { "" },
        dir,
      )
    try {
      val controller = SubtitleSearchController(scope, service, { SubtitleSearchOptions() })
      controller.editQuery("Old")
      controller.findMedia()
      withTimeout(3000) { entered.await() }
      controller.editQuery("New")
      controller.findMedia()
      withTimeout(3000) {
        cancelled.await()
        controller.state.first { it.matches.any { match -> match.title == "New" } }
      }
      assertEquals("New", controller.state.value.query)
      assertEquals("New", controller.state.value.matches.single().title)
    } finally {
      scope.cancel()
      dir.deleteRecursively()
    }
  }

  @Test
  fun allArchiveEntriesAreOfferedInsteadOfAutoSelectingTheFirstOne() = runBlocking {
    val out = java.io.ByteArrayOutputStream()
    java.util.zip.ZipOutputStream(out).use { zip ->
      listOf("Arabic.srt", "English.srt").forEach { name ->
        zip.putNextEntry(java.util.zip.ZipEntry(name))
        zip.write("1\n00:00:00,000 --> 00:00:01,000\nText".toByteArray())
        zip.closeEntry()
      }
    }
    val dir = kotlin.io.path.createTempDirectory().toFile()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    try {
      val service =
        SubtitleService(FakeSubtitleTransport { _, _ -> out.toByteArray() }, { "" }, dir)
      val controller = SubtitleSearchController(scope, service, { SubtitleSearchOptions() })
      controller.editQuery("Show")
      var applied = false
      controller.download(OnlineSubtitle("https://example.com/a.zip", "a.zip", "en", "Example")) {
        _,
        _ ->
        applied = true
      }
      withTimeout(3000) { controller.state.first { it.files.size == 2 } }
      assertFalse(applied)
      controller.chooseFile(controller.state.value.files.last().second) { _, _ -> applied = true }
      assertTrue(applied)
      assertTrue(controller.state.value.files.isEmpty())
    } finally {
      scope.cancel()
      dir.deleteRecursively()
    }
  }
}
